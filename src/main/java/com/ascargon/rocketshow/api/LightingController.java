package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.LightingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/lighting")
@CrossOrigin
public class LightingController {

    private final ControllerService controllerService;
    private final LightingService lightingService;

    public LightingController(ControllerService controllerService, LightingService lightingService) {
        this.controllerService = controllerService;
        this.lightingService = lightingService;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return controllerService.handleException(exception);
    }

    @PostMapping("reset")
    public ResponseEntity<Void> reset() {
        lightingService.reset();
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
