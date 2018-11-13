package com.ascargon.rocketshow.composition;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.ascargon.rocketshow.midi.MidiPlayer;
import org.apache.log4j.Logger;
import org.freedesktop.gstreamer.*;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.audio.AudioFile;
import com.ascargon.rocketshow.midi.MidiFile;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiRouting;
import com.ascargon.rocketshow.video.VideoFile;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.elements.URIDecodeBin;

@XmlRootElement
public class Composition {

    private final static Logger logger = Logger.getLogger(Composition.class);

    public enum PlayState {
        PLAYING, // Is the composition playing?
        PAUSED, // Is the composition paused?
        STOPPING, // Is the composition being stopped?
        STOPPED, // Is the composition stopped?
        LOADING, // Is the composition waiting for all files to be loaded to
        // start playing?
        LOADED // Has the composition finished loading all files?
    }

    private PlayState playState = PlayState.STOPPED;

    private String name;

    private final String uuid = String.valueOf(UUID.randomUUID());

    private boolean autoStartNextComposition = false;

    private String notes;

    private long durationMillis;

    private MidiMapping midiMapping = new MidiMapping();

    private List<File> fileList = new ArrayList<>();

    private Manager manager;

    private Timer autoStopTimer;

    private boolean filesLoaded = false;

    // Is this the default composition?
    private boolean defaultComposition = false;

    // Is this composition played as a sample?
    private boolean isSample = false;

    // The gstreamer pipeline, used to sync all files in this composition
    private Pipeline pipeline;

    private long startPosition = 0;

    public void close() throws Exception {
        if (pipeline != null) {
            pipeline.stop();
        }

        for (File file : fileList) {
            if (file.isActive() && file instanceof MidiFile) {
                ((MidiFile) file).close();
            } else if (file.isActive() && file instanceof AudioFile) {
                ((AudioFile) file).close();
            }
        }

        filesLoaded = false;

        // Cancel the auto-stop timer
        if (autoStopTimer != null) {
            autoStopTimer.cancel();
            autoStopTimer = null;
        }

        playState = PlayState.STOPPED;

        if (!defaultComposition && !isSample) {
            manager.getStateService().notifyClients();
        }
    }

    // Create a new pipeline, if there is at least one audio- or video file in this composition
    private boolean compositionNeedsPipeline() {
        if (isSample) {
            return false;
        }

        for (File file : fileList) {
            if (file.isActive() && (file instanceof AudioFile || file instanceof VideoFile)) {
                return true;
            }
        }

        return false;
    }

