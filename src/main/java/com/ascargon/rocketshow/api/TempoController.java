package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.designer.TempoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The tempo from outside (see TempoService): tapped, the first beat of a bar, set, halved, doubled or nudged.
 */
@RestController()
@RequestMapping("${spring.data.rest.base-path}/tempo")
@CrossOrigin
public class TempoController {

    private final ControllerService controllerService;
    private final TempoService tempoService;

    public TempoController(ControllerService controllerService, TempoService tempoService) {
        this.controllerService = controllerService;
        this.tempoService = tempoService;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return controllerService.handleException(exception);
    }

    // A beat tapped (the first one after a pause is the first beat of a bar)
    @PostMapping("tap")
    public ResponseEntity<Void> tap() {
        tempoService.tap();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // Now is the first beat of a bar
    @PostMapping("beat-one")
    public ResponseEntity<Void> beatOne() {
        tempoService.beatOne();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // The tempo set (none = back to the programmed one)
    @PostMapping("set")
    public ResponseEntity<Void> set(@RequestParam(value = "value", required = false) Double value) {
        tempoService.setBeatsPerMinute(value);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("halve")
    public ResponseEntity<Void> halve() {
        tempoService.halve();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("double")
    public ResponseEntity<Void> doubleTempo() {
        tempoService.doubleTempo();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // A little faster (positive) or slower (negative), in beats per minute
    @PostMapping("nudge")
    public ResponseEntity<Void> nudge(@RequestParam("value") double value) {
        tempoService.nudge(value);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
