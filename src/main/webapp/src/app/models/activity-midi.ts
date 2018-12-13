import { MidiSignal } from "./midi-signal";

export class ActivityMidi {
    midiSignal: MidiSignal;
    midiDirection: string = '';
    midiSource: string = '';
    midiDestination: string = '';

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.midiSignal = new MidiSignal(data.midiSignal);
        this.midiDirection = data.midiDirection;
        this.midiSource = data.midiSource;
        this.midiDestination = data.midiDestination;
    }
}
