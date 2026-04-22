import { UpdateState } from "./update-state";

export class State {
    playState: string = "STOPPED";
    currentCompositionIndex: number = 0;
    currentCompositionName: string = "";
    currentCompositionDurationMillis: number = 0;
    positionMillis: number;
    updateState: UpdateState = new UpdateState();
    currentSetName: string;
    error: string;

    constructor(data?: any) {
        if (!data) {
            return;
        }

        this.playState = data.playState;
        this.currentCompositionIndex = data.currentCompositionIndex;
        this.currentCompositionName = data.currentCompositionName;
        this.currentCompositionDurationMillis = data.currentCompositionDurationMillis;
        this.positionMillis = data.positionMillis;

        if (data.updateState) {
            this.updateState = new UpdateState(data.updateState);
        }

        this.currentSetName = data.currentSetName;
        this.error = data.error;
    }
}
