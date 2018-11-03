package com.ascargon.rocketshow;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiUnavailableException;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.midi.MidiControl;
import com.ascargon.rocketshow.midi.MidiDevice;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiRouting;
import com.ascargon.rocketshow.midi.MidiUtil;
import com.ascargon.rocketshow.midi.MidiUtil.MidiDirection;
import com.ascargon.rocketshow.raspberry.RaspberryGpioControl;
import com.ascargon.rocketshow.util.ShellManager;

@XmlRootElement
public class Settings {

	final static Logger logger = Logger.getLogger(Settings.class);

	// Create an own logging enum to save it in the settings xml
	public enum LoggingLevel {
		ERROR, WARN, INFO, DEBUG, TRACE
	}

	// Possible audio outputs
	public enum AudioOutput {
		HEADPHONES, HDMI, DEVICE
	}

	private MidiDevice midiInDevice;
	private MidiDevice midiOutDevice;

	private List<RemoteDevice> remoteDeviceList = new ArrayList<RemoteDevice>();

	private List<MidiControl> midiControlList = new ArrayList<MidiControl>();
	private MidiMapping midiMapping;

	private List<RaspberryGpioControl> raspberryGpioControlList = new ArrayList<RaspberryGpioControl>();

	private int dmxSendDelayMillis;

	// Global play offset on file types
	private int offsetMillisMidi;
	private int offsetMillisAudio;
	private int offsetMillisVideo;

	private List<MidiRouting> deviceInMidiRoutingList = new ArrayList<MidiRouting>();
	private List<MidiRouting> remoteMidiRoutingList = new ArrayList<MidiRouting>();

	private String defaultComposition;

	private LoggingLevel loggingLevel;

	private String language = "en";

	private String deviceName;

	private boolean resetUsbAfterBoot = false;

	private AudioOutput audioOutput;

	private int audioRate;

	private AudioDevice audioDevice;

	private List<AudioBus> audioBusList = new ArrayList<AudioBus>();

	private boolean wlanApEnable = true;

	private String wlanApSsid = "Rocket Show";

	private String wlanApPassphrase = "";

	private boolean wlanApSsidHide = false;

	private boolean enableRaspberryGpio;

	public Settings() {
		// Initialize default settings

		midiInDevice = new MidiDevice();
		midiOutDevice = new MidiDevice();

		// Global MIDI mapping
		midiMapping = new MidiMapping();

		try {
			List<MidiDevice> midiInDeviceList;
			midiInDeviceList = MidiUtil.getMidiDevices(MidiDirection.IN);
			if (midiInDeviceList.size() > 0) {
				midiInDevice = midiInDeviceList.get(0);
			}
		} catch (MidiUnavailableException e) {
			logger.error("Could not get any MIDI IN devices");
			logger.error(e.getStackTrace());
		}

		try {
			List<MidiDevice> midiOutDeviceList;
			midiOutDeviceList = MidiUtil.getMidiDevices(MidiDirection.OUT);
			if (midiOutDeviceList.size() > 0) {
				midiOutDevice = midiOutDeviceList.get(0);
			}
		} catch (MidiUnavailableException e) {
			logger.error("Could not get any MIDI OUT devices");
			logger.error(e.getStackTrace());
		}

		dmxSendDelayMillis = 10;

		offsetMillisMidi = 0;
		offsetMillisAudio = 0;
		offsetMillisVideo = 0;

		audioOutput = AudioOutput.HEADPHONES;
		audioRate = 44100 /* or 48000 */;

		loggingLevel = LoggingLevel.INFO;

		enableRaspberryGpio = true;

		updateSystem();
	}

	public RemoteDevice getRemoteDeviceByName(String name) {
		for (RemoteDevice remoteDevice : remoteDeviceList) {
			if (remoteDevice.getName().equals(name)) {
				return remoteDevice;
			}
		}

		return null;
	}

	private void setSystemAudioOutput(int id) throws Exception {
		ShellManager shellManager = new ShellManager(new String[] { "amixer", "cset", "numid=3", String.valueOf(id) });
		shellManager.getProcess().waitFor();
	}

	private int getTotalAudioChannels() {
		int total = 0;

		for (AudioBus audioBus : audioBusList) {
			total += audioBus.getChannels();
		}

		return total;
	}

