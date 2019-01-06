package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.Midi2LightingConvertService;
import com.ascargon.rocketshow.midi.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sound.midi.InvalidMidiDataException;
import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/midi")
@CrossOrigin
class MidiController {

    private final ActivityNotificationMidiService activityNotificationMidiService;
    private final MidiService midiService;
    private final MidiControlActionExecutionService midiControlActionExecutionService;

    private MidiRouter midiRouter;

    private MidiController(SettingsService settingsService, ActivityNotificationMidiService activityNotificationMidiService, MidiService midiService, MidiControlActionExecutionService midiControlActionExecutionService, Midi2LightingConvertService midi2LightingConvertService, LightingService lightingService, MidiDeviceOutService midiDeviceOutService) {
        this.activityNotificationMidiService = activityNotificationMidiService;
        this.midiService = midiService;
        this.midiControlActionExecutionService = midiControlActionExecutionService;

        midiRouter = new MidiRouter(settingsService, midi2LightingConvertService, lightingService, midiDeviceOutService, activityNotificationMidiService, settingsService.getSettings().getRemoteMidiRoutingList());
    }

    @GetMapping("in-devices")
    public List<MidiDevice> getInDevices() throws Exception {
        return midiService.getMidiDevices(MidiSignal.MidiDirection.IN);
    }

    @GetMapping("out-devices")
    public List<MidiDevice> getOutDevices() throws Exception {
        return midiService.getMidiDevices(MidiSignal.MidiDirection.OUT);
    }

    @PostMapping("send-message")
    public ResponseEntity<Void> sendMessage(@RequestParam("command") int command, @RequestParam("channel") int channel,
                                            @RequestParam("note") int note, @RequestParam("velocity") int velocity) throws InvalidMidiDataException {

        MidiSignal midiSignal = new MidiSignal();

        midiSignal.setCommand(command);
        midiSignal.setChannel(channel);
        midiSignal.setNote(note);
        midiSignal.setVelocity(velocity);

        midiRouter.sendSignal(midiSignal);

        activityNotificationMidiService.notifyClients(midiSignal, MidiSignal.MidiDirection.IN, MidiSignal.MidiSource.REMOTE, null);

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

}
