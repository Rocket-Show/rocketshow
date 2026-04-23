import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { Injectable } from "@angular/core";
import { Version } from "../models/version";
import { SettingsService } from "./settings.service";
import { UpdateState } from "../models/update-state";

@Injectable()
export class UpdateService {
  constructor(
    private http: HttpClient,
    private settingsService: SettingsService
  ) { }

  // Get the version of the device
  getCurrentVersion(): Observable<Version> {
    return this.http.get("system/current-version").pipe(
      map((response) => {
        return new Version(response);
      })
    );
  }

  // Get the latest available version
  getRemoteVersion(): Observable<Version> {
    return this.http
      .get(
        "system/remote-version?testBranch=" +
        this.settingsService.settings.updateTestBranch
      )
      .pipe(
        map((response) => {
          return new Version(response);
        })
      );
  }

  doUpdate(): Observable<null> {
    return this.http
      .post(
        "system/update?testBranch=" +
        +this.settingsService.settings.updateTestBranch,
        null
      )
      .pipe(
        map(() => {
          return null;
        })
      );
  }

  getUpdateState(): Observable<UpdateState> {
    return this.http
      .get(
        "system/update-state"
      )
      .pipe(
        map((response) => {
          return new UpdateState(response);
        })
      );
  }

}
