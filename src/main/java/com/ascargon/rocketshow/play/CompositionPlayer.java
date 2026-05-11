package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.ActionTriggerComposition;
import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.audio.ActivityNotificationAudioService;
import com.ascargon.rocketshow.midi.ActivityNotificationMidiService;
import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.AudioService;
import com.ascargon.rocketshow.gstreamer.GstApi;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.lighting.designer.Project;
import com.ascargon.rocketshow.midi.*;
import com.ascargon.rocketshow.util.ActionExecutionService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.video.HdmiService;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.freedesktop.gstreamer.lowlevel.GType;
import org.freedesktop.gstreamer.lowlevel.GValueAPI;
import org.freedesktop.gstreamer.message.Message;
import org.freedesktop.gstreamer.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Set;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Manage the playing of a composition. Read all files and create the Gstreamer pipeline.
 */
@Service
public class CompositionPlayer {

    private final static Logger logger = LoggerFactory.getLogger(CompositionPlayer.class);

    private final String uuid = String.valueOf(UUID.randomUUID());

    public enum PlayState {
        PLAYING, // Is the composition playing?
        PAUSED, // Is the composition paused?
        STOPPING, // Is the composition being stopped?
        STOPPED, // Is the composition stopped?
        LOADING, // Is the composition waiting for all files to be loaded to start playing?
        LOADED // Has the composition finished loading all files?
    }

    private final NotificationService notificationService;
    private final ActivityNotificationMidiService activityNotificationMidiService;
    private final PlayerService playerService;
    private final SettingsService settingsService;
    private final CapabilitiesService capabilitiesService;
    private final ActivityNotificationAudioService activityNotificationAudioService;
    private final SetService setService;
    private final LightingService lightingService;
    private final DesignerService designerService;
    private final OperatingSystemInformationService operatingSystemInformationService;
    private final AudioService audioService;
    private final MidiRouterFactory midiRouterFactory;
    private final ActionExecutionService actionExecutionService;
    private final MidiService midiService;
    private final HdmiService hdmiService;

    private Composition composition;
    private PlayState playState = PlayState.STOPPED;
    private boolean seekAfterPlay = false; // Seek, as soon as the pipeline is playing
    private boolean firstPlayDone = false; // Has the pipeline played at least once?
    private long startPosition = 0; // The position, the composition started the last play
    private long lastPlayTimeMillis; // The system time, when playing started
    private ScheduledFuture<?> autoStopHandle;

    private final MidiMapping midiMapping = new MidiMapping();

    // Is this the default composition?
    private boolean isDefaultComposition = false;

    // Is this composition played as a sample?
    private boolean isSample = false;

    // The gstreamer pipeline, used to sync all files in this composition
    private Pipeline pipeline;
    private List<Element> volumeList = new ArrayList<>();

    // Execute the action triggers at the specified positions
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduleActionTriggerHandle;
    private List<ScheduledFuture<?>> actionTriggerHandleList = new ArrayList<>();

    // Ensure, each trigger is executed only once (during inconsistencies when re-scheduling periodically)
    private List<ActionTriggerComposition> remainingActionTriggerCompositionList;

    // All MIDI routers
    private final List<MidiRouter> midiRouterList = new ArrayList<>();

    // A MIDI message parser for MIDI files inside this composition
    private final MidiMessageParser midiMessageParser = new MidiMessageParser();

    public CompositionPlayer(DefaultPlayerService playerService, MidiRouterFactory midiRouterFactory) {
        this.playerService = playerService;
        this.notificationService = playerService.getNotificationService();
        this.activityNotificationMidiService = playerService.getActivityNotificationMidiService();
        this.settingsService = playerService.getSettingsService();
        this.capabilitiesService = playerService.getCapabilitiesService();
        this.activityNotificationAudioService = playerService.getActivityNotificationAudioService();
        this.setService = playerService.getSetService();
        this.lightingService = playerService.getLightingService();
        this.designerService = playerService.getDesignerService();
        this.operatingSystemInformationService = playerService.getOperatingSystemInformationService();
        this.audioService = playerService.getAudioService();
        this.midiRouterFactory = midiRouterFactory;
        this.actionExecutionService = playerService.getActionExecutionService();
        this.midiService = playerService.getMidiService();
        this.hdmiService = playerService.getHdmiService();

        this.midiMapping.setParent(settingsService.getSettings().getMidiMapping());
    }

