import { RaspberryGpioControl } from './raspberry-gpio-control';
import { AudioDevice } from './audio-device';
import { MidiRouting } from './midi-routing';
import { AudioBus } from './audio-bus';
import { MidiControl } from './midi-control';
import { MidiDevice } from "./midi-device";
import { RemoteDevice } from "./remote-device";
import { MidiMapping } from './midi-mapping';

export class Settings {
    midiInDevice: MidiDevice;
    midiOutDevice: MidiDevice;
    remoteDeviceList: RemoteDevice[];
    deviceInMidiRoutingList: MidiRouting[];
    remoteMidiRoutingList: MidiRouting[];
    midiControlList: MidiControl[];
    midiMapping: MidiMapping;
    raspberryGpioControlList: RaspberryGpioControl[];
    dmxSendDelayMillis: number;
    defaultComposition: string;
    offsetMillisMidi: number;
    offsetMillisAudio: number;
    offsetMillisVideo: number;
    audioPlayerType: string;
    loggingLevel: string;
    language: string;
    deviceName: string;
    resetUsbAfterBoot: boolean;
    audioOutput: string;
    audioRate: number;
    audioDevice: AudioDevice;
    audioBusList: AudioBus[];
    wlanApEnable: boolean;
    wlanApSsid: string;
    wlanApPassphrase: string;
    wlanApSsidHide: boolean;
    enableRaspberryGpio: boolean;

    constructor(data?: any) {
        if (!data) {
            return;
        }

        if (data.midiInDevice) {
            this.midiInDevice = new MidiDevice(data.midiInDevice);
        }

        if (data.midiOutDevice) {
            this.midiOutDevice = new MidiDevice(data.midiOutDevice);
        }

        if (data.remoteDeviceList) {
            this.remoteDeviceList = [];

            for (let remoteDevice of data.remoteDeviceList) {
                this.remoteDeviceList.push(new RemoteDevice(remoteDevice));
            }
        }

        if (data.deviceInMidiRoutingList) {
            this.deviceInMidiRoutingList = [];

            for (let midiRouting of data.deviceInMidiRoutingList) {
                this.deviceInMidiRoutingList.push(new MidiRouting(midiRouting));
            }
        }

        if (data.remoteMidiRoutingList) {
            this.remoteMidiRoutingList = [];

            for (let midiRouting of data.remoteMidiRoutingList) {
                this.remoteMidiRoutingList.push(new MidiRouting(midiRouting));
            }
        }

        if (data.midiControlList) {
            this.midiControlList = [];

            for (let midiControl of data.midiControlList) {
                this.midiControlList.push(new MidiControl(midiControl));
            }
        }

        if (data.midiMapping) {
            this.midiMapping = new MidiMapping(data.midiMapping);
        }

        if (data.raspberryGpioControlList) {
            this.raspberryGpioControlList = [];

            for (let raspberryGpioControl of data.raspberryGpioControlList) {
                this.raspberryGpioControlList.push(new RaspberryGpioControl(raspberryGpioControl));
            }
        }

        this.dmxSendDelayMillis = data.dmxSendDelayMillis;
        this.defaultComposition = data.defaultComposition;
        this.offsetMillisMidi = data.offsetMillisMidi;
        this.offsetMillisAudio = data.offsetMillisAudio;
        this.offsetMillisVideo = data.offsetMillisVideo;
        this.audioPlayerType = data.audioPlayerType;
        this.loggingLevel = data.loggingLevel;
        this.language = data.language;
        this.deviceName = data.deviceName;
        this.resetUsbAfterBoot = data.resetUsbAfterBoot;
        this.audioOutput = data.audioOutput;
        this.audioRate = data.audioRate;

        if (data.audioDevice) {
            this.audioDevice = new AudioDevice(data.audioDevice);
        }

        if (data.audioBusList) {
            this.audioBusList = [];

            for (let audioBus of data.audioBusList) {
                this.audioBusList.push(new AudioBus(audioBus));
            }
        }

        this.wlanApEnable = data.wlanApEnable;
        this.wlanApSsid = data.wlanApSsid;
        this.wlanApPassphrase = data.wlanApPassphrase;
        this.wlanApSsidHide = data.wlanApSsidHide;

        this.enableRaspberryGpio = data.enableRaspberryGpio;
    }

}
