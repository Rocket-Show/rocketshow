import { NgZone } from "@angular/core";
import { of } from "rxjs";

import { State } from "../../models/state";
import { PlaySceneLiveComponent } from "./play-scene-live.component";

/**
 * The tempo of the live row: the beat lit runs on from the beats the device sent, and each button is one request.
 */
describe("PlaySceneLiveComponent", () => {
  let component: PlaySceneLiveComponent;
  let tempoService: jasmine.SpyObj<any>;

  beforeEach(() => {
    tempoService = jasmine.createSpyObj("TempoService", ["tap", "beatOne", "set", "halve", "double", "nudge"]);
    for (const method of ["tap", "beatOne", "set", "halve", "double", "nudge"]) {
      tempoService[method].and.returnValue(of(null));
    }

    component = new PlaySceneLiveComponent({} as any, tempoService, {} as any, new NgZone({}));
  });

  function receive(data: any, arrivedAt: number) {
    spyOn(performance, "now").and.returnValue(arrivedAt);
    component.state = new State(data);
    component.ngOnChanges({ state: {} as any });
  }

  it("runs the beats on from the ones sent, at the tempo, in the bar", () => {
    receive({ tempoBeatsPerMinute: 120, tempoBeats: 6.5, tempoBeatsPerBar: 4 }, 1000);

    // beat 7 is the third of its bar (from 0: 6 % 4 = 2)
    expect(component.currentBeat(1000)).toBe(2);
    // half a beat later (250 ms at 120 BPM): the fourth
    expect(component.currentBeat(1250)).toBe(3);
    // a beat and a half later: the first of the next bar
    expect(component.currentBeat(1750)).toBe(0);
  });

  it("shows the beats of a bar the device counts in, and none lit without a tempo", () => {
    receive({ tempoBeats: 3, tempoBeatsPerBar: 3 }, 0);

    expect(component.beatIndices()).toEqual([0, 1, 2]);
    expect(component.currentBeat(5000)).toBe(-1);
    expect(component.tempo()).toBe("–");
    expect(component.tempoSource()).toBe("project");
  });

  it("shows the tempo rounded to a tenth, with where it comes from", () => {
    receive({ tempoBeatsPerMinute: 127.96, tempoSource: "tap" }, 0);

    expect(component.tempo()).toBe("128");
    expect(component.tempoSource()).toBe("tap");
  });

  it("taps, sets the first beat, halves, doubles and goes back to the programmed tempo", () => {
    const event = jasmine.createSpyObj("Event", ["preventDefault"]);

    component.tap(event);
    component.beatOne(event);
    component.halve();
    component.double();
    component.resetTempo();

    expect(tempoService.tap).toHaveBeenCalled();
    expect(tempoService.beatOne).toHaveBeenCalled();
    expect(tempoService.halve).toHaveBeenCalled();
    expect(tempoService.double).toHaveBeenCalled();
    expect(tempoService.set).toHaveBeenCalledWith(undefined);
    // the pointer going down is the beat: the click following it is not
    expect(event.preventDefault).toHaveBeenCalledTimes(2);
  });
});