    // Load all files and construct the complete GST pipeline
    public synchronized void loadFiles() throws Exception {
        MidiPlayer firstMidiPlayer = null;

        if (filesLoaded) {
            return;
        }

        playState = PlayState.LOADING;

        if (!defaultComposition && !isSample) {
            manager.getStateService().notifyClients();
        }

        logger.debug(
                "Loading composition '" + name + "...");

        if (compositionNeedsPipeline()) {
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

        if (!defaultComposition) {
            manager.stopDefaultComposition();
        }

        // Load all files, create the pipeline and handle exceptions to pipeline-playing
        for (int i = 0; i < fileList.size(); i++) {
            File file = fileList.get(i);

            if (file.isActive()) {
                file.setManager(manager);

                if (file instanceof MidiFile) {
                    MidiFile midiFile = (MidiFile) file;

                    for (MidiRouting midiRouting : midiFile.getMidiRoutingList()) {
                        midiRouting.getMidiMapping().setParent(midiMapping);
                    }

                    midiFile.load(pipeline, firstMidiPlayer);

                    if (firstMidiPlayer == null) {
                        firstMidiPlayer = midiFile.getMidiPlayer();
                    }
                } else if (file instanceof AudioFile) {
                    AudioFile audioFile = (AudioFile) file;
                    audioFile.load(isSample);

                    // Samples are played with the AlsaPlayer
                    if (!isSample) {
                        URIDecodeBin audioSource = (URIDecodeBin) ElementFactory.make("uridecodebin", "uridecodebin" + i);
                        audioSource.set("uri", "file://" + ((AudioFile) file).getPath());
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
                } else if (file instanceof VideoFile) {
                    PlayBin playBin = (PlayBin) ElementFactory.make("playbin", "playbin" + i);
                    playBin.set("uri", "file://" + ((VideoFile) file).getPath());
                    pipeline.add(playBin);
                }
            }
        }

        logger.debug("Composition '" + name + "' loaded");

        // Maybe we are stopping meanwhile
        if (playState == PlayState.LOADING && !defaultComposition && !isSample) {
            playState = PlayState.LOADED;
            filesLoaded = true;

            manager.getStateService().notifyClients();
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

        // Workaround, because "this" does not work inside a TimerTask.
        Composition thisComposition = this;

        for (File file : fileList) {
            if (file.isActive()) {
                int fileOffset = 0;

                if (file.isLoop()) {
                    // At least one file is looped -> don't stop the composition
                    // automatically
                    return;
                }

                if (file instanceof MidiFile) {
                    fileOffset = ((MidiFile) file).getFullOffsetMillis();
                } else if (file instanceof AudioFile) {
                    fileOffset = ((AudioFile) file).getFullOffsetMillis();
                } else if (file instanceof VideoFile) {
                    fileOffset = ((VideoFile) file).getFullOffsetMillis();
                }

                if (file.getDurationMillis() + fileOffset > maxDurationAndOffset) {
                    maxDurationAndOffset = file.getDurationMillis() + fileOffset;
                }
            }
        }

        maxDurationAndOffset -= positionMillis;

        logger.debug("Scheduled the auto-stop timer in " + maxDurationAndOffset + " millis");

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    logger.debug("Automatically stopping the composition...");

                    timer.cancel();
                    autoStopTimer = null;

                    if (isSample) {
                        stop(false);
                        manager.getPlayer().sampleCompositionFinishedPlaying(thisComposition);
                    } else {
                        // Don't stop the composition for samples (they should
                        // be short anyway)
                        if (autoStartNextComposition && manager.getCurrentSet().hasNextComposition()) {
                            // Stop, don't play the default composition but
                            // start
                            // playing the next composition
                            manager.getPlayer().stop(false);

                            manager.getCurrentSet().nextComposition(false);
                            manager.getPlayer().play();
                        } else if (manager.getSession().isAutoSelectNextComposition()) {
                            manager.getCompositionManager().nextComposition();
                        } else {
                            // Stop, play the default composition and select the
                            // next composition automatically (if there is one)
                            manager.getPlayer().stop(true);

                            if (manager.getCurrentSet() != null) {
                                manager.getCurrentSet().nextComposition();
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("Could not automatically stop composition '" + name + "'", e);
                }
            }
        }, maxDurationAndOffset);

        autoStopTimer = timer;
    }

    public synchronized void play() throws Exception {
        // Load the files, if not already done by a previously by a separate
        // call
        loadFiles();

        // All files are loaded -> play the composition (start each file)
        logger.info("Playing composition '" + name + "'...");

        if (pipeline != null) {
            pipeline.play();
        }

        for (File file : fileList) {
            if (file.isActive() && file instanceof MidiFile) {
                ((MidiFile) file).play();
            } else if (file instanceof AudioFile) {
                ((AudioFile) file).play();
            }
        }

        startAutoStopTimer();

        playState = PlayState.PLAYING;

        if (!defaultComposition && !isSample) {
            manager.getStateService().notifyClients();
        }
    }

    public synchronized void pause() throws Exception {
        // Cancel the auto-stop timer
        if (autoStopTimer != null) {
            autoStopTimer.cancel();
            autoStopTimer = null;
        }

        if (playState == PlayState.PAUSED) {
            return;
        }

        logger.info("Pausing composition '" + name + "'");

        // Pause the composition
        if (pipeline != null) {
            pipeline.pause();
        }

        for (File file : fileList) {
            if (file.isActive() && file instanceof MidiFile) {
                ((MidiFile) file).pause();
            }
        }

        playState = PlayState.PAUSED;

        if (!defaultComposition && !isSample) {
            manager.getStateService().notifyClients();
        }
    }

    public synchronized void togglePlay() throws Exception {
        if (playState == PlayState.PLAYING) {
            stop();
        } else {
            play();
        }
    }

    public synchronized void stop(boolean playDefaultComposition) throws Exception {
        playState = PlayState.STOPPING;

        if (!defaultComposition && !isSample) {
            manager.getStateService().notifyClients();
        }

        startPosition = 0;

        // Cancel the auto-stop timer
        if (autoStopTimer != null) {
            autoStopTimer.cancel();
            autoStopTimer = null;
        }

        logger.info("Stopping composition '" + name + "'");

        // Stop the composition
        if (pipeline != null) {
            pipeline.stop();
            pipeline = null;
        }

        for (File file : fileList) {
            if (file.isActive() && file instanceof MidiFile) {
                try {
                    ((MidiFile) file).close();
                } catch (Exception e) {
                    logger.error("Could not stop file '" + file.getName() + "'");
                }
            } else if (file instanceof AudioFile) {
                ((AudioFile) file).close();
            }
        }

        filesLoaded = false;

        logger.info("Composition '" + name + "' stopped");

        playState = PlayState.STOPPED;

        if (!defaultComposition && !isSample) {
            manager.getStateService().notifyClients();
        }

        // Play the default composition, if necessary
        if (!defaultComposition && playDefaultComposition && !isSample) {
            manager.playDefaultComposition();
        }
    }

    public void seek(long positionMillis) throws Exception {
        // When we seek before pressing play
        startPosition = positionMillis;

        logger.debug("Seek to position " + positionMillis);

        if (pipeline != null) {
            pipeline.seek(positionMillis, TimeUnit.MILLISECONDS);
        }

        for (File file : fileList) {
            if (file.isActive() && file instanceof MidiFile) {
                ((MidiFile) file).seek(positionMillis);
            }
        }

        startAutoStopTimer();

        if (!isSample) {
            manager.getStateService().notifyClients();
        }
    }

    public synchronized void stop() throws Exception {
        stop(true);
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Composition) {
            Composition composition = (Composition) object;

            return this.uuid.equals(composition.uuid);
        }

        return false;
    }

    @XmlTransient
    public MidiMapping getMidiMapping() {
        return midiMapping;
    }

    public void setMidiMapping(MidiMapping midiMapping) {
        this.midiMapping = midiMapping;
    }

    @XmlElementWrapper(name = "fileList")
    @XmlElements({@XmlElement(type = MidiFile.class, name = "midiFile"),
            @XmlElement(type = VideoFile.class, name = "videoFile"),
            @XmlElement(type = AudioFile.class, name = "audioFile")})
    public List<File> getFileList() {
        return fileList;
    }

    public void setFileList(List<File> fileList) {
        this.fileList = fileList;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlTransient
    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    @XmlTransient
    public PlayState getPlayState() {
        return playState;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    @XmlTransient
    public boolean isAutoStartNextComposition() {
        return autoStartNextComposition;
    }

    public void setAutoStartNextComposition(boolean autoStartNextComposition) {
        this.autoStartNextComposition = autoStartNextComposition;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setPlayState(PlayState playState) {
        this.playState = playState;
    }

    @XmlTransient
    public boolean isDefaultComposition() {
        return defaultComposition;
    }

    public void setDefaultComposition(boolean defaultComposition) {
        this.defaultComposition = defaultComposition;
    }

    @XmlTransient
    public long getPositionMillis() {
        if (startPosition > 0) {
            return startPosition;
        }

        if (pipeline != null) {
            return pipeline.queryPosition(TimeUnit.MILLISECONDS);
        }

        for (File file : fileList) {
            if (file.isActive() && file instanceof MidiFile) {
                return ((MidiFile) file).getPositionMillis();
            }
        }

        return 0;
    }

    @XmlTransient
    public boolean isSample() {
        return isSample;
    }

    public void setSample(boolean isSample) {
        this.isSample = isSample;
    }

}
