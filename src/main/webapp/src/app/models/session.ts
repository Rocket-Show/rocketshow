export class Session {
    updateFinished: boolean = false;
    autoSelectNextComposition: boolean = false;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.updateFinished = data.updateFinished;
        this.autoSelectNextComposition = data.autoSelectNextComposition;
    }
}
