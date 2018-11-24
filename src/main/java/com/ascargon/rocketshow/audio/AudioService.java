package com.ascargon.rocketshow.audio;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AudioService {

    List<AudioDevice> getAudioDevices();

}
