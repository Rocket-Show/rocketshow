package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

/**
 * Resets the whole application to its state where it was installed.
 *
 * @author Moritz A. Vieli
 */
@Service
public class DefaultFactoryResetService implements FactoryResetService {

	@Override
	public void reset() throws Exception {
		// Reset the interface
		ShellManager shellManager = new ShellManager(new String[] { "sudo", "/opt/rocketshow_reset.sh" });

		shellManager.getProcess().waitFor();
	}

}
