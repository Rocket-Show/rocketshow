package com.ascargon.rocketshow;

import com.ascargon.rocketshow.audio.AudioBus;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBException;

@Service
public interface SettingsService {

    Settings getSettings();

    void setSettings(Settings settings);

    RemoteDevice getRemoteDeviceByName(String name);

    AudioBus getAudioBusFromName(String outputBus);

    String getAlsaDeviceFromOutputBus(String outputBus);

    int getTotalAudioChannels();

    void load() throws Exception;

    void save() throws JAXBException;

}
