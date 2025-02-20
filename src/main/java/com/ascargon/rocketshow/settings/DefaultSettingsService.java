package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.RocketShowApplication;
import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.lighting.OlaPlugin;
import com.ascargon.rocketshow.midi.MidiDevice;
import com.ascargon.rocketshow.midi.MidiDirection;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiService;
import com.ascargon.rocketshow.raspberry.RaspberryResetUsbService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiUnavailableException;
import java.io.*;
import java.util.List;
import java.util.UUID;

@Service
public class DefaultSettingsService implements SettingsService {

    private final static Logger logger = LoggerFactory.getLogger(Settings.class);

    private final int CURRENT_SETTINGS_VERSION = 2;
    private final String FILE_NAME = "settings";

    private final OperatingSystemInformationService operatingSystemInformationService;
    private final RaspberryResetUsbService raspberryResetUsbService;
    private final MidiService midiService;

    private Settings settings;

    private final ApplicationHome applicationHome = new ApplicationHome(RocketShowApplication.class);

    public DefaultSettingsService(RaspberryResetUsbService raspberryResetUsbService, OperatingSystemInformationService operatingSystemInformationService, MidiService midiService) {
        this.operatingSystemInformationService = operatingSystemInformationService;
        this.raspberryResetUsbService = raspberryResetUsbService;
        this.midiService = midiService;

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

    private void initDefaultSettings() {
        // Initialize default settings

        if (settings == null) {
            settings = new Settings();
            settings.setVersion(CURRENT_SETTINGS_VERSION);
        }

        if (settings.getBasePath() == null) {
            settings.setBasePath(applicationHome.getDir().toString() + File.separator);
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
            settings.setMidiInDevice(new MidiDevice());

            try {
                List<MidiDevice> midiInDeviceList;
                midiInDeviceList = midiService.getMidiDevices(MidiDirection.IN);
                if (!midiInDeviceList.isEmpty()) {
                    settings.setMidiInDevice(midiInDeviceList.get(0));
                }
            } catch (MidiUnavailableException e) {
                logger.error("Could not get any MIDI IN devices", e);
            }
        }

        if (settings.getMidiOutDevice() == null) {
            settings.setMidiOutDevice(new MidiDevice());

            try {
                List<MidiDevice> midiOutDeviceList;
                midiOutDeviceList = midiService.getMidiDevices(MidiDirection.OUT);
                if (!midiOutDeviceList.isEmpty()) {
                    settings.setMidiOutDevice(midiOutDeviceList.get(0));
                }
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
        File file = new File(applicationHome.getDir() + File.separator + FILE_NAME + ".xml");
        JAXBContext jaxbContext = JAXBContext.newInstance(Settings.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        // output pretty printed
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        jaxbMarshaller.marshal(settings, file);

        logger.info("Settings saved");
    }

    @Override
    public void load() throws Exception {
        File file = new File(applicationHome.getDir() + File.separator + FILE_NAME + ".xml");

        if (!file.exists() || file.isDirectory()) {
            logger.info("Settings file does not exist");
            return;
        }

        // Restore the session from the file
        JAXBContext jaxbContext = JAXBContext.newInstance(Settings.class);

        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        this.setSettings((Settings) jaxbUnmarshaller.unmarshal(file));

        if (settings.getVersion() == null || settings.getVersion() < CURRENT_SETTINGS_VERSION) {
            migrateFromOldProject();
        } else if (settings.getVersion() > CURRENT_SETTINGS_VERSION) {
            throw new Exception("The settings have been saved with a newer version of Rocket Show and cannot be used with the current one.");
        }

        // Reset the USB interface, if needed
        try {
            if (settings.isResetUsbAfterBoot()) {
                logger.info("Resetting all USB devices");
                raspberryResetUsbService.resetAllInterfaces(settings.getBasePath());
            }
        } catch (Exception e) {
            logger.error("Could not reset the USB devices", e);
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

    public void migrateFromOldProject() throws JAXBException {
        boolean migrated = false;

        if (settings.getVersion() == null) {
            // Migrate from version 1
            this.migrateToVersion2();
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