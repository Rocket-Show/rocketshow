package com.ascargon.rocketshow.api;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public interface ControllerService {

    ResponseEntity<ErrorResponse> handleException(Exception exception);

}
