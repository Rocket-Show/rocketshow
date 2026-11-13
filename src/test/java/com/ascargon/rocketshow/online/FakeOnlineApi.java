package com.ascargon.rocketshow.online;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;

/**
 * The API of the online designer on rocketshow.net, for the tests: answers each path as it is told, and keeps the
 * requests it got.
 */
public class FakeOnlineApi implements AutoCloseable {

    private final static String BASE_PATH = "/designer/api/";

    public record Request(String method, String path, String query, Map<String, String> headers, byte[] body) {

        public String header(String name) {
            return headers.get(name);
        }

        public String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }

    }

    public record Answer(int status, String contentType, byte[] body, boolean gzip) {

        public static Answer json(int status, String json) {
            return new Answer(status, "application/json", json.getBytes(StandardCharsets.UTF_8), false);
        }

        public static Answer json(String json) {
            return json(200, json);
        }

        public Answer gzipped() {
            return new Answer(status, contentType, body, true);
        }

    }

    private final HttpServer server;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Function<Request, Answer>> handlers = new ConcurrentHashMap<>();

    public FakeOnlineApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(BASE_PATH, exchange -> {
            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.get(0)));

            String path = exchange.getRequestURI().getPath().substring(BASE_PATH.length());
            Request request = new Request(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(), headers, exchange.getRequestBody().readAllBytes());
            requests.add(request);

            Function<Request, Answer> handler = handlers.get(path);
            Answer answer = handler == null ? Answer.json(404, "{\"error\": \"not-found\"}") : handler.apply(request);
            byte[] body = answer.body();

            if (answer.gzip()) {
                ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
                try (GZIPOutputStream gzip = new GZIPOutputStream(gzipped)) {
                    gzip.write(body);
                }
                body = gzipped.toByteArray();
                exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            }

            if (answer.contentType() != null) {
                exchange.getResponseHeaders().add("Content-Type", answer.contentType());
            }

            exchange.sendResponseHeaders(answer.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    public String getUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + BASE_PATH;
    }

    // How to answer a path of the API (e.g. "library-sync")
    public void answer(String path, Function<Request, Answer> handler) {
        handlers.put(path, handler);
    }

    public List<Request> getRequests(String path) {
        return requests.stream().filter(request -> request.path().equals(path)).toList();
    }

    public void clear() {
        requests.clear();
        handlers.clear();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            server.stop(0);
        }
    }

}
