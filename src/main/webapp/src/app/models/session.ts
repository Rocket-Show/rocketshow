export class Session {
    autoSelectNextComposition: boolean = false;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.autoSelectNextComposition = data.autoSelectNextComposition;
    }
}
