package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.util.ShellManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DefaultIpTablesInitializationService implements IpTablesInitializationService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultIpTablesInitializationService.class);

    public DefaultIpTablesInitializationService(OperatingSystemInformationService operatingSystemInformationService) {
        if(!OperatingSystemInformation.SubType.RASPBIAN.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            return;
        }

        try {
            new ShellManager(new String[]{"sudo", "iptables", "-t", "nat", "-A", "POSTROUTING", "-o", "eth0", "-j",
                    "MASQUERADE"});
        } catch (IOException e) {
            logger.error("Could not initialize iptables", e);
        }
    }

}
