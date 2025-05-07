import { Action } from "./action";
import { MidiSignal } from "./midi-signal";

export class ActionMidi extends Action {
  midiSignal: MidiSignal;

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.midiSignal = new MidiSignal(data.midiSignal);

    if (this.midiSignal.command == 0) {
      this.midiSignal.command = 144;
      this.midiSignal.channel = 0;
      this.midiSignal.data1 = 60;
      this.midiSignal.data2 = 99;
    }
  }

  toJSON() {
    return { actionMidi: { ...this } };
  }
}
