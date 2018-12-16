import { Observable, of } from 'rxjs';
import { map } from "rxjs/operators";
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OperatingSystemInformation } from '../models/operating-system-information';

@Injectable({
  providedIn: 'root'
})
export class OperatingSystemInformationService {

  operatingSystemInformation: OperatingSystemInformation;

  constructor(private http: HttpClient) { }

  getOperatingSystemInformation(): Observable<OperatingSystemInformation> {
    if(this.operatingSystemInformation) {
      return of(this.operatingSystemInformation);
    }

    return this.http.get('system/operating-system-information')
    .pipe(map(result => {
      return new OperatingSystemInformation(result);
    }));
  }

}
