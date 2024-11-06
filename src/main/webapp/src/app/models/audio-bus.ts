import { AudioDevice } from "./audio-device";

export class AudioBus {
  audioDevice: AudioDevice;
  name: string = "";
  channels: number = 2;

  constructor(data?: any) {
    if (!data) {
      return;
    }

    if (data.audioDevice) {
      this.audioDevice = new AudioDevice(data.audioDevice);
    }

    this.name = data.name;
    this.channels = data.channels;
  }
}
