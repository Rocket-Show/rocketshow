package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.Settings;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AudioService {

    List<AudioDevice> getAudioDevices();

    boolean isAudioChannelCountCompatible(Settings settings, int channelCount);

}
