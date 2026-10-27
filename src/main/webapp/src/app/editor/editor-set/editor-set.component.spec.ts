import { of, Subject } from "rxjs";

import { EditorSetComponent } from "./editor-set.component";
import { EditorCompositionComponent } from "../editor-composition/editor-composition.component";
import { Composition } from "../../models/composition";
import { Set } from "../../models/set";
import { Settings } from "../../models/settings";
import { PendingChangesDialogService } from "../../services/pending-changes-dialog.service";

/**
 * The set editor, next to the composition editor: a set open in it contains the compositions under the names they
 * have on the device.
 */
describe("Set editor", () => {
  let compositionService: jasmine.SpyObj<any>;
  let warningDialogService: jasmine.SpyObj<any>;
  let compositionEditor: EditorCompositionComponent;
  let setEditor: EditorSetComponent;

  beforeEach(() => {
    compositionService = jasmine.createSpyObj("CompositionService",
      ["getCompositions", "getComposition", "saveComposition", "deleteComposition", "getSets", "getSet", "saveSet", "deleteSet"]);
    compositionService.compositionsChanged = new Subject<void>();
    compositionService.compositionRenamed = new Subject<{ previousName: string; name: string }>();
    compositionService.compositionDeleted = new Subject<string>();
    compositionService.getCompositions.and.returnValue(of([new Composition({ name: "Intro" }), new Composition({ name: "Song" })]));
    compositionService.getComposition.and.callFake((name: string) => of(new Composition({ name: name, fileList: [] })));
    compositionService.saveComposition.and.returnValue(of({}));
    compositionService.deleteComposition.and.returnValue(of({}));
    compositionService.getSets.and.returnValue(of([new Set({ name: "Show" })]));
    compositionService.getSet.and.returnValue(of(new Set({
      name: "Show",
      compositionList: [{ name: "Intro" }, { name: "Song" }, { name: "Intro", autoStartNextComposition: true }],
    })));
    compositionService.saveSet.and.returnValue(of({}));

    warningDialogService = jasmine.createSpyObj("WarningDialogService", ["show"]);
    warningDialogService.show.and.returnValue(of(true));
    const pendingChangesDialogService = new PendingChangesDialogService(undefined, undefined, warningDialogService);
    const translateService = { get: () => of({}) };
    const toastrService = jasmine.createSpyObj("ToastrService", ["success", "error"]);
    const settingsService = { getSettings: () => of(new Settings()), settingsChanged: new Subject<void>() };

    compositionEditor = new EditorCompositionComponent(compositionService, undefined, warningDialogService, pendingChangesDialogService,
      toastrService, translateService as any, undefined, settingsService as any);
    compositionEditor.ngOnInit();

    setEditor = new EditorSetComponent(compositionService, undefined, warningDialogService, pendingChangesDialogService,
      toastrService, translateService as any, undefined);
    setEditor.ngOnInit();
  });

  afterEach(() => {
    setEditor.ngOnDestroy();
  });

  function names(set: Set): string[] {
    return set.compositionList.map((composition) => composition.name);
  }

  function rename(previousName: string, name: string) {
    compositionEditor.selectComposition(new Composition({ name: previousName }));
    compositionEditor.currentComposition.name = name;
    compositionEditor.save(compositionEditor.currentComposition);
  }

  it("shows a composition renamed in the composition editor under its new name", () => {
    setEditor.selectSet(new Set({ name: "Show" }));

    rename("Intro", "Opening");

    expect(names(setEditor.currentSet)).toEqual(["Opening", "Song", "Opening"]);
    expect(setEditor.currentSet.compositionList[2].autoStartNextComposition).toBeTrue();

    // Nothing to save, the set on the device contains the composition under its new name already
    let canDiscard: boolean;
    setEditor.checkPendingChanges().subscribe((result) => canDiscard = result);
    expect(canDiscard).toBeTrue();
    expect(warningDialogService.show).not.toHaveBeenCalled();

    // Saved again, the set keeps the composition
    setEditor.save(setEditor.currentSet);
    expect(names(compositionService.saveSet.calls.mostRecent().args[0])).toEqual(["Opening", "Song", "Opening"]);
  });

  it("keeps the unsaved changes of the open set, when a composition is renamed", () => {
    setEditor.selectSet(new Set({ name: "Show" }));
    setEditor.removeComposition(setEditor.currentSet.compositionList[1]);

    rename("Intro", "Opening");

    expect(names(setEditor.currentSet)).toEqual(["Opening", "Opening"]);
    expect(names(setEditor.initialSet)).toEqual(["Opening", "Song", "Opening"]);

    warningDialogService.show.and.returnValue(of(false));
    let canDiscard: boolean;
    setEditor.checkPendingChanges().subscribe((result) => canDiscard = result);
    expect(canDiscard).toBeFalse();
    expect(warningDialogService.show).toHaveBeenCalledWith("editor.warning-set-changes");
  });

  it("shows a composition renamed in the composition editor under its new name in a new set", () => {
    setEditor.createSet();
    setEditor.currentSet.name = "Encore";
    setEditor.addComposition(new Composition({ name: "Intro" }));

    rename("Intro", "Opening");

    expect(names(setEditor.currentSet)).toEqual(["Opening"]);
  });

  it("removes a composition deleted in the composition editor from the open set", () => {
    setEditor.selectSet(new Set({ name: "Show" }));
    compositionEditor.selectComposition(new Composition({ name: "Intro" }));

    compositionEditor.delete(compositionEditor.currentComposition);

    expect(names(setEditor.currentSet)).toEqual(["Song"]);

    let canDiscard: boolean;
    setEditor.checkPendingChanges().subscribe((result) => canDiscard = result);
    expect(canDiscard).toBeTrue();
  });

  it("leaves the sets of a set editor which isn't open anymore", () => {
    setEditor.selectSet(new Set({ name: "Show" }));
    setEditor.ngOnDestroy();

    rename("Intro", "Opening");

    expect(names(setEditor.currentSet)).toEqual(["Intro", "Song", "Intro"]);
  });
});