	private String getBusNameFromId(int id) {
		return "bus" + (id + 1);
	}

	public String getAlsaDeviceFromOutputBus(String outputBus) {
		logger.debug("Find ALSA device for bus name '" + outputBus + "'...");

		// Get an alsa device name from a bus name
		for (int i = 0; i < audioBusList.size(); i++) {
			AudioBus audioBus = audioBusList.get(i);

			logger.debug("Got bus '" + audioBus.getName() + "'");

			if (outputBus != null && outputBus.equals(audioBus.getName())) {
				logger.debug("Found device '" + getBusNameFromId(i) + "'");

				return getBusNameFromId(i);
			}
		}

		// Return a default bus, if none is found
		if (audioBusList.size() > 0) {
			return getBusNameFromId(0);
		}

		return "";
	}

	private String getAlsaSettings() {
		// Generate the ALSA settings
		String settings = "";
		int currentChannel = 0;

		if (audioDevice == null) {
			// We got no audio device
			return "";
		}

		// Build the general device settings
		// @formatter:off
		settings += 
				"pcm.dshare {\n" +
				"  type dmix\n" +
				"  ipc_key 2048\n" +
				"  slave {\n" +
				"    pcm \"hw:" + audioDevice.getKey() + "\"\n" +
				"    rate " + audioRate + "\n" +
				"    channels " + getTotalAudioChannels() + "\n" +
				"  }\n"+
				"  bindings {\n";
		
		// Add all channels
		for (int i = 0; i < getTotalAudioChannels(); i++) {
			settings += 
					"    " + i + " " + i + "\n";
		}
		
		settings += 
				"  }\n" +
				"}\n";
		
		// List each bus
		for (int i = 0; i < audioBusList.size(); i++) {
			AudioBus audioBus = audioBusList.get(i);
			
			settings += "\n" +
					"pcm." + getBusNameFromId(i) + " {\n" +
					"  type plug\n" +
					"  slave {\n" +
					"    pcm \"dshare\"\n" +
					"    channels " + getTotalAudioChannels() + "\n" +
					"  }\n";
			
			// Add each channel to the bus
			for (int j = 0; j < audioBus.getChannels(); j++) {
				settings += "  ttable." + j + "." + currentChannel + " 1\n";
				
				currentChannel ++;
			}
					
			settings += 	"}\n";	
		}
		// @formatter:on

		return settings;
	}

	private void updateAudioSystem() throws Exception {
		if (audioOutput == AudioOutput.HEADPHONES) {
			setSystemAudioOutput(1);
		} else if (audioOutput == AudioOutput.HDMI) {
			setSystemAudioOutput(2);
		} else if (audioOutput == AudioOutput.DEVICE) {
			// Write the audio settings to /home/.asoundrc and use ALSA to
			// output audio on the selected device name
			File alsaSettings = new File("/home/rocketshow/.asoundrc");

			try {
				FileWriter fileWriter = new FileWriter(alsaSettings, false);
				fileWriter.write(getAlsaSettings());
				fileWriter.close();
			} catch (IOException e) {
				logger.error("Could not write .asoundrc", e);
			}
		}
	}

	private void updateLoggingLevel() {
		// Set the proper logging level (map from the log4j enum to our own
		// enum)
		switch (loggingLevel) {
		case INFO:
			LogManager.getRootLogger().setLevel(Level.INFO);
			break;
		case WARN:
			LogManager.getRootLogger().setLevel(Level.WARN);
			break;
		case ERROR:
			LogManager.getRootLogger().setLevel(Level.ERROR);
			break;
		case DEBUG:
			LogManager.getRootLogger().setLevel(Level.DEBUG);
			break;
		case TRACE:
			LogManager.getRootLogger().setLevel(Level.TRACE);
			break;
		}
	}

