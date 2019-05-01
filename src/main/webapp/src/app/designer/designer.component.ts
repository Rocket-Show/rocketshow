import { Component, OnInit } from '@angular/core';
import { Observable, of } from 'rxjs';

@Component({
  selector: 'app-designer',
  templateUrl: './designer.component.html',
  styleUrls: ['./designer.component.scss']
})
export class DesignerComponent implements OnInit {

  constructor() { }

  ngOnInit() {
  }

  canDeactivate(): Observable<boolean> {
    // TODO
    return of(true);
  }

}
