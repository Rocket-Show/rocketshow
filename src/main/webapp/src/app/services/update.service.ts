import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { Injectable } from '@angular/core';
import { Version } from '../models/version';

@Injectable()
export class UpdateService {

  constructor(private http: HttpClient) { }

  // Get the version of the device
  getCurrentVersion(): Observable<Version> {
    return this.http.get('system/current-version')
    .map(response => {
      return new Version(response);
    });
  }

  // Get the latest available version
  getRemoteVersion(): Observable<Version> {
    return this.http.get('system/remote-version')
    .map(response => {
      return new Version(response);
    });
  }

  doUpdate(): Observable<null> {
    return this.http.post('system/update', null).map(() => {
      return null;
    });
  }

  finishUpdate(): Observable<null> {
    return this.http.post('session/dismiss-update-finished', null).map(() => {
      return null;
    });
  }

}
