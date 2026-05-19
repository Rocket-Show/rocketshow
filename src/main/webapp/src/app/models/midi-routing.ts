import { MidiMapping } from './midi-mapping';

export class MidiRouting {
    midiDestination: string;
    midiMapping: MidiMapping = new MidiMapping();
    universeName: string = "";
    remoteDeviceList: string[] = [];

    constructor(data?: any) {
        if(!data) {
        	return;
        }

        this.midiDestination = data.midiDestination;
        this.midiMapping = new MidiMapping(data.midiMapping);
        this.universeName = data.universeName || "";

        if(data.remoteDeviceList) {
            for(let remoteDevice of data.remoteDeviceList) {
                this.remoteDeviceList.push(remoteDevice);
            }
        }
    }
}
