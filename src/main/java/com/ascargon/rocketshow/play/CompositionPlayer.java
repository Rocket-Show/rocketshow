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
import org.freedesktop.gstreamer.event.SeekFlags;
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
    private final MidiTimecodeService midiTimecodeService;
    private final HdmiService hdmiService;

    private Composition composition;
    private PlayState playState = PlayState.STOPPED;
    private boolean firstPlayDone = false; // Has the pipeline played at least once?
    private boolean hasH265Stream = false; // True when a hardware H.265 decoder is in use in the master pipeline
    // True once the master pipeline reached EOS but the composition is still running to let trailing
    // actions (placed after the longest source) fire. Position then advances on the wall clock.
    private boolean masterEosReached = false;
    private long startPosition = 0; // The position, the composition started the last play
    private long lastPlayTimeMillis; // The system time, when playing started
    private ScheduledFuture<?> autoStopHandle;

    private final MidiMapping midiMapping = new MidiMapping();

    // Is this the default composition?
    private boolean isDefaultComposition = false;

    // Is this composition played as a sample?
    private boolean isSample = false;

    // The master gstreamer pipeline, used to sync all files which must stay aligned
    // (the longest source plus all non-looping sources). Drives the composition timeline.
    private Pipeline pipeline;
    private List<Element> volumeList = new ArrayList<>();

    // Slave pipelines: one per looping source that is shorter than the longest source.
    // They run independently (looping on their own) but share the master clock so they
    // don't drift. Empty unless the composition mixes looping and non-looping sources.
    private final List<SlavePipeline> slavePipelineList = new ArrayList<>();

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
        this.midiTimecodeService = playerService.getMidiTimecodeService();
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
        Element kmssink = ElementFactory.make("kmssink", "kmssink");
        kmssink.set("driver-name", "vc4");
        return kmssink;
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

    private void addVideoToPipelineRaspberry3(VideoCompositionFile videoCompositionFile, Pipeline target, int index) {
        PlayBin playBin = (PlayBin) ElementFactory.make("playbin", "playbin" + index);
        playBin.set("uri", "file://" + settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getVideoPath() + File.separator + videoCompositionFile.getName());
        target.add(playBin);
    }

    private void addVideoToPipeline(VideoCompositionFile videoCompositionFile, Pipeline target, int index) {
        logger.debug("Add video file to pipeline");

        // Does not work on OS X
        // See http://gstreamer-devel.966125.n4.nabble.com/OpenGL-renderer-window-td4686092.html

        // add video for raspberry pi 3, if necessary
        if (OperatingSystemInformation.Type.LINUX.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            if (OperatingSystemInformation.SubType.RASPBERRYOS.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType()) && OperatingSystemInformation.RaspberryVersion.MODEL_3.equals(operatingSystemInformationService.getOperatingSystemInformation().getRaspberryVersion())) {

                addVideoToPipelineRaspberry3(videoCompositionFile, target, index);
                return;
            }
        }

        URIDecodeBin videoSource = (URIDecodeBin) ElementFactory.make("uridecodebin", "videouridecodebin" + index);
        videoSource.set("uri", "file://" + settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getVideoPath() + File.separator + videoCompositionFile.getName());

        Element videoQueue = ElementFactory.make("queue", "videoqueue" + index);
        Element videoConvert = ElementFactory.make("videoconvert", "videoconvert" + index);

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
        target.add(videoSource);
        target.add(videoQueue);
        target.add(videoConvert);

        Element kmssink = getGstVideoSink();
        kmssink.set("sync", true);
        target.add(kmssink);

        videoSource.link(videoQueue);
        videoQueue.link(videoConvert);
        videoConvert.link(kmssink);
    }

    private void addMidiToPipeline(MidiCompositionFile midiCompositionFile, Pipeline target, int index) {
        MidiRouter midiRouter = midiRouterFactory.getMidiRouter(midiCompositionFile.getMidiRoutingList());

        midiRouterList.add(midiRouter);

        for (MidiRouting midiRouting : midiCompositionFile.getMidiRoutingList()) {
            midiRouting.getMidiMapping().setParent(midiMapping);
        }

        Element midiFileSource = ElementFactory.make("filesrc", "midifilesrc" + index);
        midiFileSource.set("location", settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getMidiPath() + "/" + midiCompositionFile.getName());
        target.add(midiFileSource);

        Element midiParse = ElementFactory.make("midiparse", "midiparse" + index);
        target.add(midiParse);

        Element queue = ElementFactory.make("queue", "midisinkqueue" + index);
        target.add(queue);

        AppSink midiSink = (AppSink) ElementFactory.make("appsink", "midisink" + index);
        // Required to actually send the signals
        midiSink.set("emit-signals", true);
        target.add(midiSink);

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

    private void addAudioToPipeline(AudioCompositionFile audioCompositionFile, Pipeline target, Map<AudioDevice, Element> audioMixerList, int index) throws Exception {
        logger.debug("Add audio file to pipeline...");

        AudioBus audioBus = settingsService.getAudioBusByUuid(audioCompositionFile.getOutputBusUuid());
        AudioDevice audioDevice = audioBus.getAudioDevice();

        if (audioDevice == null && !OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            // no audio device configured -> no output
            return;
        }

        URIDecodeBin audioSource = (URIDecodeBin) ElementFactory.make("uridecodebin", "audiouridecodebin" + index);
        audioSource.set("uri", "file://" + settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getAudioPath() + File.separator + audioCompositionFile.getName());
        target.add(audioSource);

        Element audioConvert = ElementFactory.make("audioconvert", "audioconvert" + index);
        audioSource.connect((Element.PAD_ADDED) (Element element, Pad pad) -> {
            Caps caps = pad.getCurrentCaps();

            String name = caps.getStructure(0).getName();

            if ("audio/x-raw-float".equals(name) || "audio/x-raw-int".equals(name) || "audio/x-raw".equals(name)) {
                pad.link(audioConvert.getSinkPads().get(0));
            }
        });

        audioConvert.getSrcPads().get(0).set("offset", (settingsService.getSettings().getOffsetMillisAudio() + audioCompositionFile.getOffsetMillis()) * 1000000L);
        target.add(audioConvert);

        Element audioResample = ElementFactory.make("audioresample", "audioresample" + index);
        target.add(audioResample);

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

    // Get all audio devices used by the given files
    private List<AudioDevice> getAudioDeviceList(List<CompositionFile> compositionFileList) {
        Set<AudioDevice> audioDeviceSet = new HashSet<>();

        // Add a dummy audio device for OSX
        if (OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            AudioDevice audioDevice = new AudioDevice();
            audioDevice.setId(0);
            audioDevice.setName("dummy");
            audioDevice.setKey("dummy");
            audioDeviceSet.add(audioDevice);
        }

        for (CompositionFile compositionFile : compositionFileList) {
            if (compositionFile instanceof AudioCompositionFile audioCompositionFile) {
                AudioBus audioBus = settingsService.getAudioBusByUuid(audioCompositionFile.getOutputBusUuid());
                if (audioBus != null && audioBus.getAudioDevice() != null) {
                    audioDeviceSet.add(audioBus.getAudioDevice());
                }
            }
        }
        return audioDeviceSet.stream().toList();
    }

    // Build the output chain (mixer -> capsfilter -> [level] -> queue -> volume -> sink) for one
    // audio device into the given pipeline and return the mixer to link audio sources into.
    private Element buildAudioOutputChain(Pipeline target, AudioDevice audioDevice, boolean provideClock, boolean enableLevel, List<Element> volumeListTarget) {
        String audioDeviceAlsaName = audioService.getAudioDeviceAlsaName(audioDevice);

        Element audioMixer = ElementFactory.make("audiomixer", "audiomixer_" + audioDeviceAlsaName);
        target.add(audioMixer);

        // Add a capsfilter to enforce multi-channel out. Otherwise only 2 will be mixed
        Element capsFilter = ElementFactory.make("capsfilter", "capsfilter_" + audioDeviceAlsaName);
        Caps caps = GstApi.GST_API.gst_caps_from_string("audio/x-raw,rate=" + settingsService.getSettings().getAudioRate() + ",channels=" + audioService.getChannelCountByAudioDevice(settingsService.getSettings(), audioDevice));
        capsFilter.set("caps", caps);
        target.add(capsFilter);

        audioMixer.link(capsFilter);

        Element queue = ElementFactory.make("queue", "audiosinkqueue_" + audioDeviceAlsaName);
        target.add(queue);

        BaseSink sink = audioService.getGstAudioSink(audioDevice, provideClock);
        target.add(sink);

        Element level = null;
        if (enableLevel) {
            level = ElementFactory.make("level", "level_" + audioDeviceAlsaName);
            // 1000 Milliseconds
            level.set("interval", 1000 * 1000000);
            level.set("post-messages", true);
            target.add(level);
        }

        if (level == null) {
            capsFilter.link(queue);
        } else {
            capsFilter.link(level);
            level.link(queue);
        }

        Element volume = ElementFactory.make("volume", "volume_" + audioDeviceAlsaName);
        target.add(volume);
        volumeListTarget.add(volume);

        queue.link(volume);

        volume.link(sink);

        return audioMixer;
    }

    // Prepare each used audio device for output in the master pipeline
    private Map<AudioDevice, Element> prepareAudioDevices(List<CompositionFile> compositionFileList, boolean provideClock) {
        Map<AudioDevice, Element> audioMixerList = new HashMap<>();

        // Only use the first audio device as clock master (if audio should provide a clock at all)
        boolean provideClockFirstAudioDevice = provideClock;

        // Level monitoring is only added on the master pipeline (drives the audio monitor)
        boolean enableLevel = !isSample && settingsService.getSettings().getEnableMonitor();

        for (AudioDevice audioDevice : getAudioDeviceList(compositionFileList)) {
            Element audioMixer = buildAudioOutputChain(pipeline, audioDevice, provideClockFirstAudioDevice, enableLevel, volumeList);
            audioMixerList.put(audioDevice, audioMixer);
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
                notificationService.notifyClients(message);
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
            try {
                onMasterEndOfStream();
            } catch (Exception e) {
                logger.error("Could not handle the master end of stream", e);
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

        // Decide which sources belong in the master pipeline and which loop independently in their
        // own slave pipelines.
        List<CompositionFile> activeFileList = new ArrayList<>();
        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive()) {
                activeFileList.add(compositionFile);
            }
        }

        // The longest active source, and the latest action trigger. Actions placed after the
        // longest source extend the timeline past all media into a wall-clock "action-trailing
        // tail", so the master pipeline no longer has to anchor the end of the composition.
        long longestSourceMillis = 0;
        for (CompositionFile compositionFile : activeFileList) {
            longestSourceMillis = Math.max(longestSourceMillis, compositionFile.getDurationMillis());
        }
        long latestActionMillis = 0;
        for (ActionTriggerComposition actionTrigger : composition.getActionTriggerList()) {
            latestActionMillis = Math.max(latestActionMillis, actionTrigger.getPositionMillis());
        }
        boolean hasActionTrailingTail = latestActionMillis > longestSourceMillis;

        // A source loops in its own slave pipeline iff it is flagged to loop AND it is not needed in
        // the master as the timeline anchor:
        //  - with an action-trailing tail the wall clock anchors the end, so every looping source
        //    (even the longest) can be a slave; if they all loop there is no master pipeline at all.
        //  - without a tail the master's EOS defines the end, so the longest source(s) must stay in
        //    the master and their loop flag has no effect.
        List<CompositionFile> masterFileList = new ArrayList<>();
        List<CompositionFile> slaveFileList = new ArrayList<>();
        for (CompositionFile compositionFile : activeFileList) {
            boolean isSlave = compositionFile.isLoop()
                    && (hasActionTrailingTail || compositionFile.getDurationMillis() < longestSourceMillis);
            if (isSlave) {
                slaveFileList.add(compositionFile);
            } else {
                masterFileList.add(compositionFile);
            }
        }

        if (masterFileList.isEmpty()) {
            // Every source loops (in the action-trailing tail) -> there is no master pipeline; the
            // wall clock drives the timeline and the slaves loop on their own clocks.
            pipeline.dispose();
            pipeline = null;
        } else {
            // Prepare the audio devices used by the master files and keep the mixers to link into.
            // Use audio to provide a clock, if there's no video. Otherwise, let video provide the
            // clock (more stable, avoids jittering issues).
            Map<AudioDevice, Element> audioMixerList = prepareAudioDevices(masterFileList, !hasVideo);

            // Add all master files to the master pipeline
            for (CompositionFile compositionFile : masterFileList) {
                int index = composition.getCompositionFileList().indexOf(compositionFile);
                addCompositionFileToPipeline(compositionFile, pipeline, audioMixerList, index, hasVideo);
            }
        }

        // Build a slave pipeline for each looping source. They are started later (clock-synced to the
        // master if there is one, otherwise free-running on the wall-clock timeline).
        for (CompositionFile compositionFile : slaveFileList) {
            if (compositionFile instanceof AudioCompositionFile && getAudioDeviceList(List.of(compositionFile)).isEmpty()) {
                // No audio device configured -> no output, skip
                continue;
            }
            if (compositionFile instanceof VideoCompositionFile && !hasVideo) {
                // No HDMI device connected -> video won't play, skip
                continue;
            }
            int index = composition.getCompositionFileList().indexOf(compositionFile);
            SlavePipeline slavePipeline = new SlavePipeline(compositionFile, index);
            slavePipeline.build();
            slavePipelineList.add(slavePipeline);
        }
    }

    // Add a single composition file (midi/audio/video) to the given pipeline, applying the same
    // capability/HDMI guards used for the master pipeline. For audio, the matching mixer must
    // already be present in audioMixerList.
    private void addCompositionFileToPipeline(CompositionFile compositionFile, Pipeline target, Map<AudioDevice, Element> audioMixerList, int index, boolean hasVideo) throws Exception {
        if (compositionFile instanceof MidiCompositionFile) {
            addMidiToPipeline((MidiCompositionFile) compositionFile, target, index);
        } else if (compositionFile instanceof AudioCompositionFile && capabilitiesService.getCapabilities().isGstreamer()) {
            addAudioToPipeline((AudioCompositionFile) compositionFile, target, audioMixerList, index);
        } else if (compositionFile instanceof VideoCompositionFile && capabilitiesService.getCapabilities().isGstreamer() && hasVideo) {
            addVideoToPipeline((VideoCompositionFile) compositionFile, target, index);
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
        // Avoid audio artifacts: mute the master and all slaves, then tear everything down
        if (pipeline != null || !slavePipelineList.isEmpty()) {
            setMasterVolume(0.0);
            for (SlavePipeline slavePipeline : slavePipelineList) {
                slavePipeline.mute();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        for (SlavePipeline slavePipeline : slavePipelineList) {
            slavePipeline.stop();
        }
        slavePipelineList.clear();

        if (pipeline != null) {
            pipeline.stop();
            pipeline.dispose();
            pipeline = null;
        }
        volumeList = new ArrayList<>();
    }

    // Start all slave pipelines, locked onto the (already playing) master's clock and phase-aligned
    // to the given master position
    private void startSlaves(long masterPositionMillis) {
        for (SlavePipeline slavePipeline : slavePipelineList) {
            slavePipeline.startSyncedToMaster(masterPositionMillis);
        }
    }

    // Resume all slaves from pause (the shared clock kept them aligned while paused)
    private void resumeSlaves() {
        for (SlavePipeline slavePipeline : slavePipelineList) {
            slavePipeline.resume();
        }
    }

    // Re-phase all slaves to the master's new position (used when the master is looped or seeked)
    private void rephaseSlaves(long masterPositionMillis) {
        for (SlavePipeline slavePipeline : slavePipelineList) {
            slavePipeline.rephaseTo(masterPositionMillis);
        }
    }

    // True if a hardware H.265 decoder is in use anywhere (master or any slave). H.265 cannot
    // seek or pause/resume on the Pi, so these operations are restricted when it is present.
    private boolean hasAnyH265Stream() {
        if (hasH265Stream) {
            return true;
        }
        for (SlavePipeline slavePipeline : slavePipelineList) {
            if (slavePipeline.hasH265()) {
                return true;
            }
        }
        return false;
    }

    private void calculateRemainingActionTriggerList() {
        remainingActionTriggerCompositionList = new CopyOnWriteArrayList<>();
        remainingActionTriggerCompositionList.addAll(composition.getActionTriggerList().stream().filter(trigger -> trigger.getPositionMillis() >= getPositionMillis()).toList());
    }

    private boolean findHardwareH265Decoder(Bin bin) {
        for (Element element : bin.getElements()) {
            ElementFactory factory = element.getFactory();
            if (factory != null) {
                String name = factory.getName();
                if ((name.contains("h265") || name.contains("hevc")) && !name.startsWith("avdec")) {
                    return true;
                }
            }
            if (element instanceof Bin && findHardwareH265Decoder((Bin) element)) {
                return true;
            }
        }
        return false;
    }

    // Load all files and construct the complete GST pipeline
    public void loadFiles() throws Exception {
        boolean hasActiveFile = false;
        firstPlayDone = false;
        hasH265Stream = false;

        if (playState != PlayState.STOPPED) {
            // A resume (PAUSED) returns here -> keep masterEosReached so a trailing-action tail survives
            return;
        }

        // Fresh load -> not in a trailing-action tail
        masterEosReached = false;

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
        // Re-entrant (also called on resume / each loop): drop any previously scheduled end first
        stopAutoStopTimer();

        if (pipeline != null && !masterEosReached) {
            // A live master pipeline drives the end via its EOS -> no need for the timer
            return;
        }

        // No master, or a master that already reached EOS (trailing-action tail) -> the wall clock
        // drives the end. Add a small buffer so the last action triggers fire before we loop/finish.
        autoStopHandle = scheduler.schedule(this::reachedEnd,
                composition.getDurationMillis() - getPositionMillis() + 50, MILLISECONDS);
    }

    private void stopAutoStopTimer() {
        if (autoStopHandle == null) {
            return;
        }

        // Don't interrupt the thread, because it might need to notify some websocket sessions
        autoStopHandle.cancel(false);
        autoStopHandle = null;
    }

    // The master pipeline reached its end (the longest non-looping source). If actions are placed
    // after it, run them out first on the wall clock (the "trailing-action tail") and only reach the
    // real end of the composition once the full duration has elapsed; otherwise the end is now.
    private void onMasterEndOfStream() {
        if (masterEosReached) {
            // Already in the tail (e.g. a duplicate EOS while resuming) -> nothing to do
            return;
        }

        long remainingMillis = composition.getDurationMillis() - getPositionMillis();

        if (remainingMillis <= 0 && composition.isLoop()) {
            // No trailing tail and looping -> loop straight away, so there is no gap between
            // iterations (an extra wait would be audible/visible).
            reachedEnd();
            return;
        }

        // Otherwise run out the wall-clock tail before reaching the end: any trailing actions
        // (remainingMillis), or just the small +50ms buffer so an action exactly at the end still
        // fires. Anchor wall-clock timing at the current position (the pipeline position is now
        // frozen at EOS) so it keeps advancing, survive pause/resume, and let the auto-stop timer
        // loop/finish once the duration fully elapses. The composition must NOT loop/finish here.
        if (remainingMillis > 0) {
            logger.info("Master pipeline reached EOS; waiting {}ms for trailing actions", remainingMillis);
        }
        startPosition = getPositionMillis();
        lastPlayTimeMillis = System.currentTimeMillis();
        masterEosReached = true;
        startAutoStopTimer();
    }

    // The composition reached its real end (full duration elapsed). Loop it back to the start if it
    // is set to loop, otherwise finish (media -> auto-next handling; pure actions -> just stop).
    private void reachedEnd() {
        if (composition.isLoop()) {
            loopToStart();
            return;
        }

        try {
            if (pipeline != null || !slavePipelineList.isEmpty()) {
                playerService.compositionPlayerFinishedPlaying(this);
            } else {
                stop();
            }
        } catch (Exception e) {
            logger.error("Could not finish the composition at its end", e);
        }
    }

    // Loop the whole composition back to the start (master if any + slaves + action triggers).
    private void loopToStart() {
        if (pipeline != null && hasH265Stream) {
            // H.265 master can't seek -> recreate it. Off the bus/timer thread to avoid deadlocking
            // pipeline disposal.
            scheduler.execute(this::recreateMasterAndRestart);
            return;
        }

        try {
            // seek(0) restarts the master (if any), re-phases the slaves and re-arms the actions.
            seek(0);
            // Re-arm the end timer for the next cycle. With a master this is a no-op (its EOS drives
            // the next loop); without one the wall-clock timer drives it.
            startAutoStopTimer();
        } catch (Exception e) {
            logger.error("Could not loop the composition back to the start", e);
        }
    }

    private void recreateMasterAndRestart() {
        try {
            stopPipeline();
            firstPlayDone = false;
            masterEosReached = false;
            createGstreamerPipeline();
            if (pipeline != null) {
                pipeline.setState(State.PAUSED);
                pipeline.getState(5, TimeUnit.SECONDS);
                hasH265Stream = findHardwareH265Decoder(pipeline);
                pipeline.setState(State.PLAYING);
                pipeline.getState(5, TimeUnit.SECONDS);
            }
            startSlaves(0);
            lastPlayTimeMillis = System.currentTimeMillis();
            calculateRemainingActionTriggerList();
        } catch (Exception e) {
            logger.error("Could not recreate the H.265 pipeline to loop the composition", e);
        }
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

    // Cancel any action triggers that are scheduled but haven't fired yet, so they don't fire after
    // the composition is paused or stopped (e.g. leaking into a following composition).
    private void cancelPendingActionTriggers() {
        for (ScheduledFuture<?> handle : actionTriggerHandleList) {
            handle.cancel(false);
        }
        actionTriggerHandleList.clear();
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
            if (masterEosReached) {
                // Resuming during the trailing-action tail: the master is exhausted (at EOS) and its
                // position is past the end, so don't re-preroll or seek it. Just resume playback so
                // the slaves/clock keep running while the wall-clock timers (re-armed below) finish.
                pipeline.setState(State.PLAYING);
                pipeline.getState(5, TimeUnit.SECONDS);
                resumeSlaves();
            } else {
                // A fresh start prerolls and phase-aligns the slaves; a resume just continues them
                boolean freshStart = !firstPlayDone;

                if (freshStart) {
                    pipeline.setState(State.PAUSED);
                    pipeline.getState(5, TimeUnit.SECONDS);

                    hasH265Stream = findHardwareH265Decoder(pipeline);
                    if (hasH265Stream) {
                        logger.warn("Hardware H.265 decoder detected in the master pipeline — seeking will be disabled for this composition");
                    }

                    if (startPosition > 0 && !hasH265Stream) {
                        pipeline.seekSimple(Format.TIME,
                                EnumSet.of(SeekFlags.FLUSH, SeekFlags.KEY_UNIT),
                                startPosition * 1_000_000L);
                        pipeline.getState(5, TimeUnit.SECONDS);
                    }
                }
                pipeline.setState(State.PLAYING);
                pipeline.getState(5, TimeUnit.SECONDS);

                // The master is now playing -> bring up the slaves on its clock
                if (freshStart) {
                    startSlaves(startPosition);
                } else {
                    resumeSlaves();
                }
            }
        }

        lastPlayTimeMillis = System.currentTimeMillis();

        calculateRemainingActionTriggerList();

        startAutoStopTimer();
        scheduleActionTriggerTimers();

        designerService.play();
        startMidiTimecode();

        if (pipeline == null) {
            // No master pipeline (every source loops, or only actions/designer) -> start any slaves
            // on the wall-clock timeline, then issue the playing state directly.
            startSlaves(startPosition);
            playState = PlayState.PLAYING;
            notificationService.notifyClients(playerService, setService);
        }
    }

    public void pause() throws Exception {
        if (playState == PlayState.PAUSED) {
            return;
        }

        logger.info("Pausing composition '" + composition.getName() + "'");

        if (hasAnyH265Stream()) {
            logger.warn("Pause ignored: hardware H.265 decoder does not support pausing/resuming");
            return;
        }

        // Pause the composition (master + all slaves)
        if (pipeline != null) {
            pipeline.pause();
        }
        for (SlavePipeline slavePipeline : slavePipelineList) {
            slavePipeline.pause();
        }

        designerService.pause();

        startPosition = getPositionMillis();

        playState = PlayState.PAUSED;

        stopAutoStopTimer();
        stopActionTriggerTimer();
        cancelPendingActionTriggers();
        midiTimecodeService.stop(this);

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService, setService);
        }
    }

    public void stop() throws Exception {
        startPosition = 0;
        firstPlayDone = false;
        hasH265Stream = false;
        masterEosReached = false;

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
        cancelPendingActionTriggers();
        midiTimecodeService.stop(this);

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
        if (hasH265Stream) {
            // Seeking is disabled for hardware H.265 decode on RPi. Two approaches were tried
            // and both fail:
            //   1. Early seek (before preroll): the hardware decoder ignores the seek event
            //      during pipeline initialisation and always prerolls at position 0.
            //   2. Post-preroll FLUSH seek: the decoder's sole DPB slot (MaxDpbPic = 1 for
            //      4K H.265 level 4.x) is held by the KMS scanout buffer. alloc_frame()
            //      cannot get a free slot and fails with "Not enough memory to decode H265
            //      stream". Unlike software decoders, the hardware path has no additional
            //      DPB slots to absorb the seek.
            logger.warn("Seek to {}ms ignored: hardware H.265 decoder does not support seeking", positionMillis);
            if (!isSample) {
                notificationService.notifyClients(playerService, setService);
            }
            return;
        }

        startPosition = positionMillis;

        // A (flushing) seek re-activates the pipeline, so we leave any trailing-action tail and let
        // the pipeline drive position and the end of stream again.
        masterEosReached = false;

        logger.debug("Seek to position " + positionMillis);

        if (pipeline != null) {
            long positionNanos = positionMillis * 1_000_000L;

            // Software decoders have sufficient decode buffers to handle a direct FLUSH seek
            // on the live pipeline without any state teardown. The pipeline automatically
            // returns to its previous state (PLAYING or PAUSED) after the seek.
            pipeline.seekSimple(Format.TIME,
                    EnumSet.of(SeekFlags.FLUSH, SeekFlags.KEY_UNIT),
                    positionNanos);
            pipeline.getState(5, TimeUnit.SECONDS);
        }

        // Re-phase each slave to the in-loop position matching the new position (e.g. seek to 35s
        // with a 10s loop -> the slave jumps to 5s; H.265 slaves restart). Works with or without a
        // master pipeline.
        rephaseSlaves(positionMillis);

        this.lastPlayTimeMillis = System.currentTimeMillis();

        calculateRemainingActionTriggerList();
        scheduleActionTriggerTimers();
        if (playState == PlayState.PLAYING) {
            startMidiTimecode();
        }

        if (!isSample) {
            notificationService.notifyClients(playerService, setService);
        }
    }

    private void startMidiTimecode() {
        if (isDefaultComposition || isSample) {
            return;
        }

        midiTimecodeService.start(this, this::getPositionMillis);
    }

    public long getPositionMillis() {
        if (composition == null) {
            return 0;
        }

        // If we're not playing, just return the current start position to resume playing
        if (playState != PlayState.PLAYING) {
            return startPosition;
        }

        // If there is a live pipeline -> use it (only if playing, querying its status is not reliably
        // otherwise). After the master reached EOS its position is frozen at the longest source, so
        // fall through to the wall clock to keep advancing through the trailing-action tail.
        if (pipeline != null && !masterEosReached) {
            long queriedMillis = pipeline.queryPosition(TimeUnit.MILLISECONDS);
            if (queriedMillis >= startPosition) {
                // only return, if the query worked (shortly after seeking it's 0, even if we're playing)
                return queriedMillis;
            }
        }

        // No (usable) pipeline position (only actions, or the trailing-action tail) -> wall clock
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

    /**
     * A slave pipeline plays a single looping source that is shorter than the longest source.
     * It runs independently (looping on its own EOS) but shares the master's clock and base time
     * so it does not drift. The master applies its play/pause/stop/seek state to all slaves.
     */
    private class SlavePipeline {

        private final CompositionFile compositionFile;
        private final int index;
        private Pipeline slavePipeline;
        private final List<Element> volumeList = new ArrayList<>();
        private boolean hasH265 = false;

        SlavePipeline(CompositionFile compositionFile, int index) {
            this.compositionFile = compositionFile;
            this.index = index;
        }

        // Construct the single-source pipeline with its own output (sink). Does not start it.
        void build() throws Exception {
            slavePipeline = new Pipeline("slave" + index);

            Bus bus = GstApi.GST_API.gst_element_get_bus(slavePipeline);
            bus.connect((Bus.ERROR) (GstObject source, int code, String message) ->
                    logger.error("GST slave error (" + compositionFile.getName() + "): " + message));
            // When the slave reaches the end of its source, loop it back to the start
            bus.connect((Bus.EOS) source -> loop());
            GstApi.GST_API.gst_object_unref(bus);

            // Audio slaves need their own output chain (mixer -> ... -> sink) to their device
            Map<AudioDevice, Element> audioMixerList = new HashMap<>();
            if (compositionFile instanceof AudioCompositionFile) {
                AudioDevice audioDevice = getAudioDeviceList(List.of(compositionFile)).get(0);
                Element mixer = buildAudioOutputChain(slavePipeline, audioDevice, false, false, volumeList);
                audioMixerList.put(audioDevice, mixer);
            }

            // hasVideo is true here: video slaves are only built when an HDMI device is connected
            addCompositionFileToPipeline(compositionFile, slavePipeline, audioMixerList, index, true);
        }

        // Preroll, lock onto the master clock, seek to the matching in-loop position, then play.
        void startSyncedToMaster(long masterPositionMillis) {
            if (slavePipeline == null) {
                return;
            }

            slavePipeline.setState(State.PAUSED);
            slavePipeline.getState(5, TimeUnit.SECONDS);

            hasH265 = findHardwareH265Decoder(slavePipeline);

            // Share the master clock so the slave never rate-drifts. We deliberately do NOT share
            // the base time: the slave runs its own (looping) timeline at a different phase, and a
            // shared base time would make every buffer arrive late and be dropped to "catch up".
            Clock masterClock = pipeline == null ? null : pipeline.getClock();
            if (masterClock != null) {
                slavePipeline.useClock(masterClock);
            }

            // Phase-align the loop to the master's position, then let the shared clock hold it.
            long phaseMillis = phasePositionMillis(masterPositionMillis);
            if (phaseMillis > 0 && !hasH265) {
                seekSlaveTo(phaseMillis);
                slavePipeline.getState(5, TimeUnit.SECONDS);
            }

            slavePipeline.setState(State.PLAYING);
            slavePipeline.getState(5, TimeUnit.SECONDS);
        }

        // The in-loop content position (ms) the slave should be at for the given master position,
        // i.e. where it would be if it had looped continuously since the composition start. E.g.
        // master at 35s with a 10s loop -> 5s. Offset shifts the loop's start on the timeline.
        private long phasePositionMillis(long masterPositionMillis) {
            long length = compositionFile.getDurationMillis();
            if (length <= 0) {
                return 0;
            }
            long position = masterPositionMillis - compositionFile.getOffsetMillis();
            if (position <= 0) {
                return 0;
            }
            return Math.floorMod(position, length);
        }

        private void seekSlaveTo(long positionMillis) {
            if (slavePipeline != null) {
                slavePipeline.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.KEY_UNIT), positionMillis * 1_000_000L);
            }
        }

        // The slave reached its own end -> loop straight back to the start (it stays phase-aligned
        // because it has been playing at the shared clock rate).
        void loop() {
            if (slavePipeline == null) {
                return;
            }
            if (hasH265) {
                // H.265 can't seek on the Pi -> recreate. Must run off the bus-callback thread to
                // avoid deadlocking pipeline disposal.
                scheduler.execute(() -> recreate(0));
            } else {
                seekSlaveTo(0);
            }
        }

        // The master was seeked/looped -> jump to the in-loop position matching its new position.
        void rephaseTo(long masterPositionMillis) {
            if (slavePipeline == null) {
                return;
            }
            if (hasH265) {
                // H.265 can't seek -> recreate (it can only restart from the beginning).
                scheduler.execute(() -> recreate(masterPositionMillis));
            } else {
                seekSlaveTo(phasePositionMillis(masterPositionMillis));
            }
        }

        private void recreate(long masterPositionMillis) {
            try {
                stop();
                build();
                startSyncedToMaster(masterPositionMillis);
            } catch (Exception e) {
                logger.error("Could not recreate slave pipeline for " + compositionFile.getName(), e);
            }
        }

        void pause() {
            if (slavePipeline != null) {
                slavePipeline.pause();
            }
        }

        // Resume from pause without re-phasing: the shared clock preserved alignment while paused.
        void resume() {
            if (slavePipeline != null) {
                slavePipeline.setState(State.PLAYING);
            }
        }

        // Mute the outputs (avoids audio artifacts before a coordinated teardown)
        void mute() {
            for (Element volume : volumeList) {
                volume.set("volume", 0.0);
            }
        }

        void stop() {
            if (slavePipeline != null) {
                slavePipeline.stop();
                slavePipeline.dispose();
                slavePipeline = null;
            }
            volumeList.clear();
        }

        boolean hasH265() {
            return hasH265;
        }
    }

}
