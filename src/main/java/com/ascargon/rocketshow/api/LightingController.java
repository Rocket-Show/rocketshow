package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.LightingAction;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.OlaPlugin;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("ola-plugins")
    public List<OlaPlugin> getOlaPlugins() {
        return lightingService.getOlaPlugins();
    }

}
