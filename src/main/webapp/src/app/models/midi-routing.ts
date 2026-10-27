import { MidiMapping } from './midi-mapping';

export class MidiRouting {
    midiDestination: string;
    midiMapping: MidiMapping = new MidiMapping();
    universeUuid: string = "";
    // Named like in the API of the unit
    remoteDeviceNameList: string[] = [];

    constructor(data?: any) {
        if(!data) {
        	return;
        }

        this.midiDestination = data.midiDestination;
        this.midiMapping = new MidiMapping(data.midiMapping);
        this.universeUuid = data.universeUuid || "";

        if(data.remoteDeviceNameList) {
            for(let remoteDevice of data.remoteDeviceNameList) {
                this.remoteDeviceNameList.push(remoteDevice);
            }
        }
    }
}
