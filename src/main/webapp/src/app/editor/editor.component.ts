import { EditorCompositionComponent } from './editor-composition/editor-composition.component';
import { Observable, of } from 'rxjs';
import { flatMap } from "rxjs/operators";
import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ComponentCanDeactivate } from '../pending-changes.guard';
import { EditorSetComponent } from './editor-set/editor-set.component';

@Component({
  selector: 'app-editor',
  templateUrl: './editor.component.html',
  styleUrls: ['./editor.component.scss']
})
export class EditorComponent implements OnInit, ComponentCanDeactivate {

  @ViewChild(EditorCompositionComponent, {static: false}) editorCompositionComponent: EditorCompositionComponent;
  @ViewChild(EditorSetComponent, {static: false}) editorSetComponent: EditorSetComponent;
  
  constructor() {
  }

  ngOnInit() {
  }

  canDeactivate(): Observable<boolean> {
    return this.editorCompositionComponent.checkPendingChanges()
    .pipe(flatMap(result => {
      if(result){
        return this.editorSetComponent.checkPendingChanges();
      } else {
        return of(false);
      }
    }));
  }

}
