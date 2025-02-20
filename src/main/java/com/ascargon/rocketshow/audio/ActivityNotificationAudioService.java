package com.ascargon.rocketshow.audio;

import org.springframework.stereotype.Service;

@Service
public interface ActivityNotificationAudioService {

    void notifyClients(double[] volumeDbs);

}
