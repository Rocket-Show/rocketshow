import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { ToastrService } from 'ngx-toastr';
import { CompositionService } from './../../services/composition.service';
import { Composition } from './../../models/composition';
import { RemoteDevice } from './../../models/remote-device';
import { MidiControl } from './../../models/midi-control';
import { Subject } from 'rxjs/Subject';
import { SettingsService } from './../../services/settings.service';
import { MidiDevice } from './../../models/midi-device';
import { Component, OnInit } from '@angular/core';
import { Settings } from '../../models/settings';

@Component({
  selector: 'app-settings-midi',
  templateUrl: './settings-midi.component.html',
  styleUrls: ['./settings-midi.component.scss']
})
export class SettingsMidiComponent implements OnInit {

  selectUndefinedOptionValue: any;

  settings: Settings;

  midiInDevices: MidiDevice[];
  midiOutDevices: MidiDevice[];

  channelList: number[] = [];
  noteList: number[] = [];
  midiActionList: string[] = [];

  compositions: Composition[];

  private noteIdNames = new Map<number, string>();

  constructor(
    private settingsService: SettingsService,
    private compositionService: CompositionService,
    private http: HttpClient,
    private translateService: TranslateService,
    private toastrService: ToastrService
  ) {
    this.compositionService.getCompositions(true).subscribe((compositions: Composition[]) => {
      this.compositions = compositions;
    });

    for (let i = 0; i < 16; i++) {
      this.channelList.push(i);
    }

    for (let i = 0; i < 128; i++) {
      this.noteList.push(i);
    }

    this.midiActionList.push('PLAY');
    this.midiActionList.push('NEXT_COMPOSITION');
    this.midiActionList.push('PREVIOUS_COMPOSITION');
    this.midiActionList.push('STOP');
    this.midiActionList.push('REBOOT');
    this.midiActionList.push('SELECT_COMPOSITION_BY_NAME');
    this.midiActionList.push('SELECT_COMPOSITION_BY_NAME_AND_PLAY');

    this.noteIdNames.set(0, 'C-2');
    this.noteIdNames.set(1, 'C#-2');
    this.noteIdNames.set(2, 'D-2');
    this.noteIdNames.set(3, 'D#-2');
    this.noteIdNames.set(4, 'E-2');
    this.noteIdNames.set(5, 'F-2');
    this.noteIdNames.set(6, 'F#-2');
    this.noteIdNames.set(7, 'G-2');
    this.noteIdNames.set(8, 'G#-2');
    this.noteIdNames.set(9, 'A-2');
    this.noteIdNames.set(10, 'A#-2');
    this.noteIdNames.set(11, 'B-2');
    this.noteIdNames.set(12, 'C-1');
    this.noteIdNames.set(13, 'C#-1');
    this.noteIdNames.set(14, 'D-1');
    this.noteIdNames.set(15, 'D#-1');
    this.noteIdNames.set(16, 'E-1');
    this.noteIdNames.set(17, 'F-1');
    this.noteIdNames.set(18, 'F#-1');
    this.noteIdNames.set(19, 'G-1');
    this.noteIdNames.set(20, 'G#-1');
    this.noteIdNames.set(21, 'A-1');
    this.noteIdNames.set(22, 'A#-1');
    this.noteIdNames.set(23, 'B-1');
    this.noteIdNames.set(24, 'C0');
    this.noteIdNames.set(25, 'C#0');
    this.noteIdNames.set(26, 'D0');
    this.noteIdNames.set(27, 'D#0');
    this.noteIdNames.set(28, 'E0');
    this.noteIdNames.set(29, 'F0');
    this.noteIdNames.set(30, 'F#0');
    this.noteIdNames.set(31, 'G0');
    this.noteIdNames.set(32, 'G#0');
    this.noteIdNames.set(33, 'A0');
    this.noteIdNames.set(34, 'A#0');
    this.noteIdNames.set(35, 'B0');
    this.noteIdNames.set(36, 'C1');
    this.noteIdNames.set(37, 'C#1');
    this.noteIdNames.set(38, 'D1');
    this.noteIdNames.set(39, 'D#1');
    this.noteIdNames.set(40, 'E1');
    this.noteIdNames.set(41, 'F1');
    this.noteIdNames.set(42, 'F#1');
    this.noteIdNames.set(43, 'G1');
    this.noteIdNames.set(44, 'G#1');
    this.noteIdNames.set(45, 'A1');
    this.noteIdNames.set(46, 'A#1');
    this.noteIdNames.set(47, 'B1');
    this.noteIdNames.set(48, 'C2');
    this.noteIdNames.set(49, 'C#2');
    this.noteIdNames.set(50, 'D2');
    this.noteIdNames.set(51, 'D#2');
    this.noteIdNames.set(52, 'E2');
    this.noteIdNames.set(53, 'F2');
    this.noteIdNames.set(54, 'F#2');
    this.noteIdNames.set(55, 'G2');
    this.noteIdNames.set(56, 'G#2');
    this.noteIdNames.set(57, 'A2');
    this.noteIdNames.set(58, 'A#2');
    this.noteIdNames.set(59, 'B2');
    this.noteIdNames.set(60, 'C3');
    this.noteIdNames.set(61, 'C#3');
    this.noteIdNames.set(62, 'D3');
    this.noteIdNames.set(63, 'D#3');
    this.noteIdNames.set(64, 'E3');
    this.noteIdNames.set(65, 'F3');
    this.noteIdNames.set(66, 'F#3');
    this.noteIdNames.set(67, 'G3');
    this.noteIdNames.set(68, 'G#3');
    this.noteIdNames.set(69, 'A3');
    this.noteIdNames.set(70, 'A#3');
    this.noteIdNames.set(71, 'B3');
    this.noteIdNames.set(72, 'C4');
    this.noteIdNames.set(73, 'C#4');
    this.noteIdNames.set(74, 'D4');
    this.noteIdNames.set(75, 'D#4');
    this.noteIdNames.set(76, 'E4');
    this.noteIdNames.set(77, 'F4');
    this.noteIdNames.set(78, 'F#4');
    this.noteIdNames.set(79, 'G4');
    this.noteIdNames.set(80, 'G#4');
    this.noteIdNames.set(81, 'A4');
    this.noteIdNames.set(82, 'A#4');
    this.noteIdNames.set(83, 'B4');
    this.noteIdNames.set(84, 'C5');
    this.noteIdNames.set(85, 'C#5');
    this.noteIdNames.set(86, 'D5');
    this.noteIdNames.set(87, 'D#5');
    this.noteIdNames.set(88, 'E5');
    this.noteIdNames.set(89, 'F5');
    this.noteIdNames.set(90, 'F#5');
    this.noteIdNames.set(91, 'G5');
    this.noteIdNames.set(92, 'G#5');
    this.noteIdNames.set(93, 'A5');
    this.noteIdNames.set(94, 'A#5');
    this.noteIdNames.set(95, 'B5');
    this.noteIdNames.set(96, 'C6');
    this.noteIdNames.set(97, 'C#6');
    this.noteIdNames.set(98, 'D6');
    this.noteIdNames.set(99, 'D#6');
    this.noteIdNames.set(100, 'E6');
    this.noteIdNames.set(101, 'F6');
    this.noteIdNames.set(102, 'F#6');
    this.noteIdNames.set(103, 'G6');
    this.noteIdNames.set(104, 'G#6');
    this.noteIdNames.set(105, 'A6');
    this.noteIdNames.set(106, 'A#6');
    this.noteIdNames.set(107, 'B6');
    this.noteIdNames.set(108, 'C7');
    this.noteIdNames.set(109, 'C#7');
    this.noteIdNames.set(110, 'D7');
    this.noteIdNames.set(111, 'D#7');
    this.noteIdNames.set(112, 'E7');
    this.noteIdNames.set(113, 'F7');
    this.noteIdNames.set(114, 'F#7');
    this.noteIdNames.set(115, 'G7');
    this.noteIdNames.set(116, 'G#7');
    this.noteIdNames.set(117, 'A7');
    this.noteIdNames.set(118, 'A#7');
    this.noteIdNames.set(119, 'B7');
    this.noteIdNames.set(120, 'C8');
    this.noteIdNames.set(121, 'C#8');
    this.noteIdNames.set(122, 'D8');
    this.noteIdNames.set(123, 'D#8');
    this.noteIdNames.set(124, 'E8');
    this.noteIdNames.set(125, 'F8');
    this.noteIdNames.set(126, 'F#8');
    this.noteIdNames.set(127, 'G8');
  }

