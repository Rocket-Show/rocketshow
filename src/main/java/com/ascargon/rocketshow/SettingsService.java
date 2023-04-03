package com.ascargon.rocketshow;

import com.ascargon.rocketshow.audio.AudioBus;
import jakarta.xml.bind.JAXBException;
import org.springframework.stereotype.Service;

@Service
public interface SettingsService {

    Settings getSettings();

    void setSettings(Settings settings);

    AudioBus getAudioBusFromName(String outputBus);

    String getAlsaDeviceFromOutputBus(String outputBus);

    RemoteDevice getRemoteDeviceByName(String name);

    int getTotalAudioChannels();

    void load() throws Exception;

    void save() throws JAXBException;

}
