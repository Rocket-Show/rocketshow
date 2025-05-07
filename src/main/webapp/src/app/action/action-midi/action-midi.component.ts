import { Component, Input } from "@angular/core";
import { ActionMidi } from "../../models/action-midi";
import { MidiService } from "../../services/midi.service";

@Component({
  selector: "app-action-midi",
  templateUrl: "./action-midi.component.html",
  styleUrl: "./action-midi.component.scss",
})
export class ActionMidiComponent {
  @Input()
  action: ActionMidi;

  public programList: any;
  public channelList: number[] = [];

  constructor(public midiService: MidiService) {
    let programListMap = new Map<number, string>();
    programListMap.set(128, "NOTE_OFF");
    programListMap.set(144, "NOTE_ON");
    programListMap.set(160, "AFTERTOUCH");
    programListMap.set(176, "CONTROL_CHANGE");
    programListMap.set(192, "PROGRAM_CHANGE");
    programListMap.set(208, "CHANNEL_PRESSURE");
    programListMap.set(224, "PITCH_BEND");
    this.programList = Array.from(programListMap.entries());

    for (let i = 0; i < 16; i++) {
      this.channelList.push(i);
    }
  }
}
