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

    private String basePath;

    private MidiDevice midiInDevice;
    private MidiDevice midiOutDevice;

    private List<RemoteDevice> remoteDeviceList = new ArrayList<>();

    private List<MidiControl> midiControlList = new ArrayList<>();
    private MidiMapping midiMapping;

    private List<RaspberryGpioControl> raspberryGpioControlList = new ArrayList<>();

    private int dmxSendDelayMillis;

    // Global play offset on file types
    private int offsetMillisMidi;
    private int offsetMillisAudio;
    private int offsetMillisVideo;

    private List<MidiRouting> deviceInMidiRoutingList = new ArrayList<>();
    private List<MidiRouting> remoteMidiRoutingList = new ArrayList<>();

    private String defaultComposition;

    private FileSettingsService.LoggingLevel loggingLevel;

    private String language = "en";

    private String deviceName;

    private boolean resetUsbAfterBoot = false;

    private FileSettingsService.AudioOutput audioOutput;

    private int audioRate;

    private AudioDevice audioDevice;

    private List<AudioBus> audioBusList = new ArrayList<>();

    private boolean wlanApEnable = true;

    private String wlanApSsid = "Rocket Show";

    private String wlanApPassphrase = "";

    private boolean wlanApSsidHide = false;

    private boolean enableRaspberryGpio;

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
        this.deviceInMidiRoutingList = deviceInMidiRoutingList;
    }

    @XmlElement(name = "remoteMidiRouting")
    @XmlElementWrapper(name = "remoteMidiRoutingList")
    public List<MidiRouting> getRemoteMidiRoutingList() {
        return remoteMidiRoutingList;
    }

    public void setRemoteMidiRoutingList(List<MidiRouting> remoteMidiRoutingList) {
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
    public FileSettingsService.LoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(FileSettingsService.LoggingLevel loggingLevel) {
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
    public FileSettingsService.AudioOutput getAudioOutput() {
        return audioOutput;
    }

    public void setAudioOutput(FileSettingsService.AudioOutput audioOutput) {
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

    @XmlElement
    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

}
