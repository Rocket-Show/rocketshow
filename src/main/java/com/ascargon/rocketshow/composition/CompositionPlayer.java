package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiPlayer;
import com.ascargon.rocketshow.midi.MidiRouting;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.apache.log4j.Logger;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.elements.URIDecodeBin;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handle the playing of a single composition.
 */
public class CompositionPlayer {

    private final static Logger logger = Logger.getLogger(CompositionPlayer.class);

    private final String uuid = String.valueOf(UUID.randomUUID());

    public enum PlayState {
        PLAYING, // Is the composition playing?
        PAUSED, // Is the composition paused?
        STOPPING, // Is the composition being stopped?
        STOPPED, // Is the composition stopped?
        LOADING, // Is the composition waiting for all files to be loaded to start playing?
        LOADED // Has the composition finished loading all files?
    }

    private NotificationService notificationService;
    private PlayerService playerService;

    private Composition composition;
    private PlayState playState;
    private Timer autoStopTimer;
    private long startPosition = 0;

    // TODO Isn't this variable already covered by the playstate?
    private boolean filesLoaded;

    private MidiMapping midiMapping = new MidiMapping();

    // Is this the default composition?
    private boolean isDefaultComposition = false;

    // Is this composition played as a sample?
    private boolean isSample = false;

    // The gstreamer pipeline, used to sync all files in this composition
    private Pipeline pipeline;

    public CompositionPlayer(NotificationService notificationService, PlayerService playerService) {
        this.notificationService = notificationService;
        this.playerService = playerService;
    }

