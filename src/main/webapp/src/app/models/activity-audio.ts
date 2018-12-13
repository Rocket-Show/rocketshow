import { ActivityAudioBus } from "./activity-audio-bus";

export class ActivityAudio {
    activityAudioBusList: ActivityAudioBus[] = [];

    constructor(data?: any) {
        if(!data) {
        	return;
        }

        this.activityAudioBusList = [];

        for(let activityAudioBus of data.activityAudioBusList) {
            this.activityAudioBusList.push(new ActivityAudioBus(activityAudioBus));
        }
    }
}
