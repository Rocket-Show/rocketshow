package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioDevice;
import jakarta.xml.bind.JAXBException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface SettingsService {

    Settings getSettings();

    void setSettings(Settings settings);

    AudioBus getAudioBusByName(String outputBus);

    String getAlsaDeviceFromOutputBus(String outputBus);

    RemoteDevice getRemoteDeviceByName(String name);

    void load() throws Exception;

    void save() throws JAXBException;

}
