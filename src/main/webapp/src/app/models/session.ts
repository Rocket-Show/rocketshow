export class Session {
    autoSelectNextComposition: boolean = false;
    playViewMode: 'setlist' | 'grid' = 'setlist';

    constructor(data?: any) {
        if(!data) {
        	return;
        }

        this.autoSelectNextComposition = data.autoSelectNextComposition;
        this.playViewMode = data.playViewMode || 'setlist';
    }
}
