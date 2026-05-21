import { CompositionFile } from "./composition-file";

export class CompositionAudioFile extends CompositionFile {
  outputBusUuid: string;
  volume: number;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    if (data.outputBusUuid) {
      this.outputBusUuid = data.outputBusUuid;
    }

    if (data.volume) {
      this.volume = data.volume;
    }
  }

  toJSON() {
    return { audioFile: { ...this } };
  }
}
