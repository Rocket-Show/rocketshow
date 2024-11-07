package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.settings.Settings;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AudioService {

    List<AudioDevice> getAudioDevices();

    int getMaxAvailableSinkChannels();

    String getAudioDeviceAlsaName(AudioDevice audioDevice);

    BaseSink getGstAudioSink(AudioDevice audioDevice);

    int getChannelCountByAudioDevice(Settings settings, AudioDevice audioDevice);

    List<AudioDevice> getAudioDeviceInUseList(Settings settings);

}
