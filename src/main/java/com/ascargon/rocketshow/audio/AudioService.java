package com.ascargon.rocketshow.audio;

import org.freedesktop.gstreamer.elements.BaseSink;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AudioService {

    List<AudioDevice> getAudioDevices();

    int getMaxAvailableSinkChannels();

    String getAudioDeviceAlsaName(AudioDevice audioDevice);

    BaseSink getGstAudioSink(AudioDevice audioDevice);

}
