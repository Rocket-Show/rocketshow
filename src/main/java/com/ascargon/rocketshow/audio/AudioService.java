package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.settings.Settings;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AudioService {

    List<AudioDevice> getAudioDevices();

    int getMaxAvailableSinkChannels();

    String getAudioDeviceAlsaName(AudioDevice audioDevice);

    BaseSink getGstAudioSink(AudioDevice audioDevice, boolean provideClock);

    int getChannelCountByAudioDevice(Settings settings, AudioDevice audioDevice);

    List<AudioDevice> getAudioDeviceInUseList(Settings settings);

    // The sink and the source of a sound card itself (ALSA "hw"), without the audio settings, e.g. for the self-test
    BaseSink getGstHardwareAudioSink(AudioDevice audioDevice);

    Element getGstHardwareAudioSource(AudioDevice audioDevice);

    // The most channels a sound card plays (or records), 0 if it can't. Opens the sound card for a moment.
    int getHardwareChannelCount(AudioDevice audioDevice, boolean capture);

}
