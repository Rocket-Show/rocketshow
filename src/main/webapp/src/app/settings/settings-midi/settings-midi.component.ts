import { HttpClient } from "@angular/common/http";
import { TranslateService } from "@ngx-translate/core";
import { ToastrService } from "ngx-toastr";
import { CompositionService } from "./../../services/composition.service";
import { Composition } from "./../../models/composition";
import { SettingsService } from "./../../services/settings.service";
import { MidiDevice } from "./../../models/midi-device";
import { Component, OnInit, OnDestroy } from "@angular/core";
import { Settings } from "../../models/settings";
import { map } from "rxjs/operators";
import { Subscription } from "rxjs";
import { ActionTriggerMidi } from "../../models/action-trigger-midi";

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

  midiActionList: string[] = [];

  compositions: Composition[];

  constructor(
    private settingsService: SettingsService,
    private compositionService: CompositionService,
    private http: HttpClient,
    private translateService: TranslateService,
    private toastrService: ToastrService
  ) {
    this.compositionService
      .getCompositions(true)
      .subscribe((compositions: Composition[]) => {
        this.compositions = compositions;
      });

    this.midiActionList.push("PLAY");
    this.midiActionList.push("NEXT_COMPOSITION");
    this.midiActionList.push("PREVIOUS_COMPOSITION");
    this.midiActionList.push("STOP");
    this.midiActionList.push("REBOOT");
    this.midiActionList.push("SELECT_COMPOSITION_BY_NAME");
    this.midiActionList.push("SELECT_COMPOSITION_BY_NAME_AND_PLAY");
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
    let trigger = new ActionTriggerMidi();
    this.settings.actionTriggerMidiList.push(trigger);
  }

  deleteActionTrigger(triggerIndex: number) {
    this.settings.actionTriggerMidiList.splice(triggerIndex, 1);
  }

  midiDeviceEqual(device1: MidiDevice, device2: MidiDevice): boolean {
    return device1 && device2 ? device1.id === device2.id : false;
  }
}
