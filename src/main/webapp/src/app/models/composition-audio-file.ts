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

    // Also a muted file (0)
    if (data.volume != null) {
      this.volume = data.volume;
    }
  }

  toJSON() {
    return { audioFile: { ...this } };
  }
}
