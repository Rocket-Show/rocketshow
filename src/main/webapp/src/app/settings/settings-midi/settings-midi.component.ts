import { CompositionService } from "./../../services/composition.service";
import { Composition } from "./../../models/composition";
import { SettingsService } from "./../../services/settings.service";
import { MidiDevice } from "./../../models/midi-device";
import { Component, OnInit, OnDestroy } from "@angular/core";
import { Settings } from "../../models/settings";
import { map } from "rxjs/operators";
import { Subscription } from "rxjs";
import { ActionTriggerMidi } from "../../models/action-trigger-midi";
import { ActionTriggerMidiNoteOn } from "../../models/action-trigger-midi-note-on";

@Component({
  selector: "app-settings-midi",
  templateUrl: "./settings-midi.component.html",
  styleUrls: ["./settings-midi.component.scss"],
})
export class SettingsMidiComponent implements OnInit, OnDestroy {
  private settingsChangedSubscription: Subscription;

  selectUndefinedOptionValue: any;

  settings: Settings;

  midiInDevices: MidiDevice[];
  midiOutDevices: MidiDevice[];

  compositions: Composition[];

  constructor(
    private settingsService: SettingsService,
    private compositionService: CompositionService
  ) {
    this.compositionService
      .getCompositions(true)
      .subscribe((compositions: Composition[]) => {
        this.compositions = compositions;
      });
  }

  private loadSettings() {
    this.settingsService
      .getSettings()
      .pipe(
        map((result) => {
          this.settings = result;

          this.settingsService.getMidiInDevices().subscribe((response) => {
            this.midiInDevices = response;
          });

          this.settingsService.getMidiOutDevices().subscribe((response) => {
            this.midiOutDevices = response;
          });
        })
      )
      .subscribe();
  }

  ngOnInit() {
    this.loadSettings();

    this.settingsChangedSubscription =
      this.settingsService.settingsChanged.subscribe(() => {
        this.loadSettings();
      });
  }

  ngOnDestroy() {
    this.settingsChangedSubscription.unsubscribe();
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt) {
    return evt.related.className.indexOf("no-sortjs") === -1;
  }

  addActionTrigger() {
    let trigger = new ActionTriggerMidiNoteOn();
    this.settings.actionTriggerMidiList.push(trigger);
  }

  deleteActionTrigger(triggerIndex: number) {
    this.settings.actionTriggerMidiList.splice(triggerIndex, 1);
  }

  onTriggerChange(event: { index: number; newTrigger: ActionTriggerMidi }) {
    this.settings.actionTriggerMidiList[event.index] = event.newTrigger;
  }

  midiDeviceEqual(device1: MidiDevice, device2: MidiDevice): boolean {
    return device1 && device2 ? device1.id === device2.id : false;
  }
}
