package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.LightingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/lighting")
@CrossOrigin
public class LightingController {

    private final LightingService lightingService;

    public LightingController(LightingService lightingService) {
        this.lightingService = lightingService;
    }

    @PostMapping("reset")
    public ResponseEntity<Void> reset() {
        lightingService.reset();
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
