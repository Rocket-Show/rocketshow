package com.ascargon.rocketshow.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ascargon.rocketshow.midi.MidiPlayer;

/**
 * Get the duration of a file.
 *
 * @author Moritz A. Vieli
 */
public class FileDurationGetter implements Runnable {

	private final static Logger logger = LogManager.getLogger(FileDurationGetter.class);

	private static final String DURATION = "ID_LENGTH=";

	private SettingsService settingsService;

	private CompositionFile compositionFile;

	public FileDurationGetter(SettingsService settingsService, CompositionFile compositionFile) {
		this.compositionFile = compositionFile;
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

	@Override
	public void run() {
	    String path = settingsService.getSettings().getBasePath() + "/" + settingsService.getSettings().getMediaPath() + "/";

		try {
			if (compositionFile instanceof MidiCompositionFile) {
				MidiCompositionFile midiFile = (MidiCompositionFile) compositionFile;
				compositionFile.setDurationMillis(MidiPlayer.getDuration(path + settingsService.getSettings().getMidiPath()));
			} else if (compositionFile instanceof AudioCompositionFile) {
				AudioCompositionFile audioFile = (AudioCompositionFile) compositionFile;
				compositionFile.setDurationMillis(getDurationWithMplayer(path + settingsService.getSettings().getAudioPath()));
			} else if (compositionFile instanceof VideoCompositionFile) {
				VideoCompositionFile videoFile = (VideoCompositionFile) compositionFile;
				compositionFile.setDurationMillis(getDurationWithMplayer(path + settingsService.getSettings().getVideoPath()));
			}
		} catch (Exception e) {
			logger.error("Could not get duration for file '" + compositionFile.getName() + "'", e);
		}
	}

}
