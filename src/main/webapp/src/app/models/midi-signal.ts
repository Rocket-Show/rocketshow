export class MidiSignal {
    command: number = 0;
    channel: number = 0;
    note: number = 0;
    velocity: number = 0;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.command = data.command;
        this.channel = data.channel;
        this.note = data.note;
        this.velocity = data.velocity;
    }
}
