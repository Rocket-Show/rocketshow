import { CompositionFile } from "./composition-file";
import { MidiRouting } from "./midi-routing";

export class CompositionMidiFile extends CompositionFile {
  midiRoutingList: MidiRouting[] = [];

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    if (data.midiRoutingList) {
      for (let routing of data.midiRoutingList) {
        this.midiRoutingList.push(new MidiRouting(routing));
      }
    }
  }

  toJSON() {
    return { midiFile: { ...this } };
  }
}
