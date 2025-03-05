import { ActionTrigger } from "./action-trigger";

export class RaspberryGpioControl extends ActionTrigger {
    pinId: number = 0;

    constructor(data?: any) {
        super(data);

        if(!data) {
        	return;
        }

        this.pinId = data.pinId;
    }
}
