package com.ascargon.rocketshow;

public interface SettingsService {

    Settings getSettings();

    RemoteDevice getRemoteDeviceByName(String name);

}
