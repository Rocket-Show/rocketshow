package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.midi.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/midi")
@CrossOrigin
class MidiController {

    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final MidiRoutingService midiRoutingService;
    private final MidiService midiService;
    private final MidiControlActionExecutionService midiControlActionExecutionService;

    private MidiController(SettingsService settingsService, NotificationService notificationService, MidiRoutingService midiRoutingService, MidiService midiService, MidiControlActionExecutionService midiControlActionExecutionService) {
        this.settingsService = settingsService;
        this.notificationService = notificationService;
        this.midiRoutingService = midiRoutingService;
        this.midiService = midiService;
        this.midiControlActionExecutionService = midiControlActionExecutionService;
    }

    @GetMapping("in-devices")
    public List<MidiDevice> getInDevices() throws Exception {
        return midiService.getMidiDevices(DefaultMidiService.MidiDirection.IN);
    }

    @GetMapping("out-devices")
    public List<MidiDevice> getOutDevices() throws Exception {
        return midiService.getMidiDevices(DefaultMidiService.MidiDirection.OUT);
    }

    @PostMapping("play")
    public ResponseEntity<Void> sendMessage(@RequestParam("command") int command, @RequestParam("channel") int channel,
                                            @RequestParam("note") int note, @RequestParam("velocity") int velocity) {

        MidiSignal midiSignal = new MidiSignal();

        midiSignal.setCommand(command);
        midiSignal.setChannel(channel);
        midiSignal.setNote(note);
        midiSignal.setVelocity(velocity);

        midiRoutingService.sendSignal(midiSignal, settingsService.getSettings().getRemoteMidiRoutingList());

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("test-control")
    public ResponseEntity<Void> testControl(@RequestParam("command") int command, @RequestParam("channel") int channel,
                                            @RequestParam("note") int note, @RequestParam("velocity") int velocity) throws Exception {

        MidiSignal midiSignal = new MidiSignal();

        midiSignal.setCommand(command);
        midiSignal.setChannel(channel);
        midiSignal.setNote(note);
        midiSignal.setVelocity(velocity);

        midiControlActionExecutionService.processMidiSignal(midiSignal);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("activate-midi-learn")
    public ResponseEntity<Void> activateMidiLearn() {
        notificationService.setMidiLearn(true);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("deactivate-midi-learn")
    public ResponseEntity<Void> deactivateMidiLearn() {
        notificationService.setMidiLearn(false);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
