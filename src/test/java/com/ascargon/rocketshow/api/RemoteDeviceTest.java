package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.MidiSignal;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Requests to other units, e.g. to play a composition on all of them.
 */
class RemoteDeviceTest {

    private static final int READ_TIMEOUT_MILLIS = 500;

    private final BlockingQueue<String> requests = new LinkedBlockingQueue<>();
    private final CountDownLatch hangingRequestsEnd = new CountDownLatch(1);
    private final ExecutorService serverExecutor = Executors.newCachedThreadPool();
    private HttpServer server;

    // Another unit. It hangs on the composition "Hang" and takes a while to play and for a note on.
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            if (exchange.getRequestURI().getRawPath().equals("/api/transport/play") || body.contains("\"command\":144")) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if ("name=Hang".equals(query)) {
                try {
                    hangingRequestsEnd.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query)
                        + " " + exchange.getRequestHeaders().getFirst(ApiKeyAuthFilter.API_KEY_HEADER_NAME) + (body.isEmpty() ? "" : " " + body));
                exchange.sendResponseHeaders(200, -1);
            }

            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        hangingRequestsEnd.countDown();
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    private RemoteDevice remoteDevice(int port) {
        RemoteDevice remoteDevice = new RemoteDevice(1000, READ_TIMEOUT_MILLIS);
        remoteDevice.setName("Unit 2");
        remoteDevice.setHost("127.0.0.1:" + port);
        remoteDevice.setApiKey("api-key-of-unit-2");
        return remoteDevice;
    }

    private RemoteDevice remoteDevice() {
        return remoteDevice(server.getAddress().getPort());
    }

    private String nextRequest() throws InterruptedException {
        return requests.poll(5, TimeUnit.SECONDS);
    }

    @Test
    void sendsCompositionNamesWithSpacesAndSymbols() throws Exception {
        RemoteDevice remoteDevice = remoteDevice();

        remoteDevice.load(true, "Song 1 & 2");
        assertEquals("POST /api/transport/load?name=Song+1+%26+2 api-key-of-unit-2", nextRequest());

        remoteDevice.playAsSample("Song 1");
        assertEquals("POST /api/transport/play-as-sample?name=Song+1 api-key-of-unit-2", nextRequest());

        remoteDevice.setCompositionName("Song 1");
        assertEquals("POST /api/transport/set-composition-name?name=Song+1 api-key-of-unit-2", nextRequest());
    }

    @Test
    void givesUpOnAUnitWhichDoesNotAnswer() throws Exception {
        // Accepts connections, but never answers
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            List<Socket> connections = new ArrayList<>();
            Thread acceptor = new Thread(() -> {
                try {
                    while (true) {
                        connections.add(serverSocket.accept());
                    }
                } catch (IOException e) {
                    // closed
                }
            });
            acceptor.start();

            RemoteDevice remoteDevice = remoteDevice(serverSocket.getLocalPort());

            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> remoteDevice.load(true, "Song 1"));
        }
    }

    @Test
    void reachesTheUnitAgainAfterItHung() throws Exception {
        RemoteDevice remoteDevice = remoteDevice();

        // More hanging requests than connections to a unit
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (int i = 0; i < 3; i++) {
                remoteDevice.load(true, "Hang");
            }

            remoteDevice.load(true, "Song 1");
        });

        assertEquals("POST /api/transport/load?name=Song+1 api-key-of-unit-2", nextRequest());
    }

    @Test
    void sendsMidiInTheOrderItWasPlayed() throws Exception {
        RemoteDevice remoteDevice = remoteDevice();

        // The unit takes a while for each note on: the note off must not overtake it, or the note keeps sounding
        for (int note = 60; note < 65; note++) {
            remoteDevice.sendMidiSignal(new MidiSignal(144, 0, note, 100));
            remoteDevice.sendMidiSignal(new MidiSignal(128, 0, note, 0));
        }

        for (int note = 60; note < 65; note++) {
            assertEquals("POST /api/midi/send-message api-key-of-unit-2 {\"channel\":0,\"command\":144,\"data1\":" + note + ",\"data2\":100}", nextRequest());
            assertEquals("POST /api/midi/send-message api-key-of-unit-2 {\"channel\":0,\"command\":128,\"data1\":" + note + ",\"data2\":0}", nextRequest());
        }
    }

    @Test
    void sendsTheTransportInTheOrderItWasUsed() throws Exception {
        RemoteDevice remoteDevice = remoteDevice();

        remoteDevice.play();
        remoteDevice.stop(false);

        assertEquals("POST /api/transport/play api-key-of-unit-2", nextRequest());
        assertEquals("POST /api/transport/stop?playDefaultComposition=false api-key-of-unit-2", nextRequest());
    }

    @Test
    void waitsForTheRequestsBeforeAnAwaitedOne() throws Exception {
        RemoteDevice remoteDevice = remoteDevice();

        remoteDevice.play();
        remoteDevice.load(true, "Song 1");

        // Both arrived when loading returns
        assertEquals("POST /api/transport/play api-key-of-unit-2", requests.poll());
        assertEquals("POST /api/transport/load?name=Song+1 api-key-of-unit-2", requests.poll());
    }

    @Test
    void dropsRequestsToAUnitWhichDoesNotKeepUp() {
        RemoteDevice remoteDevice = remoteDevice();

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            // Each takes until the read timeout
            for (int i = 0; i < 1001; i++) {
                remoteDevice.load(false, "Hang");
            }

            // No more room: dropped instead of waiting for all the requests before it
            remoteDevice.load(true, "Song 1");
        });

        assertNull(requests.poll());
    }

}
