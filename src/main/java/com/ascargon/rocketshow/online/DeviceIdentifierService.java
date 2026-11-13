package com.ascargon.rocketshow.online;

import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public interface DeviceIdentifierService {

    // The identifier of this device for the API of the online designer, hashed: it tells devices apart and links a
    // device to its account, but says nothing about the device
    String getIdentifier() throws IOException;

}
