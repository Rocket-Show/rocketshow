import { CompositionFile } from "./composition-file";

export class CompositionVideoFile extends CompositionFile {
  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }
  }

  toJSON() {
    return { videoFile: { ...this } };
  }
}
