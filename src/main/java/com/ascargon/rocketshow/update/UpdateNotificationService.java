package com.ascargon.rocketshow.update;

import org.springframework.stereotype.Service;

@Service
public interface UpdateNotificationService {

    void notifyClients(UpdateState updateState);

}
