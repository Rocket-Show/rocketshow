package com.ascargon.rocketshow;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.api.StateManager;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionManager;
import com.ascargon.rocketshow.composition.FileManager;
import com.ascargon.rocketshow.composition.Set;
import com.ascargon.rocketshow.dmx.DmxManager;
import com.ascargon.rocketshow.dmx.Midi2DmxConverter;
import com.ascargon.rocketshow.image.ImageDisplayer;
import com.ascargon.rocketshow.midi.MidiControlActionExecuter;
import com.ascargon.rocketshow.midi.MidiDeviceConnectedListener;
import com.ascargon.rocketshow.midi.MidiInDeviceReceiver;
import com.ascargon.rocketshow.midi.MidiRouting;
import com.ascargon.rocketshow.midi.MidiUtil;
import com.ascargon.rocketshow.midi.MidiUtil.MidiDirection;
import com.ascargon.rocketshow.raspberry.RaspberryGpioControlActionExecuter;
import com.ascargon.rocketshow.util.ControlActionExecuter;
import com.ascargon.rocketshow.util.ResetUsb;
import com.ascargon.rocketshow.util.ShellManager;
import com.ascargon.rocketshow.util.Updater;

public class Manager {

	final static Logger logger = Logger.getLogger(Manager.class);

	public final static String BASE_PATH = "/opt/rocketshow/";

	// Manage the client states (web GUI)
	private StateManager stateManager;

	private Updater updater;

	private CompositionManager compositionManager;
	private FileManager fileManager;

	private ControlActionExecuter controlActionExecuter;

	private ImageDisplayer imageDisplayer;
	private MidiInDeviceReceiver midiInDeviceReceiver;
	private MidiControlActionExecuter midi2ActionConverter;

	private DmxManager dmxManager;
	private Midi2DmxConverter midi2DmxConverter;

	private MidiDevice midiOutDevice;
	private Timer connectMidiOutDeviceTimer;
	private List<MidiDeviceConnectedListener> midiOutDeviceConnectedListeners = new ArrayList<MidiDeviceConnectedListener>();

	private Session session;
	private Settings settings;

	private Set currentSet;

	private Composition defaultComposition;

	private Player player;

	// The Raspberry GPIO controller
	private RaspberryGpioControlActionExecuter raspberryGpioControlActionExecuter;

	private boolean isInitializing = true;

	public void addMidiOutDeviceConnectedListener(MidiDeviceConnectedListener listener) {
		midiOutDeviceConnectedListeners.add(listener);

		// We already have a device connected -> fire the listener
		if (midiOutDevice != null) {
			listener.deviceConnected(midiOutDevice);
		}
	}

	public void removeMidiOutDeviceConnectedListener(MidiDeviceConnectedListener listener) {
		midiOutDeviceConnectedListeners.remove(listener);
	}

	public void reconnectMidiDevices() throws MidiUnavailableException {
		if (midiInDeviceReceiver != null) {
			midiInDeviceReceiver.connectMidiReceiver();
		}

		connectMidiSender();
	}

