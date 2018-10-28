export class ControlAction {
    action: string = 'PLAY';
    compositionName: string;
    remoteDeviceList: string[] = [];
    executeLocally: boolean = true;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.action = data.action;
        this.compositionName = data.compositionName;

        if(data.remoteDeviceList) {
            for(let remoteDevice of data.remoteDeviceList) {
                this.remoteDeviceList.push(remoteDevice);
            }
        }

        this.executeLocally = data.executeLocally;
    }
}
