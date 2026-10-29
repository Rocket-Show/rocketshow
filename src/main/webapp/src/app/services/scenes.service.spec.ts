import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";

import { ScenesService } from "./scenes.service";
import { SceneProject } from "../models/scene-project";
import { State } from "../models/state";

/**
 * The scenes as the Play screen shows them (in the order of the scene tree of the designer) and what a tap on one
 * of them lights.
 */
describe("ScenesService", () => {
  let service: ScenesService;
  let http: HttpTestingController;

  // Folders and scenes ordered together, like the designer orders them: "Bar" sits above the folder "Party"
  const club = new SceneProject({
    name: "Club",
    sceneFolders: [
      { uuid: "basics", name: "Basics", sortIndex: 0 },
      { uuid: "party", name: "Party", sortIndex: 2 },
      { uuid: "strobes", name: "Strobes", parentUuid: "party", sortIndex: 1 },
      { uuid: "empty", name: "Empty", sortIndex: 3 },
    ],
    scenes: [
      { uuid: "cool", name: "Cool", folderUuid: "basics", sortIndex: 1, colorAuto: false, color: "#0000ff" },
      { uuid: "warm", name: "Warm", folderUuid: "basics", sortIndex: 0, displayColor: "#ff9900", icon: "fa-sun-o" },
      { uuid: "bar", name: "Bar", sortIndex: 1, dimmer: 0.6 },
      { uuid: "strobe", name: "Strobe", folderUuid: "strobes", sortIndex: 0 },
      { uuid: "red", name: "Red", folderUuid: "party", sortIndex: 0 },
    ],
  });

  const garden = new SceneProject({ name: "Garden", scenes: [{ uuid: "lanterns", name: "Lanterns" }] });

  function state(projectName: string, activeSceneUuids: string[]): State {
    return new State({ sceneProjectName: projectName, activeSceneUuids });
  }

  function scene(uuid: string) {
    return club.scenes.find((candidate) => candidate.uuid === uuid);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ScenesService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(ScenesService);
    http = TestBed.inject(HttpTestingController);
    service.projects = [club, garden];
  });

  afterEach(() => {
    http.verify();
  });

  it("orders the scenes like the scene tree of the designer", () => {
    expect(service.scenesIn(club, undefined).map((s) => s.name)).toEqual(["Warm", "Cool", "Bar", "Red", "Strobe"]);
    expect(service.scenesIn(club, "party").map((s) => s.name)).toEqual(["Red", "Strobe"]);
  });

  it("offers the folders at the top with scenes in them", () => {
    expect(service.topFolders(club).map((folder) => folder.name)).toEqual(["Basics", "Party"]);
    expect(service.topFolders(garden)).toEqual([]);
  });

  it("shows the project of the scenes on, until another one is picked", () => {
    expect(service.viewedProject(state("Garden", ["lanterns"]))).toBe(garden);
    expect(service.viewedProject(state(undefined, []))).toBe(club);

    service.viewProject(garden);
    expect(service.viewedProject(state("Club", ["warm"]))).toBe(garden);
  });

  it("lights only the tapped scene and turns it off with the next tap", () => {
    const current = state("Club", ["warm", "cool"]);

    service.tap(scene("red"), club, current).subscribe();
    http.expectOne("scenes/active?projectName=Club").flush(null);
    expect(current.activeSceneUuids).toEqual(["red"]);

    service.tap(scene("red"), club, current).subscribe();
    const request = http.expectOne("scenes/active?projectName=Club");
    expect(request.request.body).toEqual([]);
    request.flush(null);
  });

  it("adds the tapped scene to the ones on while stacking", () => {
    const current = state("Club", ["warm"]);
    service.stack = true;

    service.tap(scene("red"), club, current).subscribe();
    http.expectOne("scenes/active?projectName=Club").flush(null);
    expect(current.activeSceneUuids).toEqual(["warm", "red"]);

    service.tap(scene("warm"), club, current).subscribe();
    http.expectOne("scenes/active?projectName=Club").flush(null);
    expect(current.activeSceneUuids).toEqual(["red"]);
  });

  it("starts over with the scenes of another project", () => {
    const current = state("Garden", ["lanterns"]);
    service.stack = true;

    service.tap(scene("red"), club, current).subscribe();
    http.expectOne("scenes/active?projectName=Club").flush(null);

    expect(current.sceneProjectName).toBe("Club");
    expect(current.activeSceneUuids).toEqual(["red"]);
    expect(service.scenesOn(current).map((s) => s.name)).toEqual(["Red"]);
  });

  it("marks a scene with the color the designer shows it with", () => {
    expect(service.color(scene("warm"))).toBe("#ff9900");
    // picked by hand
    expect(service.color(scene("cool"))).toBe("#0000ff");
    // decided by its presets, but not written by the designer yet
    expect(service.color(scene("bar"))).toBeUndefined();

    expect(service.icon(scene("warm"))).toBe("fa-sun-o");
    expect(service.icon(scene("bar"))).toBe(ScenesService.defaultIcon);
  });

});
