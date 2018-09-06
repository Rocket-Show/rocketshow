import { DiskSpace } from './../models/disk-space';
import { Observable } from 'rxjs/Observable';
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class DiskSpaceService {

  constructor(private http: HttpClient) { }

  getDiskSpace(): Observable<DiskSpace> {
    return this.http.get('system/disk-space')
    .map(result => {
      return new DiskSpace(result);
    });
  }

}
