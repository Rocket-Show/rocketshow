import { Component, Input } from "@angular/core";
import { ActionTriggerMidiSongSelect } from "../../../../models/action-trigger-midi-song-select";

@Component({
    selector: "app-action-trigger-midi-song-select",
    templateUrl: "./action-trigger-midi-song-select.component.html",
    styleUrl: "./action-trigger-midi-song-select.component.scss",
    standalone: false
})
export class ActionTriggerMidiSongSelectComponent {
  @Input()
  trigger: ActionTriggerMidiSongSelect;

  songList: number[] = [];

  constructor() {
    for (let i = 0; i < 128; i++) {
      this.songList.push(i);
    }
  }
}
