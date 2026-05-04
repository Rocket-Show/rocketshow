package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.RocketShowApplication;
import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.lighting.OlaPlugin;
import com.ascargon.rocketshow.midi.*;
import com.ascargon.rocketshow.util.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiUnavailableException;
import java.io.File;
import java.util.UUID;

@Service
public class DefaultSettingsService implements SettingsService {

    private final static Logger logger = LoggerFactory.getLogger(Settings.class);

    private final int CURRENT_SETTINGS_VERSION = 2;
    private String directory;
    private final String FILE_NAME = "settings";

    private final OperatingSystemInformationService operatingSystemInformationService;
    private final MidiService midiService;
    private final FactoryResetService factoryResetService;
    private final DeviceInformationService deviceInformationService;

    private Settings settings;

    public DefaultSettingsService(
            OperatingSystemInformationService operatingSystemInformationService,
            MidiService midiService,
            FactoryResetService factoryResetService,
            DeviceInformationService deviceInformationService
    ) {
        this.operatingSystemInformationService = operatingSystemInformationService;
        this.midiService = midiService;
        this.factoryResetService = factoryResetService;
        this.deviceInformationService = deviceInformationService;

        directory = new ApplicationHome(RocketShowApplication.class).getDir().toString();
        if (isReadOnlyFileSystem()) {
            directory = "/data/rocketshow";
        }

        try {
            load();
        } catch (Exception e) {
            logger.error("Could not load the settings", e);
        }

        // Apply default settings (if not loaded)
        initDefaultSettings();

        // Save the settings to store migrations, default values, etc.
        try {
            save();
        } catch (JAXBException e) {
            logger.error("Could not save settings", e);
        }
    }

    @Override
    public boolean isReadOnlyFileSystem() {
        // Check, whether Rocket Show runs on the A/B read only filesystem with writable data or not (true)
        return deviceInformationService.getDeviceInformation().isAvailable();
    }

    private MidiDevice getFirstMidiNonSerialDevice(MidiDirection midiDirection) throws MidiUnavailableException {
        // Use the first real MIDI device, if there is one, automatically.
        // Don't use a MIDI serial device automatically, because it could be something that does not work as MIDI device.
        for (MidiDevice midiDevice : midiService.getMidiDevices(midiDirection)) {
            if (!midiDevice.isSerialPort()) {
                return midiDevice;
            }
        }
        return null;
    }