  private loadSettings() {
    this.settingsService.getSettings().map(result => {
      this.settings = result;

      this.settingsService.getMidiInDevices().subscribe((response) => {
        this.midiInDevices = response;
      });

      this.settingsService.getMidiOutDevices().subscribe((response) => {
        this.midiOutDevices = response;
      });
    }).subscribe();
  }

  ngOnInit() {
    this.loadSettings();

    this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt) {
    return evt.related.className.indexOf('no-sortjs') === -1;
  }

  addMidiControl() {
    let midiControl: MidiControl = new MidiControl();
    this.settings.midiControlList.push(midiControl);
  }

  deleteMidiControl(midiControlIndex: number) {
    this.settings.midiControlList.splice(midiControlIndex, 1);
  }

  midiIdToNote(id: number): string {
    return this.noteIdNames.get(id);
  }

  testControl(midiControl: MidiControl): void {
    this.http.post('midi/test-control?command=144&channel=' + midiControl.channelFrom + '&note=' + midiControl.noteFrom + '&velocity=' + 127, null).subscribe((result) => {
      this.translateService.get(['settings.midi-control-test-success', 'settings.midi-control-test-success-title']).subscribe(result => {
        this.toastrService.success(result['settings.midi-control-test-success'], result['settings.midi-control-test-success-title']);
      });
    });
  }

}
