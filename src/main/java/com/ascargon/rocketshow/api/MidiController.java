package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.midi.MidiRoutingService;
import com.ascargon.rocketshow.midi.MidiSignal;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/midi")
public class MidiController {

    private final static Logger logger = LoggerFactory.getLogger(MidiController.class);

    private SettingsService settingsService;
    private MidiRoutingService midiRoutingService;

    public MidiController(SettingsService settingsService, MidiRoutingService midiRoutingService) {
        this.settingsService = settingsService;
        this.midiRoutingService = midiRoutingService;
    }

//	@Path("in-devices")
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	public List<MidiDevice> getInDevices() throws Exception {
//		return MidiUtil.getMidiDevices(MidiDirection.IN);
//	}
//
//	@Path("out-devices")
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	public List<MidiDevice> getOutDevices() throws Exception {
//		return MidiUtil.getMidiDevices(MidiDirection.OUT);
//	}

    @PostMapping("play")
    public ResponseEntity<Void> sendMessage(@RequestParam("command") int command, @RequestParam("channel") int channel,
                                            @RequestParam("note") int note, @RequestParam("velocity") int velocity) {

        MidiSignal midiSignal = new MidiSignal();

        midiSignal.setCommand(command);
        midiSignal.setChannel(channel);
        midiSignal.setNote(note);
        midiSignal.setVelocity(velocity);

        midiRoutingService.sendSignal(midiSignal, settingsService.getSettings().getRemoteMidiRoutingList());

        return new ResponseEntity<Void>(HttpStatus.OK);
    }

//	@Path("test-control")
//	@POST
//	@Produces(MediaType.APPLICATION_JSON)
//	@Consumes(MediaType.APPLICATION_JSON)
//	public Response testControl(@QueryParam("command") int command, @QueryParam("channel") int channel,
//			@QueryParam("note") int note, @QueryParam("velocity") int velocity) throws Exception {
//
//		Manager manager = (Manager) context.getAttribute("manager");
//		MidiSignal midiSignal = new MidiSignal();
//
//		midiSignal.setCommand(command);
//		midiSignal.setChannel(channel);
//		midiSignal.setNote(note);
//		midiSignal.setVelocity(velocity);
//
//		manager.getMidi2ActionConverter().processMidiSignal(midiSignal);
//
//		return Response.status(200).build();
//	}
//
//	@Path("activate-midi-learn")
//	@POST
//	@Produces(MediaType.APPLICATION_JSON)
//	public Response activateMidiLearn() {
//		Manager manager = (Manager) context.getAttribute("manager");
//		manager.getMidiInDeviceReceiver().setMidiLearn(true);
//		return Response.status(200).build();
//	}
//
//	@Path("deactivate-midi-learn")
//	@POST
//	@Produces(MediaType.APPLICATION_JSON)
//	public Response deactivateMidiLearn() {
//		Manager manager = (Manager) context.getAttribute("manager");
//		manager.getMidiInDeviceReceiver().setMidiLearn(false);
//		return Response.status(200).build();
//	}

}
