import { EditorCompositionComponent } from './editor-composition/editor-composition.component';
import { Observable } from 'rxjs/Observable';
import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ComponentCanDeactivate } from '../pending-changes.guard';
import { EditorSetComponent } from './editor-set/editor-set.component';

@Component({
  selector: 'app-editor',
  templateUrl: './editor.component.html',
  styleUrls: ['./editor.component.scss']
})
export class EditorComponent implements OnInit, ComponentCanDeactivate {

  @ViewChild(EditorCompositionComponent) editorCompositionComponent: EditorCompositionComponent;
  @ViewChild(EditorSetComponent) editorSetComponent: EditorSetComponent;
  
  constructor() {
  }

  ngOnInit() {
  }

  canDeactivate(): Observable<boolean> {
    return this.editorCompositionComponent.checkPendingChanges()
    .flatMap(result => {
      if(result){
        return this.editorSetComponent.checkPendingChanges();
      } else {
        return Observable.of(false);
      }
    });
  }

}
