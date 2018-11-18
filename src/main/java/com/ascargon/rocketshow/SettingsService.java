package com.ascargon.rocketshow;

import org.springframework.stereotype.Service;

@Service
public interface SettingsService {

    Settings getSettings();

    RemoteDevice getRemoteDeviceByName(String name);

    String getAlsaDeviceFromOutputBus(String outputBus);

}
