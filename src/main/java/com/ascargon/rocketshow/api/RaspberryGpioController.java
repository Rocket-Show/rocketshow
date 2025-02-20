package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.raspberry.RaspberryGpioAction;
import com.ascargon.rocketshow.raspberry.RaspberryGpioOutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/raspberry-gpio")
@CrossOrigin
public class RaspberryGpioController {

    private final ControllerService controllerService;
    private final RaspberryGpioOutService raspberryGpioOutService;

    public RaspberryGpioController(ControllerService controllerService, RaspberryGpioOutService raspberryGpioOutService) {
        this.controllerService = controllerService;
        this.raspberryGpioOutService = raspberryGpioOutService;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return controllerService.handleException(exception);
    }

    @PostMapping("execute-action")
    public void executeAction(@RequestBody RaspberryGpioAction gpioAction) {
        raspberryGpioOutService.executeAction(gpioAction);
    }

}
