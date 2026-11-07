import { Component, Input } from "@angular/core";
import { ActionTempo } from "../../models/action-tempo";

// The tempo the beat-synced effects run at, set from a trigger (e.g. a footswitch tapping the beat)
@Component({
  selector: "app-action-tempo",
  templateUrl: "./action-tempo.component.html",
  styleUrl: "./action-tempo.component.scss",
  standalone: false,
})
export class ActionTempoComponent {
  @Input()
  action: ActionTempo;

  public typeList: string[] = ["TAP", "BEAT_ONE", "SET", "HALVE", "DOUBLE", "FASTER", "SLOWER", "PROJECT"];

  // a tempo set as far as it goes
  setBeatsPerMinute(beatsPerMinute: number) {
    if (beatsPerMinute > 0) {
      this.action.beatsPerMinute = beatsPerMinute;
    }
  }
}