    private void processMidiBuffer(ByteBuffer byteBuffer, MidiRouter midiRouter) {
        try {
            Optional<MidiMessage> maybeMessage = midiMessageParser.offerByteBuffer(byteBuffer);
            maybeMessage.ifPresent(midiMessage -> {
                try {
                    midiRouter.sendSignal(midiMessage, MidiSource.MIDI_FILE);
                } catch (InvalidMidiDataException e) {
                    logger.error("Could not send MIDI signal from composition file", e);
                }

                if (settingsService.getSettings().getEnableMonitor() && midiMessage instanceof ShortMessage) {
                    activityNotificationMidiService.notifyClients((ShortMessage) midiMessage, MidiDirection.IN, MidiSource.MIDI_FILE, null);
                }
            });
        } catch (InvalidMidiDataException e) {
            logger.error("Could not create MIDI signal from composition file", e);
        }
    }

    private Element getGstVideoSink() {
        if (OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            return ElementFactory.make("osxvideosink", "osxvideosink");
        }
        return ElementFactory.make("kmssink", "kmssink");
    }

    private int getAudioBusStartChannel(AudioBus audioBus) {
        int startChannelIndex = 0;

        if (OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            return 0;
        }

        // Get the starting channel of the current bus
        for (AudioBus settingsAudioBus : settingsService.getSettings().getAudioBusList()) {
            if (settingsAudioBus.getAudioDevice().getId() == audioBus.getAudioDevice().getId()) {
                if (settingsAudioBus.getName().equals(audioBus.getName())) {
                    break;
                } else {
                    startChannelIndex += settingsAudioBus.getChannels();
                }
            }
        }

        return startChannelIndex;
    }

    private float getChannelVolume(AudioBus audioBus, int outputChannelIndex, int inputChannelIndex, float volume) {
        int startChannelIndex = getAudioBusStartChannel(audioBus);

        if (outputChannelIndex < startChannelIndex) {
            return 0;
        }

        if (inputChannelIndex >= audioBus.getChannels()) {
            return 0;
        }

        if (inputChannelIndex == outputChannelIndex - startChannelIndex) {
            return volume;
        } else {
            return 0;
        }
    }

    private void addVideoToPipelineRaspberry3(VideoCompositionFile videoCompositionFile, int index) {
        PlayBin playBin = (PlayBin) ElementFactory.make("playbin", "playbin" + index);
        playBin.set("uri", "file://" + settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getVideoPath() + File.separator + videoCompositionFile.getName());
        pipeline.add(playBin);
    }

    private void addVideoToPipeline(VideoCompositionFile videoCompositionFile, int index) {
        logger.debug("Add video file to pipeline");

        // Does not work on OS X
        // See http://gstreamer-devel.966125.n4.nabble.com/OpenGL-renderer-window-td4686092.html

        // add video for raspberry pi 3, if necessary
        if (OperatingSystemInformation.Type.LINUX.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            if (OperatingSystemInformation.SubType.RASPBERRYOS.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType()) && OperatingSystemInformation.RaspberryVersion.MODEL_3.equals(operatingSystemInformationService.getOperatingSystemInformation().getRaspberryVersion())) {

                addVideoToPipelineRaspberry3(videoCompositionFile, index);
                return;
            }
        }

        URIDecodeBin videoSource = (URIDecodeBin) ElementFactory.make("uridecodebin", "videouridecodebin");
        videoSource.set("uri", "file://" + settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getVideoPath() + File.separator + videoCompositionFile.getName());

