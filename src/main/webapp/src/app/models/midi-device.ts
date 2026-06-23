export class MidiDevice {
    id: number = 0;
    name: string = '';
    vendor: string = '';
    description: string = '';
    serialPort: boolean = false;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        this.id = data.id;
        this.name = data.name;
        this.vendor = data.vendor;
        this.description = data.description;
        this.serialPort = data.serialPort;
    }
}
