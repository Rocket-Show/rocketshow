package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.audio.ActivityNotificationAudioService;
import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.AudioService;
import com.ascargon.rocketshow.composition.ActionTriggerComposition;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.gstreamer.GstApi;
import com.ascargon.rocketshow.midi.*;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.SettingsService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Constructs the GStreamer pipeline (master pipeline plus per-source slave pipelines) for a
 * composition. All knowledge about how to turn composition files into linked GStreamer elements
 * lives here, so the {@link CompositionPlayer} only has to drive playback (play/pause/stop/seek).
 */
@Service
public class CompositionPipelineBuilder {

    private final static Logger logger = LoggerFactory.getLogger(CompositionPipelineBuilder.class);

    private final SettingsService settingsService;
    private final CapabilitiesService capabilitiesService;
    private final ActivityNotificationAudioService activityNotificationAudioService;
    private final ActivityNotificationMidiService activityNotificationMidiService;
    private final OperatingSystemInformationService operatingSystemInformationService;
    private final AudioService audioService;
    private final MidiRouterFactory midiRouterFactory;
    private final HdmiService hdmiService;

    public CompositionPipelineBuilder(
            SettingsService settingsService,
            CapabilitiesService capabilitiesService,
            ActivityNotificationAudioService activityNotificationAudioService,
            ActivityNotificationMidiService activityNotificationMidiService,
            OperatingSystemInformationService operatingSystemInformationService,
            AudioService audioService,
            MidiRouterFactory midiRouterFactory,
            HdmiService hdmiService
    ) {
        this.settingsService = settingsService;
        this.capabilitiesService = capabilitiesService;
        this.activityNotificationAudioService = activityNotificationAudioService;
        this.activityNotificationMidiService = activityNotificationMidiService;
        this.operatingSystemInformationService = operatingSystemInformationService;
        this.audioService = audioService;
        this.midiRouterFactory = midiRouterFactory;
        this.hdmiService = hdmiService;
    }

    /**
     * Receives the master pipeline's bus events. Implemented by the {@link CompositionPlayer} so the
     * builder stays free of playback state.
     */
    public interface MasterEventListener {

        // A fatal GStreamer error occurred (the player should notify and stop)
        void onError(String message);

        // The master pipeline reached the PLAYING state
        void onPlaying();

        // The master pipeline reached its end of stream
        void onEndOfStream();
    }

    // Build the complete pipeline for the given composition. The returned CompositionPipeline holds
    // the master pipeline (may be absent when every source loops) plus all slave pipelines.
    public CompositionPipeline build(Composition composition, boolean isSample, MasterEventListener listener, Executor recreateExecutor) throws Exception {
        boolean hasVideo = hasVideo(composition);

        Pipeline masterPipeline = new Pipeline();
        Bus bus = GstApi.GST_API.gst_element_get_bus(masterPipeline);

        bus.connect((Bus.ERROR) (GstObject source, int code, String message) -> {
            logger.error("GST error: " + message);
            listener.onError(message);
        });
        bus.connect((Bus.WARNING) (GstObject source, int code, String message) -> logger.warn("GST: " + message));
        bus.connect((Bus.INFO) (GstObject source, int code, String message) -> logger.warn("GST: " + message));
        bus.connect((GstObject source, State old, State newState, State pending) -> {
            if (source.getTypeName().equals("GstPipeline") && newState == State.PLAYING) {
                listener.onPlaying();
            }
        });
        bus.connect((Bus.EOS) source -> listener.onEndOfStream());
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
            masterPipeline.dispose();
            masterPipeline = null;
        }

        // Per-composition MIDI construction context, kept on the CompositionPipeline so that
        // looping MIDI slaves can be (re)built with the same parser/mapping and so the routers can
        // be closed when the pipeline is torn down.
        MidiMapping midiMapping = new MidiMapping();
        midiMapping.setParent(settingsService.getSettings().getMidiMapping());
        MidiMessageParser midiMessageParser = new MidiMessageParser();

        CompositionPipeline compositionPipeline = new CompositionPipeline(this, composition, masterPipeline, recreateExecutor, midiMapping, midiMessageParser);