	/**
	 * Connect to the MIDI out device. Also call this method, if you change the
	 * settings or want to reconnect the device.
	 *
	 * @throws MidiUnavailableException
	 */
	public void connectMidiSender() throws MidiUnavailableException {
		// Cancel an eventually existing timer
		if (connectMidiOutDeviceTimer != null) {
			connectMidiOutDeviceTimer.cancel();
			connectMidiOutDeviceTimer = null;
		}

		if (midiOutDevice != null && midiOutDevice.isOpen()) {
			// We already have an open sender -> close this one
			try {
				midiOutDevice.close();
			} catch (Exception e) {
			}
		}

		com.ascargon.rocketshow.midi.MidiDevice midiDevice = settings.getMidiOutDevice();

		logger.trace(
				"Try connecting to output MIDI device " + midiDevice.getId() + " \"" + midiDevice.getName() + "\"");

		midiOutDevice = MidiUtil.getHardwareMidiDevice(midiDevice, MidiDirection.OUT);

		if (midiOutDevice == null) {
			logger.trace("MIDI output device not found. Try again in 10 seconds.");

			TimerTask timerTask = new TimerTask() {
				@Override
				public void run() {
					try {
						connectMidiSender();
					} catch (Exception e) {
						logger.debug("Could not connect to MIDI output device", e);
					}
				}
			};

			connectMidiOutDeviceTimer = new Timer();
			connectMidiOutDeviceTimer.schedule(timerTask, 10000);

			return;
		}

		// We found a device
		if (connectMidiOutDeviceTimer != null) {
			connectMidiOutDeviceTimer.cancel();
			connectMidiOutDeviceTimer = null;
		}

		midiOutDevice.open();

		// Connect all listeners
		for (MidiDeviceConnectedListener listener : midiOutDeviceConnectedListeners) {
			listener.deviceConnected(midiOutDevice);
		}

		// We found the device and all listeners are connected
		logger.info("Successfully connected to output MIDI device " + midiOutDevice.getDeviceInfo().getName());
	}

	public void playDefaultComposition() throws Exception {
		logger.debug("Playing the default composition...");

		if (defaultComposition != null) {
			// The default composition is already initialized
			return;
		}

		if (settings.getDefaultComposition() == null || settings.getDefaultComposition().length() == 0) {
			return;
		}

		logger.info("Play default composition");

		defaultComposition = compositionManager.getComposition(settings.getDefaultComposition());
		defaultComposition.setManager(this);
		defaultComposition.setDefaultComposition(true);
		defaultComposition.play();
	}

	public void stopDefaultComposition() throws Exception {
		if (defaultComposition == null) {
			return;
		}

		logger.debug("Stopping the default composition...");

		defaultComposition.stop();
		defaultComposition = null;
	}

	public void load() throws IOException {
		logger.info("Initialize...");

		// Initialize the filemanager
		fileManager = new FileManager();

		// Initialize the session
		session = new Session();

		// Initialize the settings
		settings = new Settings();

		// Initialize the compositionmanager
		compositionManager = new CompositionManager(this);

		// Cache all compositions and sets
		try {
			compositionManager.loadAllCompositions();
		} catch (Exception e) {
			logger.error("Could not cache the compositions", e);
		}

		try {
			compositionManager.loadAllSets();
		} catch (Exception e) {
			logger.error("Could not cache the sets", e);
		}

		// Setup iptables, because it's not working properly in pi-gen distro
		// generation
		try {
			new ShellManager(new String[] { "sudo", "iptables", "-t", "nat", "-A", "POSTROUTING", "-o", "eth0", "-j",
					"MASQUERADE" });
		} catch (IOException e) {
			logger.error("Could not initialize iptables", e);
		}

		// Initialize the player
		player = new Player(this);

		// Initialize the client state
		stateManager = new StateManager();
		stateManager.load(this);

		// Initialize the updater
		updater = new Updater(this);

		// Initialize the control action executer
		controlActionExecuter = new ControlActionExecuter(this);

		// Initialize the MIDI action converter
		midi2ActionConverter = new MidiControlActionExecuter(this);

		// Initialize the DMX manager
		dmxManager = new DmxManager(this);
		midi2DmxConverter = new Midi2DmxConverter(dmxManager);

		// Initialize the image displayer and display a default black screen
		try {
			imageDisplayer = new ImageDisplayer();
			imageDisplayer.display(BASE_PATH + "black.jpg");
		} catch (IOException e) {
			logger.error("Could not initialize image displayer", e);
		}

		// Load the settings
		try {
			loadSettings();
		} catch (Exception e) {
			logger.error("Could not load the settings", e);
		}

		// Save the settings (in case none were already existant)
		try {
			saveSettings();
		} catch (JAXBException e) {
			logger.error("Could not save settings", e);
		}

		// Initialize the required objects inside settings
		if (settings.getDeviceInMidiRoutingList() != null) {
			for (MidiRouting deviceInMidiRouting : settings.getDeviceInMidiRoutingList()) {
				try {
					deviceInMidiRouting.load(this);
				} catch (MidiUnavailableException e) {
					logger.error("Could not initialize the MIDI input device routing");
				}
			}
		}

		// Initialize the MIDI receiver
		midiInDeviceReceiver = new MidiInDeviceReceiver(this);
		try {
			midiInDeviceReceiver.load();
		} catch (MidiUnavailableException e) {
			logger.error("Could not initialize the MIDI receiver", e);
		}

		// Initialize the MIDI out device
		try {
			connectMidiSender();
		} catch (MidiUnavailableException e) {
			logger.error("Could not initialize the MIDI out device", e);
		}

		// Restore the session from the file
		try {
			restoreSession();
		} catch (Exception e) {
			logger.error("Could not restore session", e);
		}

		// Initialize the Raspberry GPIO control action executer
		try {
			raspberryGpioControlActionExecuter = new RaspberryGpioControlActionExecuter(this);
		} catch (Exception e) {
			logger.error("Could not initialize the Raspberry GPIO controller", e);
		}

		isInitializing = false;

		logger.info("Finished initializing");
	}

