import { ActionTrigger } from "./action-trigger";

export class ActionTriggerRaspberryGpio extends ActionTrigger {
    pinId: number = 4;

    constructor(data?: any) {
        super(data);

        if(!data) {
        	return;
        }

        this.pinId = data.pinId;
    }
}
