package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.online.OnlineApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Answers of the requests the device hands on to the API of the online designer: its own answer as it is, or an error
 * like the ones of the API ({"error": "<code>"}) when it couldn't be asked.
 */
final class OnlineApiAnswers {

    private final static Logger logger = LoggerFactory.getLogger(OnlineApiAnswers.class);

    private OnlineApiAnswers() {
    }

    static ResponseEntity<byte[]> error(HttpStatus status, String error) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(("{\"error\": \"" + error + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    // The answer of the API, as it is
    static ResponseEntity<byte[]> answer(OnlineApiClient.Response response) {
        ResponseEntity.BodyBuilder answer = ResponseEntity.status(response.status());

        if (response.contentType() != null) {
            answer.header(HttpHeaders.CONTENT_TYPE, response.contentType());
        }

        return answer.body(response.body());
    }

    // The API couldn't be reached (e.g. no internet), or didn't answer in time
    static ResponseEntity<byte[]> unreachable(IOException exception) {
        if (exception instanceof HttpTimeoutException) {
            logger.warn("The online designer didn't answer in time");
            return error(HttpStatus.GATEWAY_TIMEOUT, "api-timeout");
        }

        logger.warn("Could not reach the online designer: {}", exception.toString());
        return error(HttpStatus.BAD_GATEWAY, "api-unreachable");
    }

}