    private void initDefaultSettings() {
        // Initialize default settings

        if (settings == null) {
            settings = new Settings();
            settings.setVersion(CURRENT_SETTINGS_VERSION);
        }

        if (settings.getBasePath() == null) {
            settings.setBasePath(directory + File.separator);
        }

        if (settings.getMediaPath() == null) {
            settings.setMediaPath("media");
        }

        if (settings.getAudioPath() == null) {
            settings.setAudioPath("audio");
        }

        if (settings.getMidiPath() == null) {
            settings.setMidiPath("midi");
        }

        if (settings.getVideoPath() == null) {
            settings.setVideoPath("video");
        }

        if (settings.getFixturePath() == null) {
            settings.setFixturePath("fixtures");
        }

        if (settings.getDesignerPath() == null) {
            settings.setDesignerPath("designer");
        }

        if (settings.getLeadSheetPath() == null) {
            settings.setLeadSheetPath("leadsheet");
        }

        if (settings.getMidiInDevice() == null) {
            try {
                settings.setMidiInDevice(getFirstMidiNonSerialDevice(MidiDirection.IN));
            } catch (MidiUnavailableException e) {
                logger.error("Could not get any MIDI IN devices", e);
            }
        }

        if (settings.getMidiOutDevice() == null) {
            try {
                settings.setMidiInDevice(getFirstMidiNonSerialDevice(MidiDirection.OUT));
            } catch (MidiUnavailableException e) {
                logger.error("Could not get any MIDI OUT devices", e);
            }
        }

        // Add the default audio bus
        if (settings.getAudioBusList().isEmpty()) {
            AudioBus audioBus = new AudioBus();
            audioBus.setName("My audio bus 1");
            audioBus.setChannels(2);
            settings.getAudioBusList().add(audioBus);
        }

//        if (settings.getVideoWidth() == null || settings.getVideoHeight() == null) {
//            settings.setVideoWidth(1920);
//            settings.setVideoHeight(1080);
//            settings.setCustomVideoResolution(false);
//        }

        if (settings.getCustomVideoResolution() == null) {
            settings.setCustomVideoResolution(true);
        }

        // Global MIDI mapping
        if (settings.getMidiMapping() == null) {
            settings.setMidiMapping(new MidiMapping());
        }

        if (settings.getLightingSendDelayMillis() == null) {
            settings.setLightingSendDelayMillis(10);
        }

        if (settings.getOffsetMillisAudio() == null) {
            settings.setOffsetMillisAudio(0);
        }

        if (settings.getOffsetMillisMidi() == null) {
            settings.setOffsetMillisMidi(150);
        }

        if (settings.getOffsetMillisVideo() == null) {
            settings.setOffsetMillisVideo(0);
        }

        if (settings.getAudioOutput() == null) {
            if (OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
                settings.setAudioOutput(Settings.AudioOutput.DEFAULT);
            } else if (OperatingSystemInformation.Type.LINUX.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
                settings.setAudioOutput(Settings.AudioOutput.DEVICE);
            }
        }

        if (settings.getAudioRate() == null) {
            settings.setAudioRate(44100 /* or 48000 */);
        }

        if (settings.getAlsaPeriodSize() == null) {
            settings.setAlsaPeriodSize(16384);
        }

        if (settings.getAlsaBufferSize() == null) {
            settings.setAlsaBufferSize(5);
        }

        if (settings.getLightingOlaPluginList().isEmpty()) {
            OlaPlugin olaPlugin = new OlaPlugin();
            olaPlugin.setId(1);
            olaPlugin.setName("Dummy");
            settings.getLightingOlaPluginList().add(olaPlugin);
        }

        if (settings.getLoggingLevel() == null) {
            settings.setLoggingLevel(Settings.LoggingLevel.INFO);
        }

        if (OperatingSystemInformation.SubType.RASPBERRYOS.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            // Raspbian-specific settings

            if (settings.getEnableRaspberryGpio() == null) {
                settings.setEnableRaspberryGpio(true);
            }

            if (settings.getWlanApEnable() == null) {
                settings.setWlanApEnable(true);
            }
        }

        if (settings.getWlanApHwMode() == null) {
            settings.setWlanApHwMode("g");
        }

        if (settings.getWlanApChannel() == null) {
            settings.setWlanApChannel(7);
        }

        if (settings.getWlanApCountryCode() == null) {
            settings.setWlanApCountryCode("US");
        }

        if (settings.getInstrumentList().isEmpty()) {
            Instrument instrument;

            instrument = new Instrument();
            instrument.setName("Vocals");
            instrument.setUuid(UUID.randomUUID().toString());
            settings.getInstrumentList().add(instrument);

            instrument = new Instrument();
            instrument.setName("Guitar");
            instrument.setUuid(UUID.randomUUID().toString());
            settings.getInstrumentList().add(instrument);

            instrument = new Instrument();
            instrument.setName("Bass");
            instrument.setUuid(UUID.randomUUID().toString());
            settings.getInstrumentList().add(instrument);

            instrument = new Instrument();
            instrument.setName("Horns");
            instrument.setUuid(UUID.randomUUID().toString());
            settings.getInstrumentList().add(instrument);
        }

        if (settings.getEnableMonitor() == null) {
            settings.setEnableMonitor(false);
        }

        if (settings.getDesignerFrequencyHertz() == null) {
            settings.setDesignerFrequencyHertz(40);
        }

        if (settings.getDesignerLivePreview() == null) {
            settings.setDesignerLivePreview(true);
        }

        if (settings.getWlanApCountryCode() == null) {
            if (deviceInformationService.getDeviceInformation().isAvailable()) {
                settings.setWlanApCountryCode(deviceInformationService.getDeviceInformation().getCountry());
            }
        }
    }

    @Override
    public AudioBus getAudioBusByName(String outputBus) {
        if (outputBus == null) {
            if (!settings.getAudioBusList().isEmpty()) {
                return settings.getAudioBusList().get(0);
            } else {
                return null;
            }
        }

        // Get an alsa device name from a bus name
        for (AudioBus audioBus : settings.getAudioBusList()) {
            if (outputBus.equals(audioBus.getName())) {
                return audioBus;
            }
        }

        // Return a default bus, if none is found
        if (!settings.getAudioBusList().isEmpty()) {
            return settings.getAudioBusList().get(0);
        }

        return null;
    }

    @Override
    public RemoteDevice getRemoteDeviceByName(String name) {
        for (RemoteDevice remoteDevice : settings.getRemoteDeviceList()) {
            if (remoteDevice.getName().equals(name)) {
                return remoteDevice;
            }
        }

        return null;
    }

    private String getBusNameFromId(int id) {
        return "bus" + (id + 1);
    }

    @Override
    public String getAlsaDeviceFromOutputBus(String outputBus) {
        // Get an alsa device name from a bus name
        for (int i = 0; i < settings.getAudioBusList().size(); i++) {
            AudioBus audioBus = settings.getAudioBusList().get(i);

            logger.debug("Got bus '" + audioBus.getName() + "'");

            if (outputBus != null && outputBus.equals(audioBus.getName())) {
                logger.debug("Found device '" + getBusNameFromId(i) + "'");

                return getBusNameFromId(i);
            }
        }

        // Return a default bus, if none is found
        if (!settings.getAudioBusList().isEmpty()) {
            return getBusNameFromId(0);
        }

        return "";
    }

