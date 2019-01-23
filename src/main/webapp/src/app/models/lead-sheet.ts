export class LeadSheet {

    name: string = '';
    instrumentUuid: string;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.name = data.name;
        this.instrumentUuid = data.instrumentUuid;
    }

}
