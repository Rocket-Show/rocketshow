import { Composition } from './../../../models/composition';
import { Subject } from 'rxjs/Subject';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-editor-composition-lead-sheet',
  templateUrl: './editor-composition-lead-sheet.component.html',
  styleUrls: ['./editor-composition-lead-sheet.component.scss']
})
export class EditorCompositionLeadSheetComponent implements OnInit {

  onClose: Subject<number>;
  composition: Composition;

  constructor() { }

  ngOnInit() {
    this.onClose = new Subject();
  }

}
