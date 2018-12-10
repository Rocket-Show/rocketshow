import { MidiSignal } from "./midi-signal";

export class ActivityMidi {
    midiSource: string = '';
    midiSignal: MidiSignal;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.midiSource = data.midiSource;
        this.midiSignal = new MidiSignal(data.midiSignal);
    }
}
