import { MidiMapping } from './../models/midi-mapping';
import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'app-midi-mapping',
  templateUrl: './midi-mapping.component.html',
  styleUrls: ['./midi-mapping.component.scss']
})
export class MidiMappingComponent implements OnInit {
  @Input() midiMapping: MidiMapping;

  constructor() { }

  ngOnInit() {
  }

}
