package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.SettingsService;

public class AudioCompositionFilePlayer {

    private final SettingsService settingsService;

    private final AudioCompositionFile audioCompositionFile;
    private final boolean isSample;
    private AlsaPlayer alsaPlayer;
    private final String path;

    public AudioCompositionFilePlayer(SettingsService settingsService, AudioCompositionFile audioCompositionFile, String path, boolean isSample) {
        this.settingsService = settingsService;

        this.audioCompositionFile = audioCompositionFile;
        this.isSample = isSample;
        this.path = path;

        // Play samples with alsa, because it's important to play it faster but
        // sync is less important
        // TODO Make this setting configurable
        if (isSample) {
            alsaPlayer = new AlsaPlayer();
        }
    }

    public void play() throws Exception {
        if (isSample) {
            alsaPlayer.play(settingsService.getAlsaDeviceFromOutputBus(audioCompositionFile.getOutputBus()), path);
        }
    }

    public void stop() throws Exception {
        if (alsaPlayer != null) {
            alsaPlayer.close();
            alsaPlayer = null;
        }
    }

}
