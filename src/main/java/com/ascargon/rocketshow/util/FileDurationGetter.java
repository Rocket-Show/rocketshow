package com.ascargon.rocketshow.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.gstreamer.GstDiscoverer;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.ascargon.rocketshow.midi.MidiPlayer;

/**
 * Get the duration of a file.
 *
 * @author Moritz A. Vieli
 */
public class FileDurationGetter implements Runnable {

	private final static Logger logger = LoggerFactory.getLogger(FileDurationGetter.class);

	private static final String DURATION = "ID_LENGTH=";

	private SettingsService settingsService;
    private GstDiscoverer gstDiscoverer;

	private final CompositionFile compositionFile;

	public FileDurationGetter(SettingsService settingsService, CompositionFile compositionFile) {
		this.settingsService = settingsService;

		this.compositionFile = compositionFile;
		this.gstDiscoverer = new GstDiscoverer();
	}

	private static boolean isDurationLine(String line) {
		return line.startsWith(DURATION);
	}

	private long getDurationWithMplayer(String path) throws Exception {
		ProcessBuilder pb = new ProcessBuilder("mplayer", "-identify", "-frames", "0", path);
		Process process = pb.start();
		BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line;

		while ((line = br.readLine()) != null) {
			if (isDurationLine(line)) {
				double duration = Double.parseDouble(line.trim().substring(DURATION.length()));
				return Math.round(duration) * 1000;
			}
		}

		return 0;
	}

	private long getDurationWithGstreamer(String path) {
        path = "file:///Users/vio/git/RocketShow/target/media/audio/head_smashed_far_away.wav";



        return 0;
    }

	@Override
	public void run() {
	    String path = settingsService.getSettings().getBasePath() + "/" + settingsService.getSettings().getMediaPath() + "/";

		try {
			if (compositionFile instanceof MidiCompositionFile) {
				compositionFile.setDurationMillis(MidiPlayer.getDuration(path + settingsService.getSettings().getMidiPath() +  "/" + compositionFile.getName()));
			} else if (compositionFile instanceof AudioCompositionFile) {
				//compositionFile.setDurationMillis(getDurationWithMplayer(path + settingsService.getSettings().getAudioPath() + "/" + compositionFile.getName()));
			    logger.info("Duration: " + getDurationWithGstreamer(path + settingsService.getSettings().getAudioPath() + "/" + compositionFile.getName()));
			} else if (compositionFile instanceof VideoCompositionFile) {
				compositionFile.setDurationMillis(getDurationWithMplayer(path + settingsService.getSettings().getVideoPath() + "/" + compositionFile.getName()));
			}
		} catch (Exception e) {
			logger.error("Could not get duration for file '" + compositionFile.getName() + "'", e);
		}
	}

}