	private void updateWlanAp() {
		String apConfig = "";
		String statusCommand = "";

		// Update the access point configuration
		apConfig += "interface=wlan0\n";
		apConfig += "driver=nl80211\n";
		apConfig += "ssid=" + wlanApSsid + "\n";
		apConfig += "utf8_ssid=1\n";
		apConfig += "hw_mode=g\n";
		apConfig += "channel=7\n";
		apConfig += "wmm_enabled=0\n";
		apConfig += "macaddr_acl=0\n";
		apConfig += "auth_algs=1\n";

		if (wlanApSsidHide) {
			apConfig += "ignore_broadcast_ssid=1\n";
		} else {
			apConfig += "ignore_broadcast_ssid=0\n";
		}

		if (wlanApPassphrase != null && wlanApPassphrase.length() >= 8) {
			apConfig += "wpa=2\n";
			apConfig += "wpa_passphrase=" + wlanApPassphrase + "\n";
		}

		apConfig += "wpa_key_mgmt=WPA-PSK\n";
		apConfig += "wpa_pairwise=TKIP\n";
		apConfig += "rsn_pairwise=CCMP\n";

		try {
			FileWriter fileWriter = new FileWriter("/etc/hostapd/hostapd.conf", false);
			fileWriter.write(apConfig);
			fileWriter.close();
		} catch (IOException e) {
			logger.error("Could not write /etc/hostapd/hostapd.conf", e);
		}

		// Activate/deactivate the access point completely
		if (wlanApEnable) {
			statusCommand = "enable";
		} else {
			statusCommand = "disable";
		}

		try {
			new ShellManager(new String[] { "sudo", "systemctl", statusCommand, "hostapd" });
		} catch (IOException e) {
			logger.error("Could not update the access point status with '" + statusCommand + "'", e);
		}
	}

	public void updateSystem() {
		// Update all system settings

		try {
			updateAudioSystem();
		} catch (Exception e) {
			logger.error("Could not update the audio system settings", e);
		}

		try {
			updateLoggingLevel();
		} catch (Exception e) {
			logger.error("Could not update the logging level system settings", e);
		}

		try {
			updateWlanAp();
		} catch (Exception e) {
			logger.error("Could not update the wireless access point settings", e);
		}
	}

	@XmlElement
	public MidiDevice getMidiInDevice() {
		return midiInDevice;
	}

	public void setMidiInDevice(MidiDevice midiInDevice) {
		this.midiInDevice = midiInDevice;
	}

	@XmlElement
	public MidiDevice getMidiOutDevice() {
		return midiOutDevice;
	}

	public void setMidiOutDevice(MidiDevice midiOutDevice) {
		this.midiOutDevice = midiOutDevice;
	}

	@XmlElement
	public int getDmxSendDelayMillis() {
		return dmxSendDelayMillis;
	}

	public void setDmxSendDelayMillis(int dmxSendDelayMillis) {
		this.dmxSendDelayMillis = dmxSendDelayMillis;
	}

	@XmlElement(name = "remoteDevice")
	@XmlElementWrapper(name = "remoteDeviceList")
	public List<RemoteDevice> getRemoteDeviceList() {
		return remoteDeviceList;
	}

	public void setRemoteDeviceList(List<RemoteDevice> remoteDeviceList) {
		this.remoteDeviceList = remoteDeviceList;
	}

	@XmlElement
	public MidiMapping getMidiMapping() {
		return midiMapping;
	}

	public void setMidiMapping(MidiMapping midiMapping) {
		this.midiMapping = midiMapping;
	}

	@XmlElement(name = "deviceInMidiRouting")
	@XmlElementWrapper(name = "deviceInMidiRoutingList")
	public List<MidiRouting> getDeviceInMidiRoutingList() {
		return deviceInMidiRoutingList;
	}

	public void setDeviceInMidiRoutingList(List<MidiRouting> deviceInMidiRoutingList) {
		for (MidiRouting deviceInMidiRouting : deviceInMidiRoutingList) {
			deviceInMidiRouting.setMidiSource("input MIDI device");
		}
		this.deviceInMidiRoutingList = deviceInMidiRoutingList;
	}

	@XmlElement(name = "remoteMidiRouting")
	@XmlElementWrapper(name = "remoteMidiRoutingList")
	public List<MidiRouting> getRemoteMidiRoutingList() {
		return remoteMidiRoutingList;
	}

	public void setRemoteMidiRoutingList(List<MidiRouting> remoteMidiRoutingList) {
		for (MidiRouting remoteMidiRouting : remoteMidiRoutingList) {
			remoteMidiRouting.setMidiSource("remote MIDI");
		}
		this.remoteMidiRoutingList = remoteMidiRoutingList;
	}

