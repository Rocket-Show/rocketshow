package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.lighting.OlaPlugin;
import com.ascargon.rocketshow.midi.*;
import com.ascargon.rocketshow.raspberry.RaspberryGpioActionTrigger;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement
@Setter
@Getter
public class Settings {

    // Create an own logging enum to save it in the settings xml
    public enum LoggingLevel {
        ERROR, WARN, INFO, DEBUG, TRACE
    }

    // Possible audio outputs
    public enum AudioOutput {
        DEFAULT, HEADPHONES, HDMI, DEVICE
    }

    private Integer version;
    private String basePath;
    private String mediaPath;
    private String midiPath;
    private String audioPath;
    private String videoPath;
    private String fixturePath;
    private String designerPath;
    private String leadSheetPath;
    private MidiDevice midiInDevice;
    private MidiDevice midiOutDevice;
    private List<RemoteDevice> remoteDeviceList = new ArrayList<>();

    /**
     * @deprecated Not used since settings version 3 anymore. Use midiActionTriggerInstead.
     */
    @Deprecated
    private List<MidiControl> midiControlList = new ArrayList<>();

    // Execute actions based on received MIDI events
    private List<MidiActionTrigger> midiActionTriggerList = new ArrayList<>();

    private MidiMapping midiMapping;

    // Execute actions based on pressed buttons, connected to GPIO pins
    private List<RaspberryGpioActionTrigger> raspberryGpioActionTriggerList = new ArrayList<>();

    // All configured GPIO pins to be able to send commands to (high/low)
    private List<Integer> raspberryGpioOutputPinBcmList = new ArrayList<>();

    // Used to collect all lighting events within a certain amount of time and send them alltogether, not
    // one by one as soon as they occur, for performance reasons. The higher, the more events will be "merged",
    // the lower, the more CPU it needs.
    // To set a delay on lighting events coming from MIDI, use offsetMillisMidi (or the offset on the composition file).
    private Integer lightingSendDelayMillis;

    // The active OLA plugin
    private List<OlaPlugin> lightingOlaPluginList = new ArrayList<>();

    // Global play offset on file types
    private Integer offsetMillisMidi;
    private Integer offsetMillisAudio;
    private Integer offsetMillisVideo;

    private List<MidiRouting> deviceInMidiRoutingList = new ArrayList<>();
    private List<MidiRouting> remoteMidiRoutingList = new ArrayList<>();
    private String defaultComposition;
    private LoggingLevel loggingLevel;
    private String language = "en";
    private String deviceName;
    private boolean resetUsbAfterBoot = false;
    private AudioOutput audioOutput;

    /**
     * @deprecated Not used since settings version 2 anymore. The device is set in the audiobus instead.
     */
    @Deprecated
    private AudioDevice audioDevice;

    private Integer audioRate;
    private Integer alsaPeriodSize;
    private Integer alsaBufferSize;
    private Integer alsaPeriodTime;
    private List<AudioBus> audioBusList = new ArrayList<>();
    private Integer videoWidth;
    private Integer videoHeight;
    private Boolean customVideoResolution;
    private Boolean wlanApEnable;
    private String wlanApSsid = "Rocket Show";
    private String wlanApPassphrase = "";
    private boolean wlanApSsidHide = false;
    private String wlanApHwMode;
    private Integer wlanApChannel;
    private String wlanApCountryCode;
    private Boolean enableRaspberryGpio = false;
    private Long raspberryGpioDebounceMillis = 3L;

    /**
     * @deprecated Was never really in use
     */
    @Deprecated
    private boolean raspberryGpioNoHardwareTrigger = false;

    /**
     * @deprecated Was never really in use
     */
    @Deprecated
    private int raspberryGpioTimerPeriodMillis = 2;

    /**
     * @deprecated Was never really in use
     */
    @Deprecated
    private int raspberryGpioCyclesHigh = 3;

    private Boolean enableMonitor;
    private Integer designerFrequencyHertz;
    private Boolean designerLivePreview = false;
    private Boolean updateTestBranch = false;

    private List<Instrument> instrumentList = new ArrayList<>();

    // Only filled, if it's a ready to use version
    private Integer readyToUseVersion;

    @XmlElement(name = "remoteDevice")
    @XmlElementWrapper(name = "remoteDeviceList")
    public List<RemoteDevice> getRemoteDeviceList() {
        return remoteDeviceList;
    }

    @XmlElement(name = "deviceInMidiRouting")
    @XmlElementWrapper(name = "deviceInMidiRoutingList")
    public List<MidiRouting> getDeviceInMidiRoutingList() {
        return deviceInMidiRoutingList;
    }

    @XmlElement(name = "remoteMidiRouting")
    @XmlElementWrapper(name = "remoteMidiRoutingList")
    public List<MidiRouting> getRemoteMidiRoutingList() {
        return remoteMidiRoutingList;
    }

    @XmlElement(name = "midiControl")
    @XmlElementWrapper(name = "midiControlList")
    public List<MidiControl> getMidiControlList() {
        return midiControlList;
    }

    @XmlElement(name = "raspberryGpioOutputPinBcm")
    @XmlElementWrapper(name = "raspberryGpioOutputPinBcmList")
    public List<Integer> getRaspberryGpioOutputPinBcmList() {
        return raspberryGpioOutputPinBcmList;
    }

    @XmlElement(name = "audioBus")
    @XmlElementWrapper(name = "audioBusList")
    public List<AudioBus> getAudioBusList() {
        return audioBusList;
    }

    @XmlElement(name = "instrument")
    @XmlElementWrapper(name = "instrumentList")
    public List<Instrument> getInstrumentList() {
        return instrumentList;
    }

    @XmlElement(name = "raspberryGpioActionTrigger")
    @XmlElementWrapper(name = "raspberryGpioActionTriggerList")
    public List<RaspberryGpioActionTrigger> getRaspberryGpioActionTriggerList() {
        return raspberryGpioActionTriggerList;
    }

    @XmlElement(name = "midiActionTrigger")
    @XmlElementWrapper(name = "midiActionTriggerList")
    public List<MidiActionTrigger> getMidiActionTriggerList() {
        return midiActionTriggerList;
    }


}