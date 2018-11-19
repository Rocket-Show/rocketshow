package com.ascargon.rocketshow;

import org.springframework.stereotype.Service;

import javax.sound.midi.Transmitter;

@Service
public interface SettingsService {

    Settings getSettings();

    RemoteDevice getRemoteDeviceByName(String name);

    String getAlsaDeviceFromOutputBus(String outputBus);

}
