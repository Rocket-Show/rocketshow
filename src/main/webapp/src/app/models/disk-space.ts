export class DiskSpace {
    usedMB: number;
    availableMB: number;

    constructor(data?: any) {
        if(!data) {
        	return;
        }

        this.usedMB = data.usedMB;
        this.availableMB = data.availableMB;
    }
}
