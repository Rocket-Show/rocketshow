import { DiskSpace } from './../models/disk-space';
import { Observable } from 'rxjs';
import { map } from "rxjs/operators";
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class DiskSpaceService {

  constructor(private http: HttpClient) { }

  getDiskSpace(): Observable<DiskSpace> {
    return this.http.get('system/disk-space')
    .pipe(map(result => {
      return new DiskSpace(result);
    }));
  }

}
