import { ControlAction } from './control-action';

export class RaspberryGpioControl extends ControlAction {
    pinId: number = 0;

    constructor(data?: any) {
        super(data);

        if(!data) {
        	return;
        }

        this.pinId = data.pinId;
    }
}
