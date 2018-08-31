export class Instrument {

    uuid: string = '';
    name: string = '';

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.uuid = data.uuid;
        this.name = data.name;
    }
}
