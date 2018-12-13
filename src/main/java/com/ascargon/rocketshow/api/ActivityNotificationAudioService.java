package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.midi.MidiSignal;
import org.springframework.stereotype.Service;

@Service
public interface ActivityNotificationAudioService {

    void notifyClients(AudioBus audioBus, int channel, double volumeDb);

}
