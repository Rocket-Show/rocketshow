export class State {
    playState: string = "STOPPED";
    currentCompositionIndex: number = 0;
    currentCompositionName: string = "";
    currentCompositionDurationMillis: number = 0;
    positionMillis: number;
    currentSetName: string;
    error: string;

    // the scenes picked by hand on the Play screen and whether a composition holds them, because it lights the stage
    // with its own designer project at the moment
    sceneProjectName: string;
    activeSceneUuids: string[] = [];
    sceneBlackout: boolean = false;
    sceneMasterDimmer: number = 1;
    scenesHeld: boolean = false;

    constructor(data?: any) {
        if (!data) {
            return;
        }

        this.playState = data.playState;
        this.currentCompositionIndex = data.currentCompositionIndex;
        this.currentCompositionName = data.currentCompositionName;
        this.currentCompositionDurationMillis = data.currentCompositionDurationMillis;
        this.positionMillis = data.positionMillis;
        this.currentSetName = data.currentSetName;
        this.error = data.error;
        this.sceneProjectName = data.sceneProjectName;
        this.activeSceneUuids = data.activeSceneUuids || [];
        this.sceneBlackout = data.sceneBlackout || false;
        this.sceneMasterDimmer = data.sceneMasterDimmer === undefined ? 1 : data.sceneMasterDimmer;
        this.scenesHeld = data.scenesHeld || false;
    }
}
