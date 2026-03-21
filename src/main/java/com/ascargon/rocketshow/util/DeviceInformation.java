package com.ascargon.rocketshow.util;

import lombok.Getter;
import lombok.Setter;

// Ready to use device information. Not available on the community edition.
@Getter
@Setter
public class DeviceInformation {

    // Version of the config file
    private String fileVersion = "1.0";

    // Used mainly for the wireless AP
    private String country;

    private String serial;
    private String model;
    private String hardwareRevision;
    private String sku;

}
