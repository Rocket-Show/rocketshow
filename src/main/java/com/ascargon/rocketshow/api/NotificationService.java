package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.util.UpdateService;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Notify clients about the current state of the device.
 */
@Service
public interface NotificationService {

    // Notify the clients about the current state and include update
    // information, if an update is running
    void notifyClients(UpdateService.UpdateState updateState) throws Exception;

    void notifyClients(PlayerService playerService) throws Exception;

    void notifyClients(SetService setService) throws Exception;

    void notifyClients(PlayerService playerService, SetService setService) throws Exception;

    void notifyClients(PlayerService playerService, SetService setService, boolean isUpdateFinished) throws IOException;

    void notifyClients(String error) throws Exception;

    void notifyClients() throws Exception;

}
