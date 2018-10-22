package com.ascargon.rocketshow.composition;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.audio.AudioPlayer;
import com.ascargon.rocketshow.audio.AudioPlayer.PlayerType;

public class AudioFile extends com.ascargon.rocketshow.composition.File {

	final static Logger logger = Logger.getLogger(AudioFile.class);

	public final static String AUDIO_PATH = "audio/";

	private AudioPlayer audioPlayer;

	private String outputBus;

	private Timer playTimer;

	@XmlTransient
	public String getPath() {
		return Manager.BASE_PATH + MEDIA_PATH + AUDIO_PATH + getName();
	}

	@Override
	public void load(long positionMillis) throws Exception {
		PlayerType playerType;
		
		logger.debug("Loading file '" + this.getName() + " at millisecond position " + positionMillis + "...");

		this.setLoaded(false);
		this.setLoading(true);

		if (audioPlayer == null) {
			audioPlayer = new AudioPlayer();
		}

		audioPlayer.setLoop(this.isLoop());
		
		playerType = this.getManager().getSettings().getAudioPlayerType();
		
		// Play samples with alsa, because no sync is required and it's faster
		if(this.getComposition().isSample()) {
			playerType = PlayerType.ALSA_PLAYER;
		}
		
		audioPlayer.load(playerType, this, getPath(), positionMillis,
				this.getManager().getSettings().getAudioOutput(),
				this.getManager().getSettings().getAlsaDeviceFromOutputBus(outputBus));
	}

	@Override
	public void close() throws Exception {
		stop();
	}

	@XmlTransient
	public int getFullOffsetMillis() {
		return this.getOffsetMillis() + this.getManager().getSettings().getOffsetMillisAudio();
	}

	@Override
	public void play() throws Exception {
		String path = getPath();

		if (audioPlayer == null) {
			logger.error("Audio player not initialized for file '" + path + "'");
			return;
		}

		if (this.getFullOffsetMillis() > 0) {
			logger.debug("Wait " + this.getFullOffsetMillis() + " milliseconds before starting the audio file '"
					+ this.getPath() + "'");

			playTimer = new Timer();
			playTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					try {
						playTimer.cancel();
						playTimer = null;
						audioPlayer.play();
					} catch (IOException e) {
						logger.error("Could not play audio file '" + path + "'", e);
					}
				}
			}, this.getFullOffsetMillis());
		} else {
			audioPlayer.play();
		}
	}

	@Override
	public void pause() throws IOException {
		if (playTimer != null) {
			playTimer.cancel();
			playTimer = null;
		}

		if (audioPlayer == null) {
			logger.error("Audio player not initialized for file '" + getPath() + "'");
			return;
		}

		audioPlayer.pause();
	}

	@Override
	public void resume() throws IOException {
		if (audioPlayer == null) {
			logger.error("Audio player not initialized for file '" + getPath() + "'");
			return;
		}

		audioPlayer.resume();
	}

	@Override
	public void stop() throws Exception {
		if (playTimer != null) {
			playTimer.cancel();
			playTimer = null;
		}

		if (audioPlayer == null) {
			logger.error("Audio player not initialized for file '" + getPath() + "'");
			return;
		}

		this.setLoaded(false);
		audioPlayer.stop();
	}

	public String getOutputBus() {
		return outputBus;
	}

	public void setOutputBus(String outputBus) {
		this.outputBus = outputBus;
	}

	public FileType getType() {
		return FileType.AUDIO;
	}

}
