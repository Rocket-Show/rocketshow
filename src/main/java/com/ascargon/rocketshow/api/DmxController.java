package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.dmx.DmxService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/dmx")
@CrossOrigin
public class DmxController {

    private final DmxService dmxService;

    public DmxController(DmxService dmxService) {
        this.dmxService = dmxService;
    }

    @PostMapping("reset")
    public ResponseEntity<Void> reset() {
        dmxService.reset();
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
