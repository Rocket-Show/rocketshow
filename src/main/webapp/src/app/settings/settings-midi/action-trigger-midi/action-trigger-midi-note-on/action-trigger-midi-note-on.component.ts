import { Component, Input } from "@angular/core";
import { ActionTriggerMidiNoteOn } from "../../../../models/action-trigger-midi-note-on";
import { MidiService } from "../../../../services/midi.service";

@Component({
  selector: "app-action-trigger-midi-note-on",
  templateUrl: "./action-trigger-midi-note-on.component.html",
  styleUrl: "./action-trigger-midi-note-on.component.scss",
})
export class ActionTriggerMidiNoteOnComponent {
  @Input()
  trigger: ActionTriggerMidiNoteOn;

  constructor(public midiService: MidiService) {}
}
