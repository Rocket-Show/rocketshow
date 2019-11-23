export class OperatingSystemInformation {
    type: string = '';
    subType: string = '';
    architectureType: string = '';
    raspberryVersion: string = '';

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.type = data.type;
        this.subType = data.subType;
        this.architectureType = data.architectureType;
        this.raspberryVersion = data.raspberryVersion;
    }
}
