import { ControlAction } from './control-action';

export class MidiControl extends ControlAction {
    channelFrom: number = 0;
    noteFrom: number = 0;

    constructor(data?: any) {
        super(data);

        if(!data) {
        	return;
        }

        this.channelFrom = data.channelFrom;
        this.noteFrom = data.noteFrom;
    }
}
