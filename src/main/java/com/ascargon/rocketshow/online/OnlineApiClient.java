package com.ascargon.rocketshow.online;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Calls the API of the online designer on rocketshow.net: the sync of the fixture library, the account of the device
 * and the requests the designer of the device hands on to it (see the plan of the fixture library in the designer,
 * docs/fixture-library.md).
 */
@Service
public class OnlineApiClient {

    public final static String DEFAULT_URL = "https://www.rocketshow.net/designer/api/";

    // A unit without internet (e.g. on a stage network) gives up soon
    private final static Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    // The answer of the API: its status, the type of its content and the content, unpacked
    public record Response(int status, String contentType, byte[] body) {

        public boolean isSuccessful() {
            return status >= 200 && status < 300;
        }

        public String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }

    }

    private final String url;
    private final HttpClient httpClient;

    // The address of the API can be changed for the tests
    public OnlineApiClient(@Value("${rocketshow.online-api-url:" + DEFAULT_URL + "}") String url) {
        this.url = url.endsWith("/") ? url : url + "/";

        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    // Sends a request to the passed path of the API (with its query, if any) and waits at most the passed time for the
    // answer. Throws when the API can't be reached, never for an error it answers.
    public Response send(String method, String path, Map<String, String> headers, HttpRequest.BodyPublisher body, Duration timeout) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url + path))
                .timeout(timeout)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : body)
                // The pages of the fixture library are large, but compress well
                .header("Accept-Encoding", "gzip");

        headers.forEach(request::header);

        HttpResponse<byte[]> response;

        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for " + url + path);
        }

        byte[] content = response.body();

        if (response.headers().firstValue("Content-Encoding").map("gzip"::equalsIgnoreCase).orElse(false)) {
            try (InputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(content))) {
                content = inputStream.readAllBytes();
            }
        }

        return new Response(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null), content);
    }

}
