import { MidiMapping } from './../models/midi-mapping';
import { Component, OnInit, Input } from '@angular/core';

@Component({
    selector: 'app-midi-mapping',
    templateUrl: './midi-mapping.component.html',
    styleUrls: ['./midi-mapping.component.scss'],
    standalone: false
})
export class MidiMappingComponent implements OnInit {
  @Input() midiMapping: MidiMapping;

  // Labels above the fields, for narrow places like the routing dialog, where a
  // label beside its field broke mid-word
  @Input() stacked = false;

  constructor() { }

  ngOnInit() {
  }

}
