package com.ascargon.rocketshow.api;

import org.springframework.stereotype.Service;

@Service
public interface ActivityNotificationAudioService {

    void notifyClients(double[] volumeDbs);

}
