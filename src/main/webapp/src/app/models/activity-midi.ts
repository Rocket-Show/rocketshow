import { MidiSignal } from "./midi-signal";

export class ActivityMidi {
    midiSignal: MidiSignal;
    midiDirection: string = '';
    midiSources: string[] = [];
    midiDestinations: string[] = [];

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.midiSignal = new MidiSignal(data.midiSignal);
        this.midiDirection = data.midiDirection;

        if(data.midiSources) {
            for(let midiSource of data.midiSources) {
                this.midiSources.push(midiSource);
            }
        }

        if(data.midiDestinations) {
            for(let midiDestination of data.midiDestinations) {
                this.midiDestinations.push(midiDestination);
            }
        }
    }
}
