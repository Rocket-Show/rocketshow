package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

@Service
public interface DeviceInformationService {

    DeviceInformation getDeviceInformation();

    void storeDeviceInformation(DeviceInformation deviceInformation) throws Exception;

}