	@XmlElement(name = "midiControl")
	@XmlElementWrapper(name = "midiControlList")
	public List<MidiControl> getMidiControlList() {
		return midiControlList;
	}

	public void setMidiControlList(List<MidiControl> midiControlList) {
		this.midiControlList = midiControlList;
	}

	@XmlElement(name = "raspberryGpioControl")
	@XmlElementWrapper(name = "raspberryGpioControlList")
	public List<RaspberryGpioControl> getRaspberryGpioControlList() {
		return raspberryGpioControlList;
	}

	public void setRaspberryGpioControlList(List<RaspberryGpioControl> raspberryGpioControlList) {
		this.raspberryGpioControlList = raspberryGpioControlList;
	}

	@XmlElement
	public String getDefaultComposition() {
		return defaultComposition;
	}

	public void setDefaultComposition(String defaultComposition) {
		this.defaultComposition = defaultComposition;
	}

	@XmlElement
	public int getOffsetMillisMidi() {
		return offsetMillisMidi;
	}

	public void setOffsetMillisMidi(int offsetMillisMidi) {
		this.offsetMillisMidi = offsetMillisMidi;
	}

	@XmlElement
	public int getOffsetMillisAudio() {
		return offsetMillisAudio;
	}

	public void setOffsetMillisAudio(int offsetMillisAudio) {
		this.offsetMillisAudio = offsetMillisAudio;
	}

	@XmlElement
	public int getOffsetMillisVideo() {
		return offsetMillisVideo;
	}

	public void setOffsetMillisVideo(int offsetMillisVideo) {
		this.offsetMillisVideo = offsetMillisVideo;
	}

	@XmlElement
	public LoggingLevel getLoggingLevel() {
		return loggingLevel;
	}

	public void setLoggingLevel(LoggingLevel loggingLevel) {
		this.loggingLevel = loggingLevel;
	}

	@XmlElement
	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	@XmlElement
	public String getDeviceName() {
		return deviceName;
	}

	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}

	@XmlElement
	public boolean isResetUsbAfterBoot() {
		return resetUsbAfterBoot;
	}

	public void setResetUsbAfterBoot(boolean resetUsbAfterBoot) {
		this.resetUsbAfterBoot = resetUsbAfterBoot;
	}

	@XmlElement
	public AudioOutput getAudioOutput() {
		return audioOutput;
	}

	public void setAudioOutput(AudioOutput audioOutput) {
		this.audioOutput = audioOutput;
	}

	@XmlElement(name = "audioBus")
	@XmlElementWrapper(name = "audioBusList")
	public List<AudioBus> getAudioBusList() {
		return audioBusList;
	}

	public void setAudioBusList(List<AudioBus> audioBusList) {
		this.audioBusList = audioBusList;
	}

	public int getAudioRate() {
		return audioRate;
	}

	@XmlElement
	public void setAudioRate(int audioRate) {
		this.audioRate = audioRate;
	}

	@XmlElement
	public AudioDevice getAudioDevice() {
		return audioDevice;
	}

	public void setAudioDevice(AudioDevice audioDevice) {
		this.audioDevice = audioDevice;
	}

	@XmlElement
	public boolean isWlanApEnable() {
		return wlanApEnable;
	}

	public void setWlanApEnable(boolean wlanApEnable) {
		this.wlanApEnable = wlanApEnable;
	}

	@XmlElement
	public String getWlanApSsid() {
		return wlanApSsid;
	}

	public void setWlanApSsid(String wlanApSsid) {
		this.wlanApSsid = wlanApSsid;
	}

	@XmlElement
	public String getWlanApPassphrase() {
		return wlanApPassphrase;
	}

	public void setWlanApPassphrase(String wlanApPassphrase) {
		this.wlanApPassphrase = wlanApPassphrase;
	}

	@XmlElement
	public boolean isWlanApSsidHide() {
		return wlanApSsidHide;
	}

	public void setWlanApSsidHide(boolean wlanApSsidHide) {
		this.wlanApSsidHide = wlanApSsidHide;
	}

	@XmlElement
	public boolean isEnableRaspberryGpio() {
		return enableRaspberryGpio;
	}

	public void setEnableRaspberryGpio(boolean enableRaspberryGpio) {
		this.enableRaspberryGpio = enableRaspberryGpio;
	}

}
