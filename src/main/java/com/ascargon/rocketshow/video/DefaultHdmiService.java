package com.ascargon.rocketshow.video;

import org.springframework.stereotype.Service;

@Service
public class DefaultHdmiService implements HdmiService {

	@Override
	public boolean isPluggedIn() throws Exception {
        // TODO
        return true;
	}

}
