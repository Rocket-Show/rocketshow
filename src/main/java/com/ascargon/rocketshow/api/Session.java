package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/session")
public class Session {

    private final SessionService sessionService;

    public Session(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public com.ascargon.rocketshow.Session getSession() {
        return sessionService.getSession();
    }

    @PostMapping("wizard-finished")
    public ResponseEntity<Void> setWizardFinished() {
        sessionService.getSession().setFirstStart(false);
        sessionService.save();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("dismiss-update-finished")
    public ResponseEntity<Void> dismissUpdateFinished() {
        sessionService.getSession().setUpdateFinished(false);
        sessionService.save();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("set-auto-select-next-composition")
    public ResponseEntity<Void> setAutoSelectNextComposition(@RequestParam("value") boolean value) {
        sessionService.getSession().setAutoSelectNextComposition(value);
        sessionService.save();
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
