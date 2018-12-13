export class ActivityAudioChannel {
    index: number = 0;
    volumeDb: number = -500;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.index = data.index;
        this.volumeDb = data.volumeDb;
    }
}
