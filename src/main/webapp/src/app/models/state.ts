export class State {
    playState: string = "STOPPED";
    currentCompositionIndex: number = 0;
    currentCompositionName: string = "";
    currentCompositionDurationMillis: number = 0;
    positionMillis: number;
    updateState: string;
    currentSetName: string;
    updateFinished: boolean;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.playState = data.playState;
        this.currentCompositionIndex = data.currentCompositionIndex;
        this.currentCompositionName = data.currentCompositionName;
        this.currentCompositionDurationMillis = data.currentCompositionDurationMillis;
        this.positionMillis = data.positionMillis;
        this.updateState = data.updateState;
        this.currentSetName = data.currentSetName;
        this.updateFinished = data.updateFinished;
    }
}
