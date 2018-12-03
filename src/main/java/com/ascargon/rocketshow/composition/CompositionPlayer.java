package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.CapabilitiesService;
import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.audio.AudioCompositionFilePlayer;
import com.ascargon.rocketshow.midi.*;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Handle the playing of a single composition.
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
    private final PlayerService playerService;
    private final SettingsService settingsService;
    private final MidiRoutingService midiRoutingService;
    private final CapabilitiesService capabilitiesService;
    private final OperatingSystemInformationService operatingSystemInformationService;

    private Composition composition;
    private PlayState playState = PlayState.STOPPED;
    private Timer autoStopTimer;
    private long startPosition = 0;

    private final List<AudioCompositionFilePlayer> audioCompositionFilePlayerList = new ArrayList<>();
    private final List<MidiCompositionFilePlayer> midiCompositionFilePlayerList = new ArrayList<>();

    private final MidiMapping midiMapping = new MidiMapping();

    // Is this the default composition?
    private boolean isDefaultComposition = false;

    // Is this composition played as a sample?
    private boolean isSample = false;

    // The gstreamer pipeline, used to sync all files in this composition
    private Pipeline pipeline;

    public CompositionPlayer(NotificationService notificationService, PlayerService playerService, SettingsService settingsService, MidiRoutingService midiRoutingService, CapabilitiesService capabilitiesService, OperatingSystemInformationService operatingSystemInformationService) {
        this.notificationService = notificationService;
        this.playerService = playerService;
        this.settingsService = settingsService;
        this.midiRoutingService = midiRoutingService;
        this.capabilitiesService = capabilitiesService;
        this.operatingSystemInformationService = operatingSystemInformationService;

        this.midiMapping.setParent(settingsService.getSettings().getMidiMapping());
    }

    // Create a new pipeline, if there is at least one audio- or video file in this composition
    private boolean compositionNeedsPipeline(Composition composition) {
        if (!capabilitiesService.getCapabilities().isGstreamer()) {
            return false;
        }

        if (isSample) {
            return false;
        }

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive() && (compositionFile instanceof AudioCompositionFile || compositionFile instanceof VideoCompositionFile)) {
                return true;
            }
        }

        return false;
    }

    // Load all files and construct the complete GST pipeline
    public void loadFiles() throws Exception {
        MidiPlayer firstMidiPlayer = null;

        if (playState != PlayState.STOPPED) {
            return;
        }

        playState = PlayState.LOADING;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService);
        }

        logger.debug(
                "Loading composition '" + composition.getName() + "...");

        if (compositionNeedsPipeline(composition)) {
            logger.trace(
                    "Pipeline required");

            if (pipeline != null) {
                pipeline.stop();
            }

            pipeline = new Pipeline();

            pipeline.getBus().connect((Bus.ERROR) (GstObject source, int code, String message) -> logger.error("GST: " + message));
            pipeline.getBus().connect((Bus.WARNING) (GstObject source, int code, String message) -> logger.warn("GST: " + message));
            pipeline.getBus().connect((Bus.INFO) (GstObject source, int code, String message) -> logger.warn("GST: " + message));
            pipeline.getBus().connect((GstObject source, State old, State newState, State pending) -> {
                if (source.getTypeName().equals("GstPipeline") && newState == State.PLAYING) {
                    // We changed to playing, maybe we need to seek to the start position (not possible before playing)
                    if (startPosition > 0) {
                        try {
                            seek(startPosition);
                        } catch (Exception e) {
                            logger.error("Could not set start position when changed to playing", e);
                        }
                        startPosition = 0;
                    }
                }
            });
        }

        // Load all files, create the pipeline and handle exceptions to pipeline-playing
        for (int i = 0; i < composition.getCompositionFileList().size(); i++) {
            CompositionFile compositionFile = composition.getCompositionFileList().get(i);

            if (compositionFile.isActive()) {
                if (compositionFile instanceof MidiCompositionFile) {
                    MidiCompositionFilePlayer midiCompositionFilePlayer = new MidiCompositionFilePlayer(midiRoutingService, (MidiCompositionFile) compositionFile, settingsService.getSettings().getBasePath() + "/" + settingsService.getSettings().getMediaPath() + "/" + settingsService.getSettings().getMidiPath() + "/" + compositionFile.getName(), pipeline, firstMidiPlayer);
                    midiCompositionFilePlayerList.add(midiCompositionFilePlayer);

                    MidiCompositionFile midiFile = (MidiCompositionFile) compositionFile;

                    for (MidiRouting midiRouting : midiFile.getMidiRoutingList()) {
                        midiRouting.getMidiMapping().setParent(midiMapping);
                    }

                    if (firstMidiPlayer == null) {
                        firstMidiPlayer = midiCompositionFilePlayer.getMidiPlayer();
                    }
                } else if (compositionFile instanceof AudioCompositionFile && capabilitiesService.getCapabilities().isGstreamer()) {
                    AudioCompositionFilePlayer audioCompositionFilePlayer = new AudioCompositionFilePlayer(settingsService, (AudioCompositionFile) compositionFile, settingsService.getSettings().getBasePath() + "/" + settingsService.getSettings().getMediaPath() + "/" + settingsService.getSettings().getAudioPath() + "/" + compositionFile.getName(), isSample);
                    audioCompositionFilePlayerList.add(audioCompositionFilePlayer);

                    // Samples are played with the AlsaPlayer
                    if (!isSample) {
                        logger.debug("Add audio file to pipeline");

                        URIDecodeBin audioSource = (URIDecodeBin) ElementFactory.make("uridecodebin", "uridecodebin" + i);
                        audioSource.set("uri", "file://" + settingsService.getSettings().getBasePath() + "/" + settingsService.getSettings().getMediaPath() + "/" + settingsService.getSettings().getAudioPath() + "/" + compositionFile.getName());
                        pipeline.add(audioSource);

                        Element convert = ElementFactory.make("audioconvert", "audioconvert" + i);
                        audioSource.connect((Element.PAD_ADDED) (Element element, Pad pad) -> {
                            String name = pad.getCaps().getStructure(0).getName();

                            if ("audio/x-raw-float".equals(name) || "audio/x-raw-int".equals(name) || "audio/x-raw".equals(name)) {
                                pad.link(convert.getSinkPads().get(0));
                            }
                        });
                        pipeline.add(convert);

                        Element resample = ElementFactory.make("audioresample", "audioresample" + i);
                        pipeline.add(resample);

                        String sinkName = "alsasink";

                        if (OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
                            sinkName = "osxaudiosink";
                        }
                        BaseSink sink = (BaseSink) ElementFactory.make(sinkName, "sink" + i);

                        if (!OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
                            sink.set("device", settingsService.getAlsaDeviceFromOutputBus(((AudioCompositionFile) compositionFile).getOutputBus()));
                        }
                        pipeline.add(sink);

                        convert.link(resample);
                        resample.link(sink);
                    }
                } else if (compositionFile instanceof VideoCompositionFile && capabilitiesService.getCapabilities().isGstreamer()) {
                    logger.debug("Add video file to pipeline");

                    // TODO Does not work on OS X

                    PlayBin playBin = (PlayBin) ElementFactory.make("playbin", "playbin" + i);
                    playBin.set("uri", "file://" + settingsService.getSettings().getBasePath() + "/" + settingsService.getSettings().getMediaPath() + "/" + settingsService.getSettings().getVideoPath() + "/" + compositionFile.getName());
                    pipeline.add(playBin);
                }
            }
        }

        logger.debug("Composition '" + composition.getName() + "' loaded");

        // Maybe we are stopping meanwhile
        if (playState == PlayState.LOADING && !isDefaultComposition && !isSample) {
            playState = PlayState.LOADED;
            notificationService.notifyClients(playerService);
        }
    }

    private void startAutoStopTimer() {
        // Start the autostop timer, to automatically stop the composition, as
        // soon as the last file (the longest one, which has the most offset)
        // has been finished)
        long maxDurationAndOffset = 0;
        long positionMillis = getPositionMillis();

        if (autoStopTimer != null) {
            autoStopTimer.cancel();
            autoStopTimer = null;
        }

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive()) {
                int fileOffset = compositionFile.getOffsetMillis();

                if (compositionFile.isLoop()) {
                    // At least one file is looped -> don't stop the composition
                    // automatically
                    return;
                }

                if (compositionFile instanceof MidiCompositionFile) {
                    fileOffset += settingsService.getSettings().getOffsetMillisMidi();
                } else if (compositionFile instanceof AudioCompositionFile) {
                    fileOffset += settingsService.getSettings().getOffsetMillisAudio();
                } else if (compositionFile instanceof VideoCompositionFile) {
                    fileOffset += settingsService.getSettings().getOffsetMillisVideo();
                }

                if (compositionFile.getDurationMillis() + fileOffset > maxDurationAndOffset) {
                    maxDurationAndOffset = compositionFile.getDurationMillis() + fileOffset;
                }
            }
        }

        maxDurationAndOffset -= positionMillis;

        logger.debug("Scheduled the auto-stop timer in " + maxDurationAndOffset + " millis");

        CompositionPlayer thisCompositionPlayer = this;

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    logger.debug("Automatically stopping the composition...");

                    timer.cancel();
                    autoStopTimer = null;

                    playerService.compositionPlayerFinishedPlaying(thisCompositionPlayer);
                } catch (Exception e) {
                    logger.error("Could not automatically stop composition '" + composition.getName() + "'", e);
                }
            }
        }, maxDurationAndOffset);

        autoStopTimer = timer;
    }

    public void play() throws Exception {
        if (composition == null) {
            return;
        }

        // Load the files, if not already done by a previously by a separate call
        loadFiles();

        // All files are loaded -> play the composition (start each file)
        logger.info("Playing composition '" + composition.getName() + "'...");

        if (pipeline != null) {
            pipeline.play();
        }

        for (AudioCompositionFilePlayer audioCompositionFilePlayer : audioCompositionFilePlayerList) {
            audioCompositionFilePlayer.play();
        }

        for (MidiCompositionFilePlayer midiCompositionFilePlayer : midiCompositionFilePlayerList) {
            midiCompositionFilePlayer.play();
        }

        startAutoStopTimer();

        playState = PlayState.PLAYING;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService);
        }
    }

    public void pause() throws Exception {
        // Cancel the auto-stop timer
        if (autoStopTimer != null) {
            autoStopTimer.cancel();
            autoStopTimer = null;
        }

        if (playState == PlayState.PAUSED) {
            return;
        }

        logger.info("Pausing composition '" + composition.getName() + "'");

        // Pause the composition
        if (pipeline != null) {
            pipeline.pause();
        }

        for (MidiCompositionFilePlayer midiCompositionFilePlayer : midiCompositionFilePlayerList) {
            midiCompositionFilePlayer.pause();
        }

        playState = PlayState.PAUSED;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService);
        }
    }

    public void togglePlay() throws Exception {
        if (playState == PlayState.PLAYING) {
            stop();
        } else {
            play();
        }
    }

    public void stop() throws Exception {
        if (composition == null) {
            return;
        }

        playState = PlayState.STOPPING;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService);
        }

        startPosition = 0;

        // Cancel the auto-stop timer
        if (autoStopTimer != null) {
            autoStopTimer.cancel();
            autoStopTimer = null;
        }

        logger.info("Stopping composition '" + composition.getName() + "'");

        // Stop the composition
        if (pipeline != null) {
            pipeline.stop();
            pipeline = null;
        }

        for (AudioCompositionFilePlayer audioCompositionFilePlayer : audioCompositionFilePlayerList) {
            audioCompositionFilePlayer.stop();
        }

        for (MidiCompositionFilePlayer midiCompositionFilePlayer : midiCompositionFilePlayerList) {
            midiCompositionFilePlayer.stop();
        }

        playState = PlayState.STOPPED;

        if (!isSample && !isDefaultComposition) {
            notificationService.notifyClients(playerService);
        }

        logger.info("Composition '" + composition.getName() + "' stopped");
    }

    public void seek(long positionMillis) throws Exception {
        // When we seek before pressing play
        startPosition = positionMillis;

        logger.debug("Seek to position " + positionMillis);

        if (pipeline != null) {
            pipeline.seek(positionMillis, TimeUnit.MILLISECONDS);
        }

        for (MidiCompositionFilePlayer midiCompositionFilePlayer : midiCompositionFilePlayerList) {
            midiCompositionFilePlayer.seek(positionMillis);
        }

        startAutoStopTimer();

        if (!isSample) {
            notificationService.notifyClients(playerService);
        }
    }

    public long getPositionMillis() {
        if (startPosition > 0) {
            return startPosition;
        }

        if (composition == null) {
            return 0;
        }

        if (pipeline != null) {
            return pipeline.queryPosition(TimeUnit.MILLISECONDS);
        }

        for (MidiCompositionFilePlayer midiCompositionFilePlayer : midiCompositionFilePlayerList) {
            midiCompositionFilePlayer.getPositionMillis();
        }

        return 0;
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
            notificationService.notifyClients(playerService);
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
