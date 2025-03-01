package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.midi.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;
import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/midi")
@CrossOrigin
class MidiController {

    private final ControllerService controllerService;
    private final ActivityNotificationMidiService activityNotificationMidiService;
    private final MidiService midiService;
    private final MidiActionExecutionService midiActionExecutionService;

    private final MidiRouter midiRouter;

    private MidiController(ControllerService controllerService, SettingsService settingsService, ActivityNotificationMidiService activityNotificationMidiService, MidiService midiService, MidiActionExecutionService midiActionExecutionService, MidiRouterFactory midiRouterFactory) {
        this.controllerService = controllerService;
        this.activityNotificationMidiService = activityNotificationMidiService;
        this.midiService = midiService;
        this.midiActionExecutionService = midiActionExecutionService;

        midiRouter = midiRouterFactory.getMidiRouter(settingsService.getSettings().getRemoteMidiRoutingList());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return controllerService.handleException(exception);
    }

    @GetMapping("in-devices")
    public List<MidiDevice> getInDevices() throws Exception {
        return midiService.getMidiDevices(MidiDirection.IN);
    }

    @GetMapping("out-devices")
    public List<MidiDevice> getOutDevices() throws Exception {
        return midiService.getMidiDevices(MidiDirection.OUT);
    }

    @PostMapping("send-message")
    public ResponseEntity<Void> sendMessage(@RequestBody MidiSignal midiSignal) throws InvalidMidiDataException {
        midiRouter.sendSignal(midiSignal.getShortMessage(), MidiSource.REMOTE);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("test-control")
    public ResponseEntity<Void> testControl(@RequestParam("command") int command, @RequestParam("channel") int channel,
                                            @RequestParam("data1") int data1, @RequestParam("data2") int data2) throws Exception {

        ShortMessage shortMessage = new ShortMessage();
        shortMessage.setMessage(command, channel, data1, data2);

        midiActionExecutionService.processMidiSignal(shortMessage);

        return new ResponseEntity<>(HttpStatus.OK);
    }

}
