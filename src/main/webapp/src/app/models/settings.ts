import { ActionTriggerRaspberryGpio } from "./action-trigger-raspberry-gpio";
import { Instrument } from "./instrument";
import { MidiRouting } from "./midi-routing";
import { AudioBus } from "./audio-bus";
import { MidiDevice } from "./midi-device";
import { RemoteDevice } from "./remote-device";
import { MidiMapping } from "./midi-mapping";
import { OlaPlugin } from "./ola-plugin";
import { ActionTriggerMidi } from "./action-trigger-midi";
import { ActionTriggerMidiNoteOn } from "./action-trigger-midi-note-on";
import { ActionTriggerMidiProgramChange } from "./action-trigger-midi-program-change";
import { ApiKey } from "./api-key";
import { LightingUniverse } from "./lighting-universe";

export class Settings {
  version: number;
  midiInDevice: MidiDevice;
  midiOutDevice: MidiDevice;
  midiTimecodeEnabled: boolean;
  midiTimecodeFrameRate: string;
  remoteDeviceList: RemoteDevice[];
  deviceInMidiRoutingList: MidiRouting[];
  remoteMidiRoutingList: MidiRouting[];
  actionTriggerMidiList: ActionTriggerMidi[] = [];
  midiMapping: MidiMapping;
  actionTriggerRaspberryGpioList: ActionTriggerRaspberryGpio[];
  raspberryGpioOutputPinBcmList: number[];
  lightingSendDelayMillis: number;
  lightingOlaPluginList: OlaPlugin[] = [];
  lightingUniverseList: LightingUniverse[] = [];
  defaultComposition: string;
  offsetMillisMidi: number;
  offsetMillisAudio: number;
  offsetMillisVideo: number;
  loggingLevel: string;
  language: string;
  deviceName: string;
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
  apiKeyList: ApiKey[] = [];
  tlsEnable: boolean = false;

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

    this.midiTimecodeEnabled = data.midiTimecodeEnabled;
    this.midiTimecodeFrameRate = data.midiTimecodeFrameRate;

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

    if (data.actionTriggerMidiList) {
      this.actionTriggerMidiList = [];

      for (let actionTriggerMidi of data.actionTriggerMidiList) {
        this.actionTriggerMidiList.push(
          Settings.createActionTriggerMidi(actionTriggerMidi)
        );
      }
    }

    if (data.midiMapping) {
      this.midiMapping = new MidiMapping(data.midiMapping);
    }

    if (data.actionTriggerRaspberryGpioList) {
      this.actionTriggerRaspberryGpioList = [];

      for (let actionTriggerRaspberryGpio of data.actionTriggerRaspberryGpioList) {
        this.actionTriggerRaspberryGpioList.push(
          new ActionTriggerRaspberryGpio(actionTriggerRaspberryGpio)
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

    if (data.lightingUniverseList) {
      this.lightingUniverseList = [];

      for (let lightingUniverse of data.lightingUniverseList) {
        this.lightingUniverseList.push(
          new LightingUniverse(lightingUniverse)
        );
      }
    }

    this.defaultComposition = data.defaultComposition;
    this.offsetMillisMidi = data.offsetMillisMidi;
    this.offsetMillisAudio = data.offsetMillisAudio;
    this.offsetMillisVideo = data.offsetMillisVideo;
    this.loggingLevel = data.loggingLevel;
    this.language = data.language;
    this.deviceName = data.deviceName;
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

    if (data.apiKeyList) {
      this.apiKeyList = [];

      for (let apiKey of data.apiKeyList) {
        this.apiKeyList.push(new ApiKey(apiKey));
      }
    }

    this.tlsEnable = data.tlsEnable;
  }

  public static createActionTriggerMidi(
    actionTriggerMidi: any
  ): ActionTriggerMidi {
    let trigger: ActionTriggerMidi;
    if (actionTriggerMidi.actionTriggerMidiNoteOn) {
      trigger = new ActionTriggerMidiNoteOn(
        actionTriggerMidi.actionTriggerMidiNoteOn
      );
    } else if (actionTriggerMidi.actionTriggerMidiProgramChange) {
      trigger = new ActionTriggerMidiProgramChange(
        actionTriggerMidi.actionTriggerMidiProgramChange
      );
    }
    return trigger;
  }
}
