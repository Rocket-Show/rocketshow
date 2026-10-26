package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.play.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The state of the unit, sent to every connected web app. Every message holds the whole state, so the web app shows
 * whatever it got last.
 */
class DefaultNotificationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // A web app on the other end. Like Tomcat, it refuses a second message while it still writes one.
    private static WebSocketSession webApp(List<Long> received) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        AtomicBoolean writing = new AtomicBoolean(false);
        ObjectMapper objectMapper = new ObjectMapper();

        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            if (!writing.compareAndSet(false, true)) {
                throw new IllegalStateException("The remote endpoint was in state [TEXT_PARTIAL_WRITING] which is an invalid state for called method");
            }

            try {
                Thread.sleep(1);
                WebSocketMessage<?> message = invocation.getArgument(0);
                if (message instanceof TextMessage textMessage) {
                    received.add(objectMapper.readTree(textMessage.getPayload()).get("positionMillis").asLong());
                }
            } finally {
                writing.set(false);
            }
            return null;
        }).when(session).sendMessage(any());

        return session;
    }

    @Test
    void sendsTheState() throws Exception {
        StateService stateService = mock(StateService.class);
        State state = new State();
        state.setCurrentCompositionName("Song 1");
        when(stateService.getCurrentState(any(), any(), any())).thenReturn(state);

        DefaultNotificationService notificationService = new DefaultNotificationService(stateService, mock(CompositionService.class));
        List<String> received = new CopyOnWriteArrayList<>();
        WebSocketSession session = mock(WebSocketSession.class);
        doAnswer(invocation -> received.add(((TextMessage) invocation.getArgument(0)).getPayload())).when(session).sendMessage(any());
        notificationService.afterConnectionEstablished(session);

        notificationService.notifyClients(mock(PlayerService.class), mock(SetService.class));
        notificationService.notifyClients("Could not play");

        long end = System.currentTimeMillis() + 5000;
        while (received.size() < 2 && System.currentTimeMillis() < end) {
            Thread.sleep(10);
        }

        assertEquals("Song 1", objectMapper.readTree(received.get(0)).get("currentCompositionName").asString());
        assertEquals("Could not play", objectMapper.readTree(received.get(1)).get("error").asString());
    }

    @Test
    void sendsTheStateWithoutSetLoaded() throws Exception {
        // Only the set changed, while no set is loaded
        State state = new DefaultStateService().getCurrentState(null, new com.ascargon.rocketshow.composition.DefaultSetService(), mock(CompositionService.class));

        assertEquals(null, state.getCurrentSetName());
    }

    @Test
    void endsWithTheLatestStateWhenNotifiedFromManyThreads() throws Exception {
        // Each state is newer than the one before
        AtomicLong stateNumber = new AtomicLong();
        StateService stateService = mock(StateService.class);
        when(stateService.getCurrentState(any(), any(), any())).thenAnswer(invocation -> {
            State state = new State();
            state.setPositionMillis(stateNumber.incrementAndGet());
            return state;
        });

        DefaultNotificationService notificationService = new DefaultNotificationService(stateService, mock(CompositionService.class));
        List<Long> received = new CopyOnWriteArrayList<>();
        notificationService.afterConnectionEstablished(webApp(received));

        // E.g. the player, the MIDI input and the designer at the same time
        ExecutorService threads = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(400);
        for (int i = 0; i < 400; i++) {
            threads.execute(() -> {
                try {
                    notificationService.notifyClients(mock(PlayerService.class));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(30, TimeUnit.SECONDS));
        threads.shutdown();

        // Let the queued messages go out
        long end = System.currentTimeMillis() + 5000;
        while (received.size() < 400 && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }

        assertEquals(400, received.size(), "Messages lost");
        assertEquals(400L, received.getLast(), "The web app shows an old state");
    }

}
