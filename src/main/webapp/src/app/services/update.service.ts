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
        (this.settingsService.settings?.updateTestBranch ?? false)
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
        (this.settingsService.settings?.updateTestBranch ?? false),
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

  isVersionNewer(version: { version: string }, referenceVersion: { version: string }): boolean {
    if (!version || !referenceVersion || !version.version || !referenceVersion.version) {
      return false;
    }

    return this.isVersionStringNewer(version.version, referenceVersion.version);
  }

  isVersionStringNewer(version: string, referenceVersion: string): boolean {
    if (!version || !referenceVersion) {
      return false;
    }

    return this.versionCompare(version, referenceVersion) > 0;
  }

  // Taken from https://stackoverflow.com/questions/6832596/how-to-compare-software-version-number-using-js-only-number
  private versionCompare(v1: string, v2: string, options?) {
    let lexicographical = options && options.lexicographical;
    let zeroExtend = options && options.zeroExtend;
    let v1parts: any[] = v1.split(".");
    let v2parts: any[] = v2.split(".");

    function isValidPart(x) {
      return (lexicographical ? /^\d+[A-Za-z]*$/ : /^\d+$/).test(x);
    }

    if (!v1parts.every(isValidPart) || !v2parts.every(isValidPart)) {
      return NaN;
    }

    if (zeroExtend) {
      while (v1parts.length < v2parts.length) v1parts.push("0");
      while (v2parts.length < v1parts.length) v2parts.push("0");
    }

    if (!lexicographical) {
      v1parts = v1parts.map(Number);
      v2parts = v2parts.map(Number);
    }

    for (var i = 0; i < v1parts.length; ++i) {
      if (v2parts.length == i) {
        return 1;
      }

      if (v1parts[i] == v2parts[i]) {
        continue;
      }
      else if (v1parts[i] > v2parts[i]) {
        return 1;
      }
      else {
        return -1;
      }
    }

    if (v1parts.length != v2parts.length) {
      return -1;
    }

    return 0;
  }

}
