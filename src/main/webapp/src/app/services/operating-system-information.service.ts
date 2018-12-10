import { Observable } from 'rxjs/Observable';
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
      return Observable.of(this.operatingSystemInformation);
    }

    return this.http.get('system/operating-system-information')
    .map(result => {
      return new OperatingSystemInformation(result);
    });
  }

}
