import { RaspberryGpioControl } from "./raspberry-gpio-control";
import { Instrument } from "./instrument";
import { MidiRouting } from "./midi-routing";
import { AudioBus } from "./audio-bus";
import { MidiControl } from "./midi-control";
import { MidiDevice } from "./midi-device";
import { RemoteDevice } from "./remote-device";
import { MidiMapping } from "./midi-mapping";
import { OlaPlugin } from "./ola-plugin";

export class Settings {
  version: number;
  midiInDevice: MidiDevice;
  midiOutDevice: MidiDevice;
  remoteDeviceList: RemoteDevice[];
  deviceInMidiRoutingList: MidiRouting[];
  remoteMidiRoutingList: MidiRouting[];

  // TODO remove
  midiControlList: MidiControl[];

  midiMapping: MidiMapping;
  raspberryGpioControlList: RaspberryGpioControl[];
  raspberryGpioOutputPinBcmList: number[];
  lightingSendDelayMillis: number;
  lightingOlaPluginList: OlaPlugin[] = [];
  defaultComposition: string;
  offsetMillisMidi: number;
  offsetMillisAudio: number;
  offsetMillisVideo: number;
  loggingLevel: string;
  language: string;
  deviceName: string;
  resetUsbAfterBoot: boolean;
  audioOutput: string;
  audioRate: number;
  alsaBufferSize: number;
  alsaPeriodSize: number;
  alsaPeriodTime: number;
  audioBusList: AudioBus[];
  videoWidth: number;
  videoHeight: number;
  customVideoResolution: boolean;
  wlanApEnable: boolean;
  wlanApSsid: string;
  wlanApPassphrase: string;
  wlanApSsidHide: boolean;
  wlanApHwMode: string;
  wlanApChannel: number;
  wlanApCountryCode: string;
  enableRaspberryGpio: boolean;
  instrumentList: Instrument[] = [];
  enableMonitor: boolean;
  designerFrequencyHertz: number;
  designerLivePreview: boolean;
  updateTestBranch: boolean;
  readyToUseVersion: number;

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
        this.raspberryGpioControlList.push(
          new RaspberryGpioControl(raspberryGpioControl)
        );
      }
    }

    if (data.raspberryGpioOutputPinBcmList) {
      this.raspberryGpioOutputPinBcmList = [];

      for (let raspberryGpioOutputPinBcm of data.raspberryGpioOutputPinBcmList) {
        this.raspberryGpioOutputPinBcmList.push(raspberryGpioOutputPinBcm);
      }
    }

    this.lightingSendDelayMillis = data.lightingSendDelayMillis;

    if (data.lightingOlaPluginList) {
      this.lightingOlaPluginList = [];

      for (let olaPlugin of data.lightingOlaPluginList) {
        this.lightingOlaPluginList.push(new OlaPlugin(olaPlugin));
      }
    }

    this.defaultComposition = data.defaultComposition;
    this.offsetMillisMidi = data.offsetMillisMidi;
    this.offsetMillisAudio = data.offsetMillisAudio;
    this.offsetMillisVideo = data.offsetMillisVideo;
    this.loggingLevel = data.loggingLevel;
    this.language = data.language;
    this.deviceName = data.deviceName;
    this.resetUsbAfterBoot = data.resetUsbAfterBoot;
    this.audioOutput = data.audioOutput;
    this.audioRate = data.audioRate;
    this.alsaPeriodSize = data.alsaPeriodSize;
    this.alsaBufferSize = data.alsaBufferSize;
    this.alsaPeriodTime = data.alsaPeriodTime;

    if (data.audioBusList) {
      this.audioBusList = [];

      for (let audioBus of data.audioBusList) {
        this.audioBusList.push(new AudioBus(audioBus));
      }
    }

    this.videoWidth = data.videoWidth;
    this.videoHeight = data.videoHeight;
    this.customVideoResolution = data.customVideoResolution;
    this.wlanApEnable = data.wlanApEnable;
    this.wlanApSsid = data.wlanApSsid;
    this.wlanApPassphrase = data.wlanApPassphrase;
    this.wlanApSsidHide = data.wlanApSsidHide;
    this.wlanApHwMode = data.wlanApHwMode;
    this.wlanApChannel = data.wlanApChannel;
    this.wlanApCountryCode = data.wlanApCountryCode;
    this.enableRaspberryGpio = data.enableRaspberryGpio;

    if (data.instrumentList) {
      this.instrumentList = [];

      for (let instrument of data.instrumentList) {
        this.instrumentList.push(new Instrument(instrument));
      }
    }

    this.enableMonitor = data.enableMonitor;
    this.designerFrequencyHertz = data.designerFrequencyHertz;
    this.designerLivePreview = data.designerLivePreview;
    this.updateTestBranch = data.updateTestBranch;
    this.readyToUseVersion = data.readyToUseVersion;
  }
}
