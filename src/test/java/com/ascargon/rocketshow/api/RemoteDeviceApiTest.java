package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.util.ActionExecutionService;
import com.ascargon.rocketshow.util.ActionTransport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * The requests a unit sends to other units reach an endpoint of their API, e.g. not a path no controller serves.
 */
class RemoteDeviceApiTest extends ApiTest {

    private final static String SONG = "Remote unit song";

    private record Request(String method, String pathAndQuery, String apiKey, String contentType, String body) {
    }

    @Autowired
    CompositionService compositionService;

    @Autowired
    PlayerService playerService;

    @Autowired
    ActionExecutionService actionExecutionService;

    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private HttpServer otherUnit;
    private RemoteDevice remoteDevice;

    // Another unit, which only records the requests
    @BeforeEach
    void setUp() throws Exception {
        otherUnit = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        otherUnit.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            requests.add(new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query),
                    exchange.getRequestHeaders().getFirst(ApiKeyAuthFilter.API_KEY_HEADER_NAME),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        otherUnit.start();

        remoteDevice = new RemoteDevice(1000, 5000);
        remoteDevice.setName("Remote unit");
        remoteDevice.setHost("127.0.0.1:" + otherUnit.getAddress().getPort());
        remoteDevice.setApiKey(API_KEY);
        settingsService.getSettings().getRemoteDeviceList().add(remoteDevice);

        setAdminPassword();
        addApiKey();

        Composition composition = new Composition();
        composition.setName(SONG);
        compositionService.saveComposition(composition);
    }

    @AfterEach
    void cleanUp() throws Exception {
        settingsService.getSettings().getRemoteDeviceList().remove(remoteDevice);
        otherUnit.stop(0);

        playerService.stop();
        playerService.loadSetAndComposition("");
        compositionService.deleteComposition(SONG);
    }

    private List<Request> receivedRequests(int count) throws InterruptedException {
        List<Request> received = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Request request = requests.poll(5, TimeUnit.SECONDS);
            assertNotNull(request, "Only received " + received);
            received.add(request);
        }

        return received;
    }

    private MockHttpServletRequestBuilder replay(Request received) {
        String[] pathAndQuery = received.pathAndQuery().split("\\?", 2);
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(received.method()), URI.create(pathAndQuery[0]))
                .header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, received.apiKey());

        // Decoded like the servlet container does, e.g. a '+' to a space, which MockMvc doesn't
        if (pathAndQuery.length > 1) {
            for (String parameter : pathAndQuery[1].split("&")) {
                String[] nameAndValue = parameter.split("=", 2);
                request.param(URLDecoder.decode(nameAndValue[0], StandardCharsets.UTF_8),
                        nameAndValue.length > 1 ? URLDecoder.decode(nameAndValue[1], StandardCharsets.UTF_8) : "");
            }
        }

        if (received.contentType() != null) {
            request.header("Content-Type", received.contentType());
        }

        return request.content(received.body());
    }

    @Test
    void sendsOnlyRequestsTheOtherUnitsServe() throws Exception {
        // Everything a unit sends to the others
        remoteDevice.load(true, SONG);
        remoteDevice.play();
        remoteDevice.pause();
        remoteDevice.togglePlay();
        remoteDevice.stop(false);
        remoteDevice.setCompositionName(SONG);
        remoteDevice.playAsSample(SONG);
        remoteDevice.sendMidiSignal(new MidiSignal(128, 0, 60, 0));
        remoteDevice.reboot();
        remoteDevice.shutdown();

        ActionTransport action = new ActionTransport();
        action.setTransportActionType(ActionTransport.TransportActionType.STOP);
        action.setExecuteLocally(false);
        action.getRemoteDeviceNames().add(remoteDevice.getName());
        actionExecutionService.execute(action);

        // This unit answers each, like the other units do
        for (Request received : receivedRequests(11)) {
            assertEquals(200, getStatus(replay(received)), received.toString());
        }
    }

}