    @Override
    public void save() throws JAXBException {
        File file = new File(directory, FILE_NAME + ".xml");

        JAXBContext jaxbContext = JAXBContext.newInstance(Settings.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        jaxbMarshaller.marshal(settings, file);

        logger.info("Settings saved");
    }

    @Override
    public void load() throws Exception {
        File file = new File(directory + File.separator + FILE_NAME + ".xml");

        if (!file.exists() || file.isDirectory()) {
            logger.info("Settings file does not exist");
            return;
        }

        // Restore the settings from the file
        JAXBContext jaxbContext = JAXBContext.newInstance(Settings.class);

        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        this.setSettings((Settings) jaxbUnmarshaller.unmarshal(file));

        if (settings.getVersion() == null || settings.getVersion() < CURRENT_SETTINGS_VERSION) {
            migrateFromOldSettings();
        } else if (settings.getVersion() > CURRENT_SETTINGS_VERSION) {
            throw new Exception("The settings have been saved with a newer version of Rocket Show and cannot be used with the current one.");
        }

        logger.info("Settings loaded");
    }

    private void migrateToVersion2() {
        // move the audio device into all buses
        for (AudioBus audioBus : settings.getAudioBusList()) {
            audioBus.setAudioDevice(settings.getAudioDevice());
        }

        settings.setVersion(2);
    }

    private void migrateToVersion3() {
        // migrate from old control action system
        for (MidiControl midiControl : settings.getMidiControlList()) {
            Action action = null;

            switch (midiControl.getAction()) {
                case PLAY:
                    action = new ActionTransport();
                    ((ActionTransport) action).setTransportActionType(ActionTransport.TransportActionType.PLAY);
                    break;
                case PLAY_AS_SAMPLE:
                    action = new ActionTransport();
                    ((ActionTransport) action).setTransportActionType(ActionTransport.TransportActionType.PLAY_AS_SAMPLE);
                    break;
                case TOGGLE_PLAY:
                    action = new ActionTransport();
                    ((ActionTransport) action).setTransportActionType(ActionTransport.TransportActionType.TOGGLE_PLAY);
                    break;
                case PAUSE:
                    action = new ActionTransport();
                    ((ActionTransport) action).setTransportActionType(ActionTransport.TransportActionType.PAUSE);
                    break;
                case NEXT_COMPOSITION:
                    action = new ActionTransport();
                    ((ActionTransport) action).setTransportActionType(ActionTransport.TransportActionType.NEXT_COMPOSITION);
                    break;
                case PREVIOUS_COMPOSITION:
                    action = new ActionTransport();
                    ((ActionTransport) action).setTransportActionType(ActionTransport.TransportActionType.PREVIOUS_COMPOSITION);
                    break;
                case STOP:
                    action = new ActionTransport();
                    ((ActionTransport) action).setTransportActionType(ActionTransport.TransportActionType.STOP);
                    break;
                case SELECT_COMPOSITION_BY_NAME:
                    action = new ActionTransport();
                    ((ActionTransport) action).setTransportActionType(ActionTransport.TransportActionType.SELECT_COMPOSITION_BY_NAME);
                    ((ActionTransport) action).setCompositionName(midiControl.getCompositionName());
                    break;
                case SELECT_COMPOSITION_BY_NAME_AND_PLAY:
                    action = new ActionTransport();
                    ((ActionTransport) action).setTransportActionType(ActionTransport.TransportActionType.SELECT_COMPOSITION_BY_NAME_AND_PLAY);
                    ((ActionTransport) action).setCompositionName(midiControl.getCompositionName());
                    break;
                case SET_COMPOSITION_INDEX:
                    // Not migrated because not used currently
                    break;
                case REBOOT:
                    action = new ActionSystem();
                    ((ActionSystem) action).setSystemActionType(ActionSystem.SystemActionType.REBOOT);
                    break;
            }

            if (action != null) {
                action.setExecuteLocally(midiControl.isExecuteLocally());
                action.setRemoteDeviceNames(midiControl.getRemoteDeviceNames());

                ActionTriggerMidiNoteOn actionTriggerMidiNoteOn = new ActionTriggerMidiNoteOn();
                actionTriggerMidiNoteOn.setChannel(midiControl.getChannelFrom());
                actionTriggerMidiNoteOn.setNote(midiControl.getNoteFrom());
                actionTriggerMidiNoteOn.getActionList().add(action);
            }
        }

        settings.setVersion(3);
    }

    public void migrateFromOldSettings() throws JAXBException {
        boolean migrated = false;

        if (settings.getVersion() == null) {
            // Migrate from version 1
            this.migrateToVersion2();
            migrated = true;
        }

        if (settings.getVersion() == 2) {
            // Migrate from version 2
            this.migrateToVersion3();
            migrated = true;
        }

        if (migrated) {
            logger.warn("The settings have been migrated from an older version");
        }
    }

    @Override
    public Settings getSettings() {
        return settings;
    }

    @Override
    public void setSettings(Settings settings) {
        this.settings = settings;

        // make sure, settings not available in the interface (e.g. the designer path)
        // are not lost
        this.initDefaultSettings();
    }

}