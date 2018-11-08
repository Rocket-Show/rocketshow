package com.ascargon.rocketshow.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.composition.Composition.PlayState;
import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.util.Updater.UpdateState;

/**
 * Handle the current state of the device and notify all connected clients on
 * updates.
 *
 * @author Moritz A. Vieli
 */
@ServerEndpoint(value = "/state")
public class StateManager {

	private final static Logger logger = Logger.getLogger(StateManager.class);

	private static List<Session> activeSessions = new ArrayList<>();

	private Manager manager;

	public StateManager() {
	}

	public void load(Manager manager) {
		this.manager = manager;
	}

	@OnOpen
	public void onOpen(Session session) {
		activeSessions.add(session);
	}

	@OnClose
	public void onClose(Session session) {
		activeSessions.remove(session);
	}

	@OnError
	public void onError(Session session, Throwable throwable) {
		logger.error("Got web socket error", throwable);

		// Session maybe closed?
		activeSessions.remove(session);
	}

	public State getCurrentState() {
		State currentState = new State();

		currentState.setPlayState(PlayState.STOPPED);
		currentState.setCurrentCompositionIndex(0);

		if (manager != null) {
			currentState.setPlayState(manager.getPlayer().getPlayState());
			currentState.setCurrentCompositionName(manager.getPlayer().getCompositionName());
			currentState.setCurrentCompositionDurationMillis(manager.getPlayer().getCompositionDurationMillis());
			currentState.setPositionMillis(manager.getPlayer().getPositionMillis());
			currentState.setInitializing(manager.isInitializing());
			
			if (manager.getCurrentSet() != null) {
				currentState.setCurrentCompositionIndex(manager.getCurrentSet().getCurrentCompositionIndex());
				currentState.setCurrentSetName(manager.getCurrentSet().getName());
			}

			if (manager.getSession() != null) {
				currentState.setUpdateFinished(manager.getSession().isUpdateFinished());
			}
		}

		return currentState;
	}

	// Get the current state and add a midi signal, if needed
	private String getSerializedState(MidiSignal midiSignal, UpdateState updateState) throws Exception {
		State currentState = getCurrentState();

		currentState.setMidiSignal(midiSignal);
		currentState.setUpdateState(updateState);

		// Convert the object to json
		ObjectMapper mapper = new ObjectMapper();

		return mapper.writeValueAsString(currentState);
	}

	private void notifyClients(MidiSignal midiSignal, UpdateState updateState) throws Exception {
		String state = getSerializedState(midiSignal, updateState);

		// Send the state to each connected client
		for (Session activeSession : activeSessions) {
			try {
				if (activeSession.isOpen()) {
					activeSession.getBasicRemote().sendText(state);
				}
			} catch (IOException e) {
				logger.error("Could not notify client on state", e);
			}
		}
	}

	// Notify the clients about the current state and include a midi signal, if
	// midi learn is activated
	public void notifyClients(MidiSignal midiSignal) throws Exception {
		notifyClients(midiSignal, null);
	}

	// Notify the clients about the current state and include update
	// information, if an update is running
	public void notifyClients(UpdateState updateState) throws Exception {
		notifyClients(null, updateState);
	}

	public void notifyClients() throws Exception {
		notifyClients(null, null);
	}

}
