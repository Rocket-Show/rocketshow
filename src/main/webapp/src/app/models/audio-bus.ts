export class AudioBus {
    name: string = '';
    channels: number = 2;
    volumes: number[] ;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.name = data.name;
        this.channels = data.channels;

        this.initVolumes();
    }

    public initVolumes() {
        this.volumes = [];

        for (var i = 0; i < this.channels; i++) {
            this.volumes.push(0);
        }
    };
}