        if (masterPipeline != null) {
            // Prepare the audio devices used by the master files and keep the mixers to link into.
            // Use audio to provide a clock, if there's no video. Otherwise, let video provide the
            // clock (more stable, avoids jittering issues).
            Map<AudioDevice, Element> audioMixerList = prepareAudioDevices(compositionPipeline, masterFileList, !hasVideo, isSample);

            // Add all master files to the master pipeline
            for (CompositionFile compositionFile : masterFileList) {
                int index = composition.getCompositionFileList().indexOf(compositionFile);
                addCompositionFileToPipeline(compositionFile, masterPipeline, audioMixerList, index, hasVideo, compositionPipeline);
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
            compositionPipeline.addSlave(compositionFile, index);
        }

        return compositionPipeline;
    }

    private void processMidiBuffer(ByteBuffer byteBuffer, MidiRouter midiRouter, MidiMessageParser midiMessageParser) {
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

    private void addMidiToPipeline(MidiCompositionFile midiCompositionFile, Pipeline target, int index, CompositionPipeline ctx) {
        MidiRouter midiRouter = midiRouterFactory.getMidiRouter(midiCompositionFile.getMidiRoutingList());

        ctx.getMidiRouterList().add(midiRouter);

        for (MidiRouting midiRouting : midiCompositionFile.getMidiRoutingList()) {
            midiRouting.getMidiMapping().setParent(ctx.getMidiMapping());
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

        MidiMessageParser midiMessageParser = ctx.getMidiMessageParser();

        // Sometimes preroll and sometimes new-sample events get fired. We have
        // to process both.
        midiSink.connect((AppSink.NEW_SAMPLE) element -> {
            Sample sample = element.pullSample();
            Buffer buffer = sample.getBuffer();
            processMidiBuffer(buffer.map(false), midiRouter, midiMessageParser);
            buffer.unmap();
            sample.dispose();
            return FlowReturn.OK;
        });
        midiSink.connect((AppSink.NEW_PREROLL) element -> {
            Sample sample = element.pullPreroll();
            Buffer buffer = sample.getBuffer();
            processMidiBuffer(buffer.map(false), midiRouter, midiMessageParser);
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

    private void addAudioToPipeline(AudioCompositionFile audioCompositionFile, Pipeline target, Map<AudioDevice, Element> audioMixerList, int index, CompositionPipeline ctx) throws Exception {
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

                float channelVolume = getChannelVolume(audioBus, i, j, getCombinedVolume(audioCompositionFile.getVolume(), ctx.getComposition().getAudioVolume()));

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
    List<AudioDevice> getAudioDeviceList(List<CompositionFile> compositionFileList) {
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
    Element buildAudioOutputChain(Pipeline target, AudioDevice audioDevice, boolean provideClock, boolean enableLevel, List<Element> volumeListTarget) {
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
    private Map<AudioDevice, Element> prepareAudioDevices(CompositionPipeline ctx, List<CompositionFile> compositionFileList, boolean provideClock, boolean isSample) {
        Map<AudioDevice, Element> audioMixerList = new HashMap<>();

        // Only use the first audio device as clock master (if audio should provide a clock at all)
        boolean provideClockFirstAudioDevice = provideClock;

        // Level monitoring is only added on the master pipeline (drives the audio monitor)
        boolean enableLevel = !isSample && settingsService.getSettings().getEnableMonitor();

        for (AudioDevice audioDevice : getAudioDeviceList(compositionFileList)) {
            Element audioMixer = buildAudioOutputChain(ctx.getMasterPipeline(), audioDevice, provideClockFirstAudioDevice, enableLevel, ctx.getVolumeList());
            audioMixerList.put(audioDevice, audioMixer);
            provideClockFirstAudioDevice = false;
        }

        return audioMixerList;
    }

    private boolean hasVideo(Composition composition) {
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

    // Add a single composition file (midi/audio/video) to the given pipeline, applying the same
    // capability/HDMI guards used for the master pipeline. For audio, the matching mixer must
    // already be present in audioMixerList.
    void addCompositionFileToPipeline(CompositionFile compositionFile, Pipeline target, Map<AudioDevice, Element> audioMixerList, int index, boolean hasVideo, CompositionPipeline ctx) throws Exception {
        if (compositionFile instanceof MidiCompositionFile) {
            addMidiToPipeline((MidiCompositionFile) compositionFile, target, index, ctx);
        } else if (compositionFile instanceof AudioCompositionFile && capabilitiesService.getCapabilities().isGstreamer()) {
            addAudioToPipeline((AudioCompositionFile) compositionFile, target, audioMixerList, index, ctx);
        } else if (compositionFile instanceof VideoCompositionFile && capabilitiesService.getCapabilities().isGstreamer() && hasVideo) {
            addVideoToPipeline((VideoCompositionFile) compositionFile, target, index);
        }
    }

}
