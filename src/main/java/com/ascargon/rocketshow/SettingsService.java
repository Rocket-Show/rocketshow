package com.ascargon.rocketshow;

import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBException;

@Service
public interface SettingsService {

    Settings getSettings();

    RemoteDevice getRemoteDeviceByName(String name);

    String getAlsaDeviceFromOutputBus(String outputBus);

    void load() throws Exception;

    void save() throws JAXBException;

}