        Element videoQueue = ElementFactory.make("queue", "videoqueue");
        videoSource.connect((Element.PAD_ADDED) (Element element, Pad pad) -> {
            Caps caps = pad.getCurrentCaps();

            String name = caps.getStructure(0).getName();

            pad.set("offset", (settingsService.getSettings().getOffsetMillisVideo() + videoCompositionFile.getOffsetMillis()) * 1000000L);

            if (name.startsWith("video/x-raw")) {
                pad.link(videoQueue.getSinkPads().get(0));
            } else if (name.startsWith("audio/x-raw")) {
                // TODO where should the audio go to? hdmisink not available in Debian Buster anymore.
            }
        });
        pipeline.add(videoSource);
        pipeline.add(videoQueue);

        Element kmssink = getGstVideoSink();
        kmssink.set("sync", true);
        pipeline.add(kmssink);

        videoSource.link(videoQueue);
        videoQueue.link(kmssink);
    }

    private void addMidiToPipeline(MidiCompositionFile midiCompositionFile, int index) {
        MidiRouter midiRouter = midiRouterFactory.getMidiRouter(midiCompositionFile.getMidiRoutingList());

        midiRouterList.add(midiRouter);

        for (MidiRouting midiRouting : midiCompositionFile.getMidiRoutingList()) {
            midiRouting.getMidiMapping().setParent(midiMapping);
        }

        Element midiFileSource = ElementFactory.make("filesrc", "midifilesrc" + index);
        midiFileSource.set("location", settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getMidiPath() + "/" + midiCompositionFile.getName());
        pipeline.add(midiFileSource);

        Element midiParse = ElementFactory.make("midiparse", "midiparse" + index);
        pipeline.add(midiParse);

        Element queue = ElementFactory.make("queue", "midisinkqueue" + index);
        pipeline.add(queue);

        AppSink midiSink = (AppSink) ElementFactory.make("appsink", "midisink" + index);
        // Required to actually send the signals
        midiSink.set("emit-signals", true);
        pipeline.add(midiSink);

        midiParse.getSrcPads().get(0).set("offset", (settingsService.getSettings().getOffsetMillisMidi() + midiCompositionFile.getOffsetMillis()) * 1000000L);

        // Sometimes preroll and sometimes new-sample events get fired. We have
        // to process both.
        midiSink.connect((AppSink.NEW_SAMPLE) element -> {
            Sample sample = element.pullSample();
            Buffer buffer = sample.getBuffer();
            processMidiBuffer(buffer.map(false), midiRouter);
            buffer.unmap();
            sample.dispose();
            return FlowReturn.OK;
        });
        midiSink.connect((AppSink.NEW_PREROLL) element -> {
            Sample sample = element.pullPreroll();
            Buffer buffer = sample.getBuffer();
            processMidiBuffer(buffer.map(false), midiRouter);
            buffer.unmap();
            sample.dispose();
            return FlowReturn.OK;
        });

        midiFileSource.link(midiParse);
        midiParse.link(queue);
        queue.link(midiSink);
    }

    private float getCombinedVolume(float compositionVolume, float fileVolume) {
        float combinedVolume = compositionVolume * fileVolume;

        // scale should be logarithmic. a "good-enough"-curve is just x^4.
        // also see: https://www.dr-lex.be/info-stuff/volumecontrols.html
//        return (float) Math.pow(combinedVolume, 4);
        return combinedVolume;
    }

    private void addAudioToPipeline(AudioCompositionFile audioCompositionFile, Map<AudioDevice, Element> audioMixerList, int index) throws Exception {
        logger.debug("Add audio file to pipeline...");

        AudioBus audioBus = settingsService.getAudioBusByName(audioCompositionFile.getOutputBus());
        AudioDevice audioDevice = audioBus.getAudioDevice();

        if (audioDevice == null && !OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            // no audio device configured -> no output
            return;
        }

        URIDecodeBin audioSource = (URIDecodeBin) ElementFactory.make("uridecodebin", "audiouridecodebin" + index);
        audioSource.set("uri", "file://" + settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getAudioPath() + File.separator + audioCompositionFile.getName());
        pipeline.add(audioSource);

        Element audioConvert = ElementFactory.make("audioconvert", "audioconvert" + index);
        audioSource.connect((Element.PAD_ADDED) (Element element, Pad pad) -> {
            Caps caps = pad.getCurrentCaps();

            String name = caps.getStructure(0).getName();

            if ("audio/x-raw-float".equals(name) || "audio/x-raw-int".equals(name) || "audio/x-raw".equals(name)) {
                pad.link(audioConvert.getSinkPads().get(0));
            }
        });

        audioConvert.getSrcPads().get(0).set("offset", (settingsService.getSettings().getOffsetMillisAudio() + audioCompositionFile.getOffsetMillis()) * 1000000L);
        pipeline.add(audioConvert);

        Element audioResample = ElementFactory.make("audioresample", "audioresample" + index);
        pipeline.add(audioResample);

        logger.debug("Apply mix matrix...");

        // Apply the mix matrix
        GValueAPI.GValue mixMatrix = new GValueAPI.GValue();
        GValueAPI.GVALUE_API.g_value_init(mixMatrix, GstApi.GST_API.gst_value_array_get_type());

        // Repeat for each output channel
        for (int i = 0; i < audioService.getChannelCountByAudioDevice(settingsService.getSettings(), audioDevice); i++) {
            GValueAPI.GValue outputChannel = new GValueAPI.GValue();
            GValueAPI.GVALUE_API.g_value_init(outputChannel, GstApi.GST_API.gst_value_array_get_type());

            // Fill the channel with the input channels
            for (int j = 0; j < audioCompositionFile.getChannels(); j++) {
                GValueAPI.GValue inputChannel = new GValueAPI.GValue(GType.FLOAT);

                float channelVolume = getChannelVolume(audioBus, i, j, getCombinedVolume(audioCompositionFile.getVolume(), composition.getAudioVolume()));

                inputChannel.setValue(channelVolume);
                GstApi.GST_API.gst_value_array_append_value(outputChannel, inputChannel.getPointer());
                GValueAPI.GVALUE_API.g_value_unset(inputChannel);
            }

            GstApi.GST_API.gst_value_array_append_value(mixMatrix, outputChannel.getPointer());
            GValueAPI.GVALUE_API.g_value_unset(outputChannel);
        }

        logger.debug("Mix-matrix for " + audioCompositionFile.getName() + ": " + mixMatrix);

        GstApi.GST_API.g_object_set_property(audioConvert, "mix-matrix", mixMatrix.getPointer());
        GValueAPI.GVALUE_API.g_value_unset(mixMatrix);

        // Find the mixer belonging to the current bus' devices
        Element audioMixer = null;

        if (OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            audioMixer = audioMixerList.entrySet().iterator().next().getValue();
        } else {
            for (Map.Entry<AudioDevice, Element> entry : audioMixerList.entrySet()) {
                if (entry.getKey().getId() == audioDevice.getId()) {
                    audioMixer = entry.getValue();
                    break;
                }
            }
        }

        if (audioMixer == null) {
            throw new Exception("Could not find a mixer element for the audio device " + audioDevice.getKey());
        }

        // Link the elements
        audioConvert.link(audioResample);
        audioResample.link(audioMixer);
    }

    // Get All audio devices used in the composition
    private List<AudioDevice> getAudioDeviceInCompositionList(Composition composition) {
        Set<AudioDevice> audioDeviceSet = new HashSet<>();

        // Add a dummy audio device for OSX
        if (OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            AudioDevice audioDevice = new AudioDevice();
            audioDevice.setId(0);
            audioDevice.setName("dummy");
            audioDevice.setKey("dummy");
            audioDeviceSet.add(audioDevice);
        }

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile instanceof AudioCompositionFile audioCompositionFile) {
                AudioBus audioBus = settingsService.getAudioBusByName(audioCompositionFile.getOutputBus());
                if (audioBus != null && audioBus.getAudioDevice() != null) {
                    audioDeviceSet.add(audioBus.getAudioDevice());
                }
            }
        }
        return audioDeviceSet.stream().toList();
    }

    // Prepare each used audio device for output
    private Map<AudioDevice, Element> prepareAudioDevices(Composition composition, boolean provideClock) {
        Map<AudioDevice, Element> audioMixerList = new HashMap<>();

        // Only use the first audio device as clock master (if audio should provide a clock at all)
        boolean provideClockFirstAudioDevice = provideClock;

        for (AudioDevice audioDevice : getAudioDeviceInCompositionList(composition)) {
            String audioDeviceAlsaName = audioService.getAudioDeviceAlsaName(audioDevice);

            Element audioMixer = ElementFactory.make("audiomixer", "audiomixer_" + audioDeviceAlsaName);

            audioMixerList.put(audioDevice, audioMixer);

            pipeline.add(audioMixer);

            // Add a capsfilter to enforce multi-channel out. Otherwise only 2 will be mixed
            Element capsFilter = ElementFactory.make("capsfilter", "capsfilter_" + audioDeviceAlsaName);
            Caps caps = GstApi.GST_API.gst_caps_from_string("audio/x-raw,rate=" + settingsService.getSettings().getAudioRate() + ",channels=" + audioService.getChannelCountByAudioDevice(settingsService.getSettings(), audioDevice));
            capsFilter.set("caps", caps);
            pipeline.add(capsFilter);

            audioMixer.link(capsFilter);

            Element queue = ElementFactory.make("queue", "audiosinkqueue_" + audioDeviceAlsaName);
            pipeline.add(queue);

            BaseSink sink = audioService.getGstAudioSink(audioDevice, provideClockFirstAudioDevice);
            pipeline.add(sink);

            Element level = null;
            if (!isSample && settingsService.getSettings().getEnableMonitor()) {
                level = ElementFactory.make("level", "level_" + audioDeviceAlsaName);
                // 1000 Milliseconds
                level.set("interval", 1000 * 1000000);
                level.set("post-messages", true);
                pipeline.add(level);
            }

            if (level == null) {
                capsFilter.link(queue);
            } else {
                capsFilter.link(level);
                level.link(queue);
            }

            Element volume = ElementFactory.make("volume", "volume_" + audioDeviceAlsaName);
            pipeline.add(volume);
            volumeList.add(volume);

            queue.link(volume);

            volume.link(sink);

            provideClockFirstAudioDevice = false;
        }

        return audioMixerList;
    }

    private boolean hasVideo() {
        if (!hdmiService.isConnected()) {
            // Even if the composition had video files, we will not play them,
            // because no device is connected to the HDMI interface
            return false;
        }

        for (int i = 0; i < composition.getCompositionFileList().size(); i++) {
            CompositionFile compositionFile = composition.getCompositionFileList().get(i);

            if (compositionFile.isActive() && compositionFile instanceof VideoCompositionFile && capabilitiesService.getCapabilities().isGstreamer()) {
                return true;
            }
        }

        return false;
    }

    private void createGstreamerPipeline() throws Exception {
        boolean hasVideo = hasVideo();
        pipeline = new Pipeline();
        Bus bus = GstApi.GST_API.gst_element_get_bus(pipeline);

        bus.connect((Bus.ERROR) (GstObject source, int code, String message) -> {
            logger.error("GST error: " + message);
            try {
                notificationService.notifyClients(message + " Please check your audio settings.");
            } catch (Exception e) {
                logger.error("Could not notify clients about an error", e);
            }
            try {
                stop();
            } catch (Exception e) {
                logger.error("Could not stop compostion triggered by an error", e);
            }
        });
        bus.connect((Bus.WARNING) (GstObject source, int code, String message) -> logger.warn("GST: " + message));
        bus.connect((Bus.INFO) (GstObject source, int code, String message) -> logger.warn("GST: " + message));
        bus.connect((GstObject source, State old, State newState, State pending) -> {
            if (source.getTypeName().equals("GstPipeline")) {
                if (newState == State.PLAYING) {
                    firstPlayDone = true;

                    // We changed to playing, maybe we need to seek to the startPosition (not possible before first play)
                    if (seekAfterPlay) {
                        seekAfterPlay = false;

                        try {
                            seek(startPosition);
                        } catch (Exception e) {
                            logger.error("Could not set start position when changed to playing", e);
                        }
                    }

                    playState = PlayState.PLAYING;

                    if (!isDefaultComposition && !isSample) {
                        try {
                            notificationService.notifyClients(playerService, setService);
                        } catch (Exception e) {
                            logger.error("Could not notify clients about a playing event", e);
                        }
                    }
                }
            }
        });
        bus.connect((Bus.EOS) source -> {
            if (composition.isLoop()) {
                pipeline.seek(0, TimeUnit.MILLISECONDS);
            } else {
                try {
                    playerService.compositionPlayerFinishedPlaying(this);
                } catch (Exception e) {
                    logger.error("Could not stop the composition after end of stream", e);
                }
            }
        });
        bus.connect((Bus bus1, Message message) -> {
            if (message.getType().equals(MessageType.ELEMENT)) {
                Structure structure = message.getStructure();

                if (structure.getName().equals("level")) {
                    try {
                        // We got a level message
                        activityNotificationAudioService.notifyClients(structure.getDoubles("peak"));
                    } catch (Exception e) {
                        logger.error("Could not process level message", e);
                    }
                }
            }
        });

        GstApi.GST_API.gst_object_unref(bus);

        // Prepare the audio devices and keep the mixer as links to connect to.
        // Use audio to provide a clock, if there's no video. Otherwise, let video provide the clock
        // (more stable, avoids jittering issues).
        Map<AudioDevice, Element> audioMixerList = prepareAudioDevices(composition, !hasVideo);

        // Load all files, create the pipeline and handle exceptions to pipeline-playing
        for (int i = 0; i < composition.getCompositionFileList().size(); i++) {
            CompositionFile compositionFile = composition.getCompositionFileList().get(i);

            if (compositionFile.isActive()) {
                if (compositionFile instanceof MidiCompositionFile) {
                    addMidiToPipeline((MidiCompositionFile) compositionFile, i);
                } else if (compositionFile instanceof AudioCompositionFile && capabilitiesService.getCapabilities().isGstreamer()) {
                    addAudioToPipeline((AudioCompositionFile) compositionFile, audioMixerList, i);
                } else if (compositionFile instanceof VideoCompositionFile && capabilitiesService.getCapabilities().isGstreamer() && hasVideo) {
                    addVideoToPipeline((VideoCompositionFile) compositionFile, i);
                }
            }
        }
    }

    // TODO also allow to set the master volume in the interface
    private void setMasterVolume(double volume) {
        logger.debug("Set the master volume to " + volume);
        for (Element volumeElement : volumeList) {
            volumeElement.set("volume", volume);
        }
    }

    private void stopPipeline() {
        if (pipeline != null) {
            // Avoid audio artifacts while stopping the pipeline
            setMasterVolume(0.0);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            pipeline.stop();
            pipeline.dispose();
            pipeline = null;
        }
        volumeList = new ArrayList<>();
    }

    private void calculateRemainingActionTriggerList() {
        remainingActionTriggerCompositionList = new CopyOnWriteArrayList<>();
        remainingActionTriggerCompositionList.addAll(composition.getActionTriggerList().stream().filter(trigger -> trigger.getPositionMillis() >= getPositionMillis()).toList());
    }

    // Load all files and construct the complete GST pipeline
    public void loadFiles() throws Exception {
        boolean hasActiveFile = false;
        firstPlayDone = false;

        if (playState != PlayState.STOPPED) {
            return;
        }

        // Search for active files
        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive()) {
                hasActiveFile = true;
                break;
            }
        }

        if (!hasActiveFile
                && designerService.getProjectByCompositionName(composition.getName()) == null
                && composition.getActionTriggerList().isEmpty()
        ) {
            // No files to be played, no actions and no designer project (maybe a lead sheet)
            if (!isDefaultComposition && !isSample) {
                notificationService.notifyClients(playerService, setService);
            }

            return;
        }

        if (hasActiveFile && !capabilitiesService.getCapabilities().isGstreamer()) {
            throw new Exception("Gstreamer is required to play this composition but not available");
        }

        playState = PlayState.LOADING;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService, setService);
        }

        logger.debug("Loading composition '" + composition.getName() + "...");

        // Destroy an old pipeline, if required
        stopPipeline();

        // Initialize lighting without designer
        lightingService.setExternalSync(false);

        // Destroy an old designer project, if required
        this.designerService.close();

        if (hasActiveFile) {
            createGstreamerPipeline();
        }

        // Prepare the scheduler for all contained action triggers (and some buffer for overlapping runs)
        scheduler = Executors.newScheduledThreadPool(composition.getActionTriggerList().size() + 5);

        logger.debug("Composition '" + composition.getName() + "' loaded");

        // Maybe we are stopping meanwhile
        if (playState == PlayState.LOADING && !isDefaultComposition && !isSample) {
            playState = PlayState.LOADED;
            notificationService.notifyClients(playerService, setService);
        }
    }

    private void startAutoStopTimer() {
        if (pipeline != null) {
            // We have a pipeline to handle the stop -> no need for the timer
            return;
        }

        final Runnable runnable = () -> {
            try {
                logger.info("Composition finished. Stopping...");
                stop();
            } catch (Exception e) {
                logger.error("Could not auto stop the composition after the timer ran out", e);
            }
        };

        // Add a small buffer before stopping the composition to ensure, the last triggers are executed
        autoStopHandle = scheduler.schedule(runnable, composition.getDurationMillis() - getPositionMillis() + 50, MILLISECONDS);
    }

    private void stopAutoStopTimer() {
        if (autoStopHandle == null) {
            return;
        }

        // Don't interrupt the thread, because it might need to notify some websocket sessions
        autoStopHandle.cancel(false);
        autoStopHandle = null;
    }

    private void startActionTriggerTimer() {
        // Schedule all action triggers in the future of the current position

        // Cancel all current schedules
        for (ScheduledFuture<?> handle : actionTriggerHandleList) {
            handle.cancel(false);
        }

        // Create new schedules for each trigger
        long currentPositionMillis = getPositionMillis();
        for (ActionTriggerComposition actionTriggerComposition : remainingActionTriggerCompositionList) {
            final Runnable runnable = () -> {
                try {
                    actionExecutionService.executeFromTrigger(actionTriggerComposition);
                } catch (Exception e) {
                    logger.error("Could not execute actions triggered at position {}", actionTriggerComposition.getPositionMillis(), e);
                    try {
                        notificationService.notifyClients("Could not execute action: " + e.getCause());
                    } catch (Exception ex) {
                    }
                }
                remainingActionTriggerCompositionList.remove(actionTriggerComposition);
            };
            ScheduledFuture<?> handle = scheduler.schedule(runnable, actionTriggerComposition.getPositionMillis() - currentPositionMillis, MILLISECONDS);
            actionTriggerHandleList.add(handle);
        }
    }

    private void scheduleActionTriggerTimers() {
        // Schedule the timers to execute the actions

        if (composition.getActionTriggerList().isEmpty()) {
            // No actions to trigger
            return;
        }

        if (pipeline == null) {
            // Only schedule once
            startActionTriggerTimer();
            return;
        }

        // Schedule periodically to stay in sync with the pipeline, which might drift under certain circumstances

        if (scheduleActionTriggerHandle != null) {
            // Timer already started
            return;
        }

        final Runnable runnable = this::startActionTriggerTimer;
        scheduleActionTriggerHandle = scheduler.scheduleAtFixedRate(runnable, 0, 2000, MILLISECONDS);
    }

    private void stopActionTriggerTimer() {
        if (scheduleActionTriggerHandle == null) {
            // No timer running currently
            return;
        }

        scheduleActionTriggerHandle.cancel(false);
        scheduleActionTriggerHandle = null;
    }

    public void play() throws Exception {
        if (composition == null) {
            return;
        }

        // Load the files, if not already done by a previously by a separate call
        loadFiles();

        // Load the designer files
        // -> no separate step, because there's only one global handler and the default composition is closed
        // after the loading step.
        Project designerProject = designerService.getProjectByCompositionName(composition.getName());
        if (designerProject != null) {
            logger.info("Designer project found. Load it...");
            designerService.load(this, designerProject, pipeline);
        }

        // All files are loaded -> play the composition (start each file)
        logger.info("Playing composition '" + composition.getName() + "'...");

        if (pipeline != null) {
            pipeline.play();
        }

        lastPlayTimeMillis = System.currentTimeMillis();

        calculateRemainingActionTriggerList();

        startAutoStopTimer();
        scheduleActionTriggerTimers();

        designerService.play();

        if (pipeline == null) {
            // No pipeline to trigger playing state -> just issue it right now
            playState = PlayState.PLAYING;
            notificationService.notifyClients(playerService, setService);
        }
    }

    public void pause() throws Exception {
        if (playState == PlayState.PAUSED) {
            return;
        }

        logger.info("Pausing composition '" + composition.getName() + "'");

        // Pause the composition
        if (pipeline != null) {
            pipeline.pause();
        }

        designerService.pause();

        startPosition = getPositionMillis();

        playState = PlayState.PAUSED;

        stopAutoStopTimer();
        stopActionTriggerTimer();

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService, setService);
        }
    }

    public void stop() throws Exception {
        startPosition = 0;
        seekAfterPlay = false;
        firstPlayDone = false;

        if (composition == null || playState == PlayState.STOPPED) {
            return;
        }

        playState = PlayState.STOPPING;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService, setService);
        }
        logger.info("Stopping composition '{}'", composition.getName());

        // Stop the composition
        stopPipeline();

        designerService.close();

        // Close all MIDI routers
        for (MidiRouter midiRouter : midiRouterList) {
            midiRouter.close();
        }

        playState = PlayState.STOPPED;

        stopAutoStopTimer();
        stopActionTriggerTimer();

        if (!isSample && !isDefaultComposition) {
            notificationService.notifyClients(playerService, setService);
        }

        logger.info("Composition '" + composition.getName() + "' stopped");
    }

    public void togglePlay() throws Exception {
        if (playState == PlayState.PLAYING) {
            stop();
        } else {
            play();
        }
    }

    public void seek(long positionMillis) throws Exception {
        if (pipeline == null || !firstPlayDone) {
            seekAfterPlay = true;
        }
        startPosition = positionMillis;

        logger.debug("Seek to position " + positionMillis);

        if (pipeline != null) {
            pipeline.seek(positionMillis, TimeUnit.MILLISECONDS);
        }

        this.lastPlayTimeMillis = System.currentTimeMillis();

        calculateRemainingActionTriggerList();
        scheduleActionTriggerTimers();

        if (!isSample) {
            notificationService.notifyClients(playerService, setService);
        }
    }

    public long getPositionMillis() {
        if (composition == null) {
            return 0;
        }

        // If we're not playing, just return the current start position to resume playing
        if (playState != PlayState.PLAYING) {
            return startPosition;
        }

        // If there is a pipeline -> use it (only if playing, querying its status is not reliably otherwise)
        if (pipeline != null) {
            long queriedMillis = pipeline.queryPosition(TimeUnit.MILLISECONDS);
            if (queriedMillis >= startPosition) {
                // only return, if the query worked (shortly after seeking it's 0, even if we're playing)
                return queriedMillis;
            }
        }

        // If there is no pipeline (e.g. only actions) and we're playing -> fallback
        return System.currentTimeMillis() - lastPlayTimeMillis + startPosition;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof CompositionPlayer) {
            CompositionPlayer compositionPlayer = (CompositionPlayer) object;

            return this.uuid.equals(compositionPlayer.uuid);
        }

        return false;
    }

    public PlayState getPlayState() {
        return playState;
    }

    public void setPlayState(PlayState playState) {
        this.playState = playState;
    }

    public Composition getComposition() {
        return composition;
    }

    public void setComposition(Composition composition) throws Exception {
        this.composition = composition;

        if (!isSample && !isDefaultComposition) {
            notificationService.notifyClients(playerService, setService);
        }
    }

    public boolean isDefaultComposition() {
        return isDefaultComposition;
    }

    public void setDefaultComposition(boolean defaultComposition) {
        this.isDefaultComposition = defaultComposition;
    }

    public boolean isSample() {
        return isSample;
    }

    public void setSample(boolean sample) {
        isSample = sample;
    }

}
