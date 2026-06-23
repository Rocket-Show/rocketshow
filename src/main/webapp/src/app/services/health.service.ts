import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { HealthStatus } from "../models/health-status";

@Injectable()
export class HealthService {
  constructor(private http: HttpClient) {}

  // Get the current health status (available without security).
  getHealth(): Observable<HealthStatus> {
    return this.http.get("system/health").pipe(
      map((response) => new HealthStatus(response))
    );
  }
}
