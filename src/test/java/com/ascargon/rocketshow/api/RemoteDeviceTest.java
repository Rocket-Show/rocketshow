package com.ascargon.rocketshow.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

    // Another unit. It hangs on the composition "Hang".
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();

            if ("name=Hang".equals(query)) {
                try {
                    hangingRequestsEnd.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query)
                        + " " + exchange.getRequestHeaders().getFirst(ApiKeyAuthFilter.API_KEY_HEADER_NAME));
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

}
