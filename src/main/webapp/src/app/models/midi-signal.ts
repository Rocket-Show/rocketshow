export class MidiSignal {
    command: number = 0;
    channel: number = 0;
    data1: number = 0;
    data2: number = 0;

    constructor(data?: any) {
        if(!data) {
        	return;
        }

        this.command = data.command;
        this.channel = data.channel;
        this.data1 = data.data1;
        this.data2 = data.data2;
    }
}
