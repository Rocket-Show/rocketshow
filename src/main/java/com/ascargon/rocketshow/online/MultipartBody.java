package com.ascargon.rocketshow.online;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A multipart/form-data body put together from fields and files, e.g. to hand a manual uploaded to the device on to
 * the API of the online designer. A file is read while it is sent, not held in memory: a manual has up to 32 MB.
 */
public class MultipartBody {

    private final String boundary = "RocketShowBoundary" + UUID.randomUUID().toString().replace("-", "");

    private final List<HttpRequest.BodyPublisher> parts = new ArrayList<>();

    // Like browsers do it: quotes and line breaks of a name can't stand in its header
    private static String escape(String name) {
        return name.replace("\"", "%22").replace("\r", "%0D").replace("\n", "%0A");
    }

    private void addHeader(String name, String fileName, String contentType) {
        StringBuilder header = new StringBuilder();
        header.append("--").append(boundary).append("\r\n");
        header.append("Content-Disposition: form-data; name=\"").append(escape(name)).append('"');

        if (fileName != null) {
            header.append("; filename=\"").append(escape(fileName)).append('"');
        }

        header.append("\r\n");

        if (contentType != null) {
            header.append("Content-Type: ").append(escape(contentType)).append("\r\n");
        }

        header.append("\r\n");

        parts.add(HttpRequest.BodyPublishers.ofString(header.toString(), StandardCharsets.UTF_8));
    }

    public void addField(String name, String value) {
        addHeader(name, null, null);
        parts.add(HttpRequest.BodyPublishers.ofString(value + "\r\n", StandardCharsets.UTF_8));
    }

    public void addFile(String name, String fileName, String contentType, long size, Supplier<InputStream> content) {
        addHeader(name, fileName == null ? "" : fileName, contentType == null ? "application/octet-stream" : contentType);

        // With its length, so that the request has one (a PHP behind a web server may not take a body without)
        if (size > 0) {
            parts.add(HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(content), size));
        }

        parts.add(HttpRequest.BodyPublishers.ofString("\r\n", StandardCharsets.UTF_8));
    }

    public String getContentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    public HttpRequest.BodyPublisher build() {
        List<HttpRequest.BodyPublisher> body = new ArrayList<>(parts);
        body.add(HttpRequest.BodyPublishers.ofString("--" + boundary + "--\r\n", StandardCharsets.UTF_8));

        return HttpRequest.BodyPublishers.concat(body.toArray(new HttpRequest.BodyPublisher[0]));
    }

}