    // Create a new pipeline, if there is at least one audio- or video file in this composition
    private boolean compositionNeedsPipeline(Composition composition) {
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

        if (filesLoaded) {
            return;
        }

        playState = PlayState.LOADING;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients();
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

        if (!isDefaultComposition) {
            playerService.stopDefaultComposition();
        }

        // Load all files, create the pipeline and handle exceptions to pipeline-playing
        for (int i = 0; i < composition.getCompositionFileList().size(); i++) {
            CompositionFile compositionFile = composition.getCompositionFileList().get(i);

            if (compositionFile.isActive()) {
                if (compositionFile instanceof MidiCompositionFile) {
                    MidiCompositionFile midiFile = (MidiCompositionFile) compositionFile;

                    for (MidiRouting midiRouting : midiFile.getMidiRoutingList()) {
                        midiRouting.getMidiMapping().setParent(midiMapping);
                    }

                    midiFile.load(pipeline, firstMidiPlayer);

                    if (firstMidiPlayer == null) {
                        firstMidiPlayer = midiFile.getMidiPlayer();
                    }
                } else if (compositionFile instanceof AudioCompositionFile) {
                    AudioCompositionFile audioFile = (AudioCompositionFile) compositionFile;
                    audioFile.load(isSample);

                    // Samples are played with the AlsaPlayer
                    if (!isSample) {
                        URIDecodeBin audioSource = (URIDecodeBin) ElementFactory.make("uridecodebin", "uridecodebin" + i);
                        audioSource.set("uri", "file://" + ((AudioCompositionFile) compositionFile).getPath());
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

                        BaseSink alsaSink = (BaseSink) ElementFactory.make("alsasink", "alsasink" + i);
                        //alsaSink.set("device", this.getManager().getSettings().getAlsaDeviceFromOutputBus(audioFile.getOutputBus()));
                        alsaSink.set("device", "bus1");
                        pipeline.add(alsaSink);

                        convert.link(resample);
                        resample.link(alsaSink);
                    }
                } else if (compositionFile instanceof VideoCompositionFile) {
                    PlayBin playBin = (PlayBin) ElementFactory.make("playbin", "playbin" + i);
                    playBin.set("uri", "file://" + ((VideoCompositionFile) compositionFile).getPath());
                    pipeline.add(playBin);
                }
            }
        }

        logger.debug("Composition '" + composition.getName() + "' loaded");

        // Maybe we are stopping meanwhile
        if (playState == PlayState.LOADING && !isDefaultComposition && !isSample) {
            playState = PlayState.LOADED;
            filesLoaded = true;

            notificationService.notifyClients();
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
                int fileOffset = 0;

                if (compositionFile.isLoop()) {
                    // At least one file is looped -> don't stop the composition
                    // automatically
                    return;
                }

                if (compositionFile instanceof MidiCompositionFile) {
                    fileOffset = ((MidiCompositionFile) compositionFile).getFullOffsetMillis();
                } else if (compositionFile instanceof AudioCompositionFile) {
                    fileOffset = ((AudioCompositionFile) compositionFile).getFullOffsetMillis();
                } else if (compositionFile instanceof VideoCompositionFile) {
                    fileOffset = ((VideoCompositionFile) compositionFile).getFullOffsetMillis();
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
        if(composition == null) {
            return;
        }

        // Load the files, if not already done by a previously by a separate call
        loadFiles();

        // All files are loaded -> play the composition (start each file)
        logger.info("Playing composition '" + composition.getName() + "'...");

        if (pipeline != null) {
            pipeline.play();
        }

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive() && compositionFile instanceof MidiCompositionFile) {
                ((MidiCompositionFile) compositionFile).play();
            } else if (compositionFile instanceof AudioCompositionFile) {
                ((AudioCompositionFile) compositionFile).play();
            }
        }

        startAutoStopTimer();

        playState = PlayState.PLAYING;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients();
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

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive() && compositionFile instanceof MidiCompositionFile) {
                ((MidiCompositionFile) compositionFile).pause();
            }
        }

        playState = PlayState.PAUSED;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients();
        }
    }

    public void togglePlay() throws Exception {
        if (playState == PlayState.PLAYING) {
            stop();
        } else {
            play();
        }
    }

    public void stop(boolean playDefaultComposition) throws Exception {
        if(composition == null) {
            return;
        }

        playState = PlayState.STOPPING;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients();
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

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive() && compositionFile instanceof MidiCompositionFile) {
                try {
                    ((MidiCompositionFile) compositionFile).close();
                } catch (Exception e) {
                    logger.error("Could not stop file '" + compositionFile.getName() + "'");
                }
            } else if (compositionFile instanceof AudioCompositionFile) {
                ((AudioCompositionFile) compositionFile).close();
            }
        }

        filesLoaded = false;

        logger.info("Composition '" + composition.getName() + "' stopped");

        playState = PlayState.STOPPED;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients();
        }

        // Play the default composition, if necessary
        if (!isDefaultComposition && playDefaultComposition && !isSample) {
            playerService.playDefaultComposition();
        }
    }

    public void seek(long positionMillis) throws Exception {
        // When we seek before pressing play
        startPosition = positionMillis;

        logger.debug("Seek to position " + positionMillis);

        if (pipeline != null) {
            pipeline.seek(positionMillis, TimeUnit.MILLISECONDS);
        }

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive() && compositionFile instanceof MidiCompositionFile) {
                ((MidiCompositionFile) compositionFile).seek(positionMillis);
            }
        }

        startAutoStopTimer();

        if (!isSample) {
            notificationService.notifyClients();
        }
    }

    public void stop() throws Exception {
        stop(true);
    }

    public void close() throws Exception {
        if (pipeline != null) {
            pipeline.stop();
        }

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive() && compositionFile instanceof MidiCompositionFile) {
                ((MidiCompositionFile) compositionFile).close();
            } else if (compositionFile.isActive() && compositionFile instanceof AudioCompositionFile) {
                ((AudioCompositionFile) compositionFile).close();
            }
        }

        filesLoaded = false;

        // Cancel the auto-stop timer
        if (autoStopTimer != null) {
            autoStopTimer.cancel();
            autoStopTimer = null;
        }

        playState = PlayState.STOPPED;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients();
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

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive() && compositionFile instanceof MidiCompositionFile) {
                return ((MidiCompositionFile) compositionFile).getPositionMillis();
            }
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

    public void setComposition(Composition composition) {
        this.composition = composition;
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
