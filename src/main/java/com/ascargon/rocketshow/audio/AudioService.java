package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.Settings;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AudioService {

    List<AudioDevice> getAudioDevices();

    BaseSink getGstAudioSink();

    int getMaxAvailableSinkChannels();

}
