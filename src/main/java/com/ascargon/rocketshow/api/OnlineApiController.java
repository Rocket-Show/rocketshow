package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.online.AccountService;
import com.ascargon.rocketshow.online.MultipartBody;
import com.ascargon.rocketshow.online.OnlineApiClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hands the requests of the designer which need the online designer on to its API, as the account the device is
 * linked to: the AI looks and fixture profiles, the votes on the profiles and offering a profile to the library (see
 * the plan of the fixture library in the designer, section 9). The browser may be connected to the access point of
 * the device without internet of its own, and the token of the account stays on the device. The answer of the API is
 * handed back as it is.
 */
@RestController
@RequestMapping("${spring.data.rest.base-path}")
public class OnlineApiController {

    // The AI takes up to 4 minutes for an answer
    private final static Duration TIMEOUT = Duration.ofMinutes(5);

    private final AccountService accountService;
    private final OnlineApiClient onlineApiClient;

    public OnlineApiController(AccountService accountService, OnlineApiClient onlineApiClient) {
        this.accountService = accountService;
        this.onlineApiClient = onlineApiClient;
    }

    private ResponseEntity<byte[]> forward(HttpServletRequest request, HttpRequest.BodyPublisher body, String contentType, boolean withQuery) throws IOException {
        Map<String, String> deviceHeaders = accountService.getDeviceHeaders();

        if (deviceHeaders.isEmpty()) {
            return OnlineApiAnswers.error(HttpStatus.UNAUTHORIZED, "device-not-linked");
        }

        // The same path on the API, e.g. /api/ai-looks to ai-looks
        String path = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/') + 1);

        if (withQuery && request.getQueryString() != null) {
            path += "?" + request.getQueryString();
        }

        Map<String, String> headers = new HashMap<>(deviceHeaders);

        if (contentType != null) {
            headers.put(HttpHeaders.CONTENT_TYPE, contentType);
        }

        OnlineApiClient.Response response;

        try {
            response = onlineApiClient.send(request.getMethod(), path, headers, body, TIMEOUT);
        } catch (IOException e) {
            return OnlineApiAnswers.unreachable(e);
        }

        // E.g. unlinked from "My devices" online
        if (response.status() == HttpStatus.UNAUTHORIZED.value()) {
            accountService.forgetRefusedLink(deviceHeaders);
        }

        return OnlineApiAnswers.answer(response);
    }

    @GetMapping({"ai-looks", "ai-fixtures", "fixture-vote"})
    public ResponseEntity<byte[]> forwardGet(HttpServletRequest request) throws IOException {
        return forward(request, HttpRequest.BodyPublishers.noBody(), null, true);
    }

    @DeleteMapping("fixture-vote")
    public ResponseEntity<byte[]> forwardDelete(HttpServletRequest request) throws IOException {
        return forward(request, HttpRequest.BodyPublishers.noBody(), null, true);
    }

    // JSON, or the form of a fixture profile to generate with the manual
    @PostMapping({"ai-looks", "ai-fixtures", "fixture-vote", "fixture-offer"})
    public ResponseEntity<byte[]> forwardPost(HttpServletRequest request) throws IOException {
        MultipartHttpServletRequest multipartRequest = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);

        if (multipartRequest == null) {
            byte[] body = request.getInputStream().readAllBytes();
            return forward(request, HttpRequest.BodyPublishers.ofByteArray(body), request.getContentType(), true);
        }

        // Read by Spring already: put together again. Its fields hold the ones of the address as well, which are
        // therefore not handed on twice.
        MultipartBody body = new MultipartBody();

        for (Map.Entry<String, String[]> field : multipartRequest.getParameterMap().entrySet()) {
            for (String value : field.getValue()) {
                body.addField(field.getKey(), value);
            }
        }

        for (Map.Entry<String, List<MultipartFile>> files : multipartRequest.getMultiFileMap().entrySet()) {
            for (MultipartFile file : files.getValue()) {
                body.addFile(files.getKey(), file.getOriginalFilename(), file.getContentType(), file.getSize(), () -> {
                    try {
                        return file.getInputStream();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }

        return forward(request, body.build(), body.getContentType(), false);
    }

}
