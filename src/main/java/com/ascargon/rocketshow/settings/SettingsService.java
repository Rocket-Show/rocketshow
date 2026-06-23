package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.audio.AudioBus;
import jakarta.xml.bind.JAXBException;
import org.springframework.stereotype.Service;

@Service
public interface SettingsService {

    Settings getSettings();

    boolean isReadOnlyFileSystem();

    void setSettings(Settings settings);

    AudioBus getAudioBusByUuid(String outputBusUuid);

    String getAlsaDeviceFromOutputBusUuid(String outputBusUuid);

    RemoteDevice getRemoteDeviceByName(String name);

    void load() throws Exception;

    void save() throws JAXBException;

}
