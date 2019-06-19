import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { map } from "rxjs/operators";
import { Injectable } from '@angular/core';
import { Session } from './../models/session';

@Injectable()
export class SessionService {

  session: Session;
  observable: Observable<Session>;

  constructor(private http: HttpClient) { }

  getSession(clearCache: boolean = false): Observable<Session> {
    if(clearCache) {
      this.session = undefined;
      this.observable = undefined;
    }

    if (this.session) {
      return of(this.session);
    }

    if(this.observable) {
      return this.observable;
    }

    this.observable = this.http.get('session')
    .pipe(map(result => {
      this.session = new Session(result);
      this.observable = undefined;

      return this.session;
    }));

    return this.observable;
  }

  introFinished(): Observable<Object> {
    return this.http.post('session/wizard-finished', undefined);
  }

  introReset(): Observable<Object> {
    return this.http.post('session/wizard-reset', undefined);
  }

  setAutoSelectNextComposition(value: boolean): Observable<Object> {
    return this.http.post('session/set-auto-select-next-composition?value=' + value, undefined);
  }

}
