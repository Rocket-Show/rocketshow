import { Component, OnInit } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AppHttpInterceptor } from '../app-http-interceptor/app-http-interceptor';

@Component({
  selector: 'app-designer',
  templateUrl: './designer.component.html',
  styleUrls: ['./designer.component.scss']
})
export class DesignerComponent implements OnInit {

  constructor(
    public appHttpInterceptor: AppHttpInterceptor
  ) { }

  ngOnInit() {
  }

  canDeactivate(): Observable<boolean> {
    // TODO
    return of(true);
  }

}
