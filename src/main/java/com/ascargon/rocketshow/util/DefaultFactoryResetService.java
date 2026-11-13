package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.RocketShowApplication;
import com.ascargon.rocketshow.online.AccountService;
import com.ascargon.rocketshow.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Resets all data to its defaults for the current version.
 *
 * @author Moritz A. Vieli
 */
@Service
public class DefaultFactoryResetService implements FactoryResetService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultFactoryResetService.class);

    private final OperatingSystemInformationService operatingSystemInformationService;
    private final AccountService accountService;

    public DefaultFactoryResetService(OperatingSystemInformationService operatingSystemInformationService, AccountService accountService) {
        this.operatingSystemInformationService = operatingSystemInformationService;
        this.accountService = accountService;
    }

    @Override
    public void reset() throws Exception {
        if (!OperatingSystemInformation.SubType.RASPBERRYOS.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            return;
        }

        logger.info("Factory reset...");

        // The reset deletes the token of the account along with the data: unlink the device online as well, so that the
        // account doesn't keep it in "My devices"
        accountService.unlink();

        ShellManager shellManager = new ShellManager(new String[]{"sudo", new ApplicationHome(RocketShowApplication.class).getDir() + File.separator + "reset.sh"});
        shellManager.getProcess().waitFor();

        logger.info("Factory reset finished");
    }

}