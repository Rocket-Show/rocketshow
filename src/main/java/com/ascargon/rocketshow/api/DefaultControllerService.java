package com.ascargon.rocketshow.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DefaultControllerService implements ControllerService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultControllerService.class);

    @Override
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(exception.getMessage());
        errorResponse.setCode("unknown");

        // specific mapping
        if (exception instanceof ResponseStatusException responseStatusException) {
            httpStatus = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
            errorResponse.setMessage(responseStatusException.getReason());
            logger.info("Request rejected: {}", responseStatusException.getReason());
        } else if (exception instanceof IllegalArgumentException) {
            // e.g. a file name leaving its folder
            httpStatus = HttpStatus.BAD_REQUEST;
            logger.info("Request rejected: {}", exception.getMessage());
        } else {
            logger.error("An exception has been thrown in the controller", exception);
        }

        return new ResponseEntity<>(errorResponse, httpStatus);
    }

}
