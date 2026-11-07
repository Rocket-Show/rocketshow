export class State {
    playState: string = "STOPPED";
    currentCompositionIndex: number = 0;
    currentCompositionName: string = "";
    currentCompositionDurationMillis: number = 0;
    positionMillis: number;
    currentSetName: string;
    midiTimecodeLocked: boolean = false;
    midiTimecodeMillis: number;
    error: string;

    // the scenes picked by hand on the Play screen and whether a composition holds them, because it lights the stage
    // with its own designer project at the moment
    sceneProjectName: string;
    activeSceneUuids: string[] = [];
    sceneBlackout: boolean = false;
    sceneMasterDimmer: number = 1;
    scenesHeld: boolean = false;

    // the live controls of the scenes: the ones flashed, the speed master, the dimmer of each scene (the ones at full
    // left out) and the scene waiting for its key, pad or button of a MIDI controller
    flashedSceneUuids: string[] = [];
    sceneSpeed: number = 1;
    sceneDimmers: { [sceneUuid: string]: number } = {};
    sceneLearning: SceneLearning;

    // the tempo playing (from outside, else the one of the composition or the project playing; undefined while nothing
    // gives one) and where it is from ("project", "tap", "set" or "clock"), the beat count of the beat clock when the
    // state was sent and how many beats a bar has
    tempoBeatsPerMinute: number;
    tempoSource: string = "project";
    tempoBeats: number = 0;
    tempoBeatsPerBar: number = 4;

    // a composition stopped at a hold point and waits for GO (it is paused meanwhile)
    holding: boolean = false;

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
        this.midiTimecodeLocked = data.midiTimecodeLocked;
        this.midiTimecodeMillis = data.midiTimecodeMillis;
        this.error = data.error;
        this.sceneProjectName = data.sceneProjectName;
        this.activeSceneUuids = data.activeSceneUuids || [];
        this.sceneBlackout = data.sceneBlackout || false;
        this.sceneMasterDimmer = data.sceneMasterDimmer === undefined ? 1 : data.sceneMasterDimmer;
        this.scenesHeld = data.scenesHeld || false;
        this.flashedSceneUuids = data.flashedSceneUuids || [];
        this.sceneSpeed = data.sceneSpeed === undefined || data.sceneSpeed === null ? 1 : data.sceneSpeed;
        this.sceneDimmers = data.sceneDimmers || {};
        this.sceneLearning = data.sceneLearning || undefined;
        this.tempoBeatsPerMinute = data.tempoBeatsPerMinute ?? undefined;
        this.tempoSource = data.tempoSource || "project";
        this.tempoBeats = data.tempoBeats ?? 0;
        this.tempoBeatsPerBar = data.tempoBeatsPerBar > 0 ? data.tempoBeatsPerBar : 4;
        this.holding = data.holding || false;
    }
}

// A scene waiting for the key, pad or button of a MIDI controller which plays it from then on: toggling it ("toggle")
// or flashing it while it is held ("flash")
export interface SceneLearning {
    projectName: string;
    scene: string;
    mode: string;
}

// A key, pad or button of a MIDI controller learned for a scene: a note ("note") or a control change ("cc") on a
// channel (counted from 0), toggling the scene ("toggle") or flashing it ("flash")
export interface SceneMidiMapping {
    scene: string;
    mode: string;
    kind: string;
    channel: number;
    number: number;
}
