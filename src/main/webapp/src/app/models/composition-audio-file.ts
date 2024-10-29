import { CompositionFile } from "./composition-file";

export class CompositionAudioFile extends CompositionFile {
  outputBus: string;
  volume: number;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    if (data.outputBus) {
      this.outputBus = data.outputBus;
    }

    if (data.volume) {
      this.volume = data.volume;
    }
  }
}
