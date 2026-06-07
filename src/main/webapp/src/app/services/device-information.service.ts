import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { DeviceInformation } from '../models/device-information';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';

@Injectable()
export class DeviceInformationService {

  deviceInformation: DeviceInformation;
  observable: Observable<DeviceInformation>;

  constructor(private http: HttpClient) {

  }

  getDeviceInformation(): Observable<DeviceInformation> {
    if (this.deviceInformation) {
      return of(this.deviceInformation);
    }

    if (this.observable) {
      return this.observable;
    }

    this.observable = this.http.get("system/device-information").pipe(
      map((response) => {
        if (!this.deviceInformation) {
          this.deviceInformation = new DeviceInformation(response);
        }
        this.observable = undefined;

        return this.deviceInformation;
      })
    );

    return this.observable;
  }

}
