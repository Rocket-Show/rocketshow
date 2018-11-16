package com.ascargon.rocketshow.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.apache.log4j.Logger;

import com.ascargon.rocketshow.midi.MidiPlayer;

/**
 * Get the duration of a file.
 *
 * @author Moritz A. Vieli
 */
public class FileDurationGetter implements Runnable {

	private final static Logger logger = Logger.getLogger(FileDurationGetter.class);

	private static final String DURATION = "ID_LENGTH=";

	private CompositionFile compositionFile;

	public FileDurationGetter(CompositionFile compositionFile) {
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
		try {
			if (compositionFile instanceof MidiCompositionFile) {
				MidiCompositionFile midiFile = (MidiCompositionFile) compositionFile;
				compositionFile.setDurationMillis(MidiPlayer.getDuration(midiFile.getPath()));
			} else if (compositionFile instanceof AudioCompositionFile) {
				AudioCompositionFile audioFile = (AudioCompositionFile) compositionFile;
				compositionFile.setDurationMillis(getDurationWithMplayer(audioFile.getPath()));
			} else if (compositionFile instanceof VideoCompositionFile) {
				VideoCompositionFile videoFile = (VideoCompositionFile) compositionFile;
				compositionFile.setDurationMillis(getDurationWithMplayer(videoFile.getPath()));
			}
		} catch (Exception e) {
			logger.error("Could not get duration for file '" + compositionFile.getName() + "'", e);
		}
	}

}
