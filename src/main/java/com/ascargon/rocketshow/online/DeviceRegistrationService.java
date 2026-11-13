package com.ascargon.rocketshow.online;

import org.springframework.stereotype.Service;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;

@Service
public interface DeviceRegistrationService {

    // What the device tells the online designer about itself with each sync of the fixture library: which versions of
    // Rocket Show are in use, on which hardware, and how much the device is used. Never the names or the content of
    // files, compositions and projects.
    ObjectNode getRegistration() throws IOException;

}
