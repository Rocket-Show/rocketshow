// A scene of a designer project, as the Play screen lights it by hand
export class DesignerScene {
    uuid: string;
    name: string;

    // the folder of the scene tree of the designer the scene is in (undefined = top level) and its position among the
    // folders and scenes of that folder
    folderUuid: string;
    sortIndex: number = 0;

    // how the designer marks the scene: the font awesome class of its icon (undefined = the default one) and its
    // color. While colorAuto is set, the presets of the scene decide its color, which the designer writes as
    // displayColor when it saves the project.
    icon: string;
    color: string;
    colorAuto: boolean = true;
    displayColor: string;

    // how much of the scene reaches the stage: 1 = all of it
    dimmer: number = 1;

    constructor(data?: any) {
        if (!data) {
            return;
        }

        this.uuid = data.uuid;
        this.name = data.name;
        this.folderUuid = data.folderUuid;
        this.sortIndex = data.sortIndex || 0;
        this.icon = data.icon;
        this.color = data.color;
        this.colorAuto = data.colorAuto !== false;
        this.displayColor = data.displayColor;
        this.dimmer = data.dimmer === undefined ? 1 : data.dimmer;
    }
}

// A folder of the scene tree of the designer
export class DesignerSceneFolder {
    uuid: string;
    name: string;

    // the folder this one is in (undefined = top level) and its position among the folders and scenes of that folder
    parentUuid: string;
    sortIndex: number = 0;

    constructor(data?: any) {
        if (!data) {
            return;
        }

        this.uuid = data.uuid;
        this.name = data.name;
        this.parentUuid = data.parentUuid;
        this.sortIndex = data.sortIndex || 0;
    }
}

// A designer project with scenes, which the Play screen picks scenes from
export class SceneProject {
    name: string;
    scenes: DesignerScene[] = [];
    sceneFolders: DesignerSceneFolder[] = [];

    constructor(data?: any) {
        if (!data) {
            return;
        }

        this.name = data.name;
        this.scenes = (data.scenes || []).map((scene: any) => new DesignerScene(scene));
        this.sceneFolders = (data.sceneFolders || []).map((folder: any) => new DesignerSceneFolder(folder));
    }
}
