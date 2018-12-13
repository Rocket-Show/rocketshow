import { ActivityAudioChannel } from "./activity-audio-channel";

export class ActivityAudioBus {
    name: string;
    activityAudioChannelList: ActivityAudioChannel[] = [];

    constructor(data?: any) {
        if(!data) {
        	return;
        }

        this.name = data.name;

        this.activityAudioChannelList = [];

        for(let activityAudioBus of data.activityAudioChannelList) {
            this.activityAudioChannelList.push(new ActivityAudioChannel(activityAudioBus));
        }
    }
}
