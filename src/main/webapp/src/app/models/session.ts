export class Session {
    autoSelectNextComposition: boolean = false;
    playViewMode: 'setlist' | 'grid' = 'setlist';

    // what the Play screen shows: the compositions of the set or the scenes of the designer projects
    playMode: 'compositions' | 'scenes' = 'compositions';

    constructor(data?: any) {
        if(!data) {
        	return;
        }

        this.autoSelectNextComposition = data.autoSelectNextComposition;
        this.playViewMode = data.playViewMode || 'setlist';
        this.playMode = data.playMode || 'compositions';
    }
}
