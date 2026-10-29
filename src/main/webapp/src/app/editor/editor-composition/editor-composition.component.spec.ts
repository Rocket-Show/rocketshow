import { fakeAsync, tick } from "@angular/core/testing";
import { of, Subject } from "rxjs";

import { EditorCompositionComponent } from "./editor-composition.component";
import { Composition } from "../../models/composition";
import { Settings } from "../../models/settings";

/**
 * The composition editor: saving, renaming and deleting compositions, and playing them for a test.
 */
describe("Composition editor", () => {
  let compositionService: jasmine.SpyObj<any>;
  let warningDialogService: jasmine.SpyObj<any>;
  let editor: EditorCompositionComponent;

  beforeEach(() => {
    compositionService = jasmine.createSpyObj("CompositionService",
      ["getCompositions", "getComposition", "saveComposition", "deleteComposition", "testPlay", "testStop", "testSeek"]);
    compositionService.compositionsChanged = new Subject<void>();
    compositionService.compositionRenamed = new Subject<{ previousName: string; name: string }>();
    compositionService.compositionDeleted = new Subject<string>();
    compositionService.getCompositions.and.returnValue(of([new Composition({ name: "Intro" }), new Composition({ name: "Rock & Roll" })]));
    compositionService.getComposition.and.callFake((name: string) => of(new Composition({ name: name, fileList: [] })));
    compositionService.saveComposition.and.returnValue(of({}));
    compositionService.deleteComposition.and.returnValue(of({}));
    compositionService.testStop.and.returnValue(of({}));

    warningDialogService = jasmine.createSpyObj("WarningDialogService", ["show"]);
    const pendingChangesDialogService = { check: () => of(true) };
    const translateService = { get: () => of({}) };
    const toastrService = jasmine.createSpyObj("ToastrService", ["success", "error"]);
    const settingsService = { getSettings: () => of(new Settings()), settingsChanged: new Subject<void>() };

    editor = new EditorCompositionComponent(compositionService, undefined, warningDialogService, pendingChangesDialogService as any,
      toastrService, translateService as any, undefined, settingsService as any);
    editor.ngOnInit();
  });

  // The name the composition was saved under before, if renamed
  function savedWithPreviousName(): string {
    expect(compositionService.saveComposition).toHaveBeenCalledTimes(1);
    return compositionService.saveComposition.calls.mostRecent().args[1];
  }

  it("saves a new composition", () => {
    editor.createComposition();
    editor.currentComposition.name = "Outro";

    editor.save(editor.currentComposition);

    expect(savedWithPreviousName()).toBeUndefined();
    expect(compositionService.saveComposition.calls.mostRecent().args[0].name).toBe("Outro");
    expect(warningDialogService.show).not.toHaveBeenCalled();
  });

  it("saves a composition under its name", () => {
    editor.selectComposition(new Composition({ name: "Intro" }));

    editor.save(editor.currentComposition);

    expect(savedWithPreviousName()).toBeUndefined();
    expect(warningDialogService.show).not.toHaveBeenCalled();
  });

  it("renames a composition, so the sets and the settings using it keep it", () => {
    const renamed = [];
    compositionService.compositionRenamed.subscribe((composition) => renamed.push(composition));
    editor.selectComposition(new Composition({ name: "Intro" }));
    editor.currentComposition.name = "Opening";

    editor.save(editor.currentComposition);

    expect(savedWithPreviousName()).toBe("Intro");
    expect(warningDialogService.show).not.toHaveBeenCalled();
    // For the set editor
    expect(renamed).toEqual([{ previousName: "Intro", name: "Opening" }]);
  });

  it("renames no composition saved under its name", () => {
    const renamed = [];
    compositionService.compositionRenamed.subscribe((composition) => renamed.push(composition));
    editor.selectComposition(new Composition({ name: "Intro" }));

    editor.save(editor.currentComposition);

    expect(renamed).toEqual([]);
  });

  it("asks before overwriting another composition", () => {
    editor.createComposition();
    editor.currentComposition.name = "Intro";

    warningDialogService.show.and.returnValue(of(false));
    editor.save(editor.currentComposition);
    expect(warningDialogService.show).toHaveBeenCalledWith("editor.warning-overwrite-composition");
    expect(compositionService.saveComposition).not.toHaveBeenCalled();

    warningDialogService.show.and.returnValue(of(true));
    editor.save(editor.currentComposition);
    expect(savedWithPreviousName()).toBeUndefined();
  });

  it("asks before renaming a composition to the name of another one", () => {
    editor.selectComposition(new Composition({ name: "Intro" }));
    editor.currentComposition.name = "Rock & Roll";
    warningDialogService.show.and.returnValue(of(true));

    editor.save(editor.currentComposition);

    expect(warningDialogService.show).toHaveBeenCalledWith("editor.warning-overwrite-composition");
    expect(savedWithPreviousName()).toBe("Intro");
  });

  it("removes the slashes of a name, which would leave the folder of the compositions", () => {
    editor.createComposition();
    editor.currentComposition.name = "AC/DC\\Live";

    editor.save(editor.currentComposition);

    expect(compositionService.saveComposition.calls.mostRecent().args[0].name).toBe("ACDCLive");
  });

  it("saves no composition without a name", () => {
    editor.createComposition();
    editor.currentComposition.name = "/";

    editor.save(editor.currentComposition);

    expect(compositionService.saveComposition).not.toHaveBeenCalled();
  });

  it("deletes the composition under its saved name", () => {
    const deleted = [];
    compositionService.compositionDeleted.subscribe((name) => deleted.push(name));
    editor.selectComposition(new Composition({ name: "Intro" }));
    // Renamed, but not saved
    editor.currentComposition.name = "Opening";
    warningDialogService.show.and.returnValue(of(true));

    editor.delete(editor.currentComposition);

    expect(compositionService.deleteComposition).toHaveBeenCalledWith("Intro");
    expect(editor.currentComposition).toBeUndefined();
    expect(deleted).toEqual(["Intro"]);
  });

  it("finds compositions regardless of the case", () => {
    editor.filterCompositions("rOCK");

    expect(editor.filteredCompositions.map((composition) => composition.name)).toEqual(["Rock & Roll"]);
  });

  it("keeps a muted file muted", () => {
    const composition = new Composition({
      name: "Song",
      fileList: [{ audioFile: { name: "click.wav", type: "AUDIO", volume: 0 } }, { audioFile: { name: "song.wav", type: "AUDIO", volume: 0.5 } }],
      audioVolume: 0,
    });

    const saved = JSON.parse(JSON.stringify(composition));

    expect(saved.fileList[0].audioFile.volume).toBe(0);
    expect(saved.fileList[1].audioFile.volume).toBe(0.5);
    expect(saved.audioVolume).toBe(0);
  });

  it("shows the position of a test play until it is stopped", fakeAsync(() => {
    compositionService.testPlay.and.returnValue(of({ durationMillis: 10000 }));
    editor.selectComposition(new Composition({ name: "Intro" }));

    editor.play();
    tick(500);
    expect(editor.isTestPlaying).toBeTrue();
    expect(editor.testPlayPositionMillis).toBeGreaterThanOrEqual(490);

    editor.stop();
    const stoppedAt = editor.testPlayPositionMillis;
    tick(500);

    expect(editor.isTestPlaying).toBeFalse();
    expect(editor.testPlayPositionMillis).toBe(stoppedAt);
    expect(compositionService.testStop).toHaveBeenCalledTimes(1);
  }));

  it("stops a test play at its end", fakeAsync(() => {
    compositionService.testPlay.and.returnValue(of({ durationMillis: 1000 }));
    editor.selectComposition(new Composition({ name: "Intro" }));

    editor.play();
    tick(1000);

    expect(editor.isTestPlaying).toBeFalse();
    expect(compositionService.testStop).toHaveBeenCalledTimes(1);
  }));
});
