export class AudioBus {
    name: string = '';
    channels: number = 2;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.name = data.name;
        this.channels = data.channels;
    }
}