	public void saveSettings() throws JAXBException {
		File file = new File(BASE_PATH + "settings");
		JAXBContext jaxbContext = JAXBContext.newInstance(Settings.class);
		Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

		// output pretty printed
		jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

		jaxbMarshaller.marshal(settings, file);

		settings.updateSystem();

		logger.info("Settings saved");
	}

	public void loadSettings() throws Exception {
		File file = new File(BASE_PATH + "settings");

		if (!file.exists() || file.isDirectory()) {
			return;
		}

		// Restore the session from the file
		JAXBContext jaxbContext = JAXBContext.newInstance(Settings.class);

		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		settings = (Settings) jaxbUnmarshaller.unmarshal(file);

		settings.updateSystem();

		// Reset the USB interface, if needed
		try {
			if (settings.isResetUsbAfterBoot()) {
				logger.info("Resetting all USB devices");
				ResetUsb.resetAllInterfaces();
			}
		} catch (Exception e) {
			logger.error("Could not reset the USB devices", e);
		}

		// Play the default composition, if set
		playDefaultComposition();

		logger.info("Settings loaded");
	}

	public void saveSession() {
		if (currentSet == null) {
			session.setCurrentSetName("");
		} else {
			session.setCurrentSetName(currentSet.getName());
		}

		try {
			File file = new File(BASE_PATH + "session");
			JAXBContext jaxbContext = JAXBContext.newInstance(Session.class);
			Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

			// output pretty printed
			jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

			jaxbMarshaller.marshal(session, file);

			logger.info("Session saved");
		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}

	private void restoreSession() throws Exception {
		File file = new File(BASE_PATH + "session");

		if (file.exists()) {
			// We already have a session -> restore it from the file
			try {
				JAXBContext jaxbContext = JAXBContext.newInstance(Session.class);

				Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
				session = (Session) jaxbUnmarshaller.unmarshal(file);

				logger.info("Session restored");
			} catch (JAXBException e) {
				e.printStackTrace();
			}
		} else {
			// There is no session existant -> create a default session
			saveSession();
		}

		// Load the last set/composition
		if (session != null && session.getCurrentSetName() != null && session.getCurrentSetName().length() > 0) {
			// Load the last set
			loadSetAndComposition(session.getCurrentSetName());
		} else {
			// Load the default set
			loadSetAndComposition("");
		}
	}

	public void loadSetAndComposition(String setName) throws Exception {
		if (currentSet != null) {
			// Unload the current set
			currentSet.close();
			currentSet = null;
		}

		if (setName.length() > 0) {
			// Load the new set
			currentSet = compositionManager.getSet(setName);
			currentSet.setManager(this);
			currentSet.setName(setName);
		}

		// Read the current composition file
		if (currentSet == null) {
			// We have no set. Simply read the first composition, if available
			logger.debug("Try setting a default composition...");

			List<Composition> compositions = compositionManager.getAllCompositions();

			if (compositions.size() > 0) {
				logger.debug("Set default composition '" + compositions.get(0).getName() + "'...");

				player.setComposition(compositions.get(0));
			}
		} else {
			// We got a set loaded
			try {
				currentSet.readCurrentComposition();
			} catch (Exception e) {
				logger.error("Could not read current composition", e);
			}
		}

		stateManager.notifyClients();

		saveSession();
	}

	public void close() {
		logger.info("Close...");

		if (dmxManager != null) {
			try {
				dmxManager.close();
			} catch (Exception e) {
				logger.error("Could not close the DMX manager", e);
			}
		}

		try {
			player.close();
		} catch (Exception e) {
			logger.error("Could not close the player", e);
		}

		if (connectMidiOutDeviceTimer != null) {
			connectMidiOutDeviceTimer.cancel();
		}

		if (midiInDeviceReceiver != null) {
			try {
				midiInDeviceReceiver.close();
			} catch (Exception e) {
				logger.error("Could not close MIDI in device receiver", e);
			}
		}

		if (midiOutDevice != null && midiOutDevice.isOpen()) {
			try {
				midiOutDevice.close();
			} catch (Exception e) {
				logger.error("Could not close MIDI out device", e);
			}
		}

		if (currentSet != null) {
			try {
				currentSet.close();
			} catch (Exception e) {
				logger.error("Could not close current set list", e);
			}
		}

		try {
			stopDefaultComposition();
		} catch (Exception e) {
			logger.error("Could not stop the default composition", e);
		}

		try {
			raspberryGpioControlActionExecuter.close();
			;
		} catch (Exception e) {
			logger.error("Could not close the Raspberry GPIO controller", e);
		}

		logger.info("Finished closing");
	}

	public void reboot() throws Exception {
		for (RemoteDevice remoteDevice : settings.getRemoteDeviceList()) {
			if (remoteDevice.isSynchronize()) {
				remoteDevice.reboot();
			}
		}

		ShellManager shellManager = new ShellManager(new String[] { "sudo", "reboot" });
		shellManager.getProcess().waitFor();
	}

	public Midi2DmxConverter getMidi2DmxConverter() {
		return midi2DmxConverter;
	}

	public Set getCurrentSet() {
		return currentSet;
	}

	public void setCurrentSet(Set currentSet) {
		this.currentSet = currentSet;
		saveSession();
	}

	public Settings getSettings() {
		return settings;
	}

	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	public DmxManager getDmxManager() {
		return dmxManager;
	}

	public void setDmxManager(DmxManager dmxManager) {
		this.dmxManager = dmxManager;
	}

	public MidiControlActionExecuter getMidi2ActionConverter() {
		return midi2ActionConverter;
	}

	public CompositionManager getCompositionManager() {
		return compositionManager;
	}

	public Updater getUpdater() {
		return updater;
	}

	public Session getSession() {
		return session;
	}

	public StateManager getStateManager() {
		return stateManager;
	}

	public FileManager getFileManager() {
		return fileManager;
	}

	public void setFileManager(FileManager fileManager) {
		this.fileManager = fileManager;
	}

	public MidiInDeviceReceiver getMidiInDeviceReceiver() {
		return midiInDeviceReceiver;
	}

	public Player getPlayer() {
		return player;
	}

	public void setPlayer(Player player) {
		this.player = player;
	}

	public ImageDisplayer getImageDisplayer() {
		return imageDisplayer;
	}

	public boolean isInitializing() {
		return isInitializing;
	}

	public ControlActionExecuter getControlActionExecuter() {
		return controlActionExecuter;
	}

	public void setControlActionExecuter(ControlActionExecuter controlActionExecuter) {
		this.controlActionExecuter = controlActionExecuter;
	}

}
