package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.util.Updater;
import org.springframework.stereotype.Service;

/**
 * Notify clients about the current state of the device.
 */
@Service
public interface NotificationService {

    // Notify the clients about the current state and include a midi signal, if
    // midi learn is activated
    void notifyClients(MidiSignal midiSignal) throws Exception;

    // Notify the clients about the current state and include update
    // information, if an update is running
    void notifyClients(Updater.UpdateState updateState) throws Exception;

    void notifyClients(PlayerService playerService) throws Exception;

    void notifyClients(SetService setService) throws Exception;

    void notifyClients(boolean isUpdateFinished) throws Exception;

    void notifyClients() throws Exception;

}
