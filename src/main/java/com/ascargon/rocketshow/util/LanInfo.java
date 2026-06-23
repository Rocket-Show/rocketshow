package com.ascargon.rocketshow.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LanInfo {

    private String ipAddress;
    private String subnetMask;
    private String gateway;
    private String dns1;
    private String dns2;

}
