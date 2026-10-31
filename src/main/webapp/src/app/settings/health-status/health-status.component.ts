import { Component, Input } from "@angular/core";
import { HealthStatus } from "../../models/health-status";

// The health of the unit, as the watchdog and the updates see it. Shown on the provisioning and in the advanced
// settings, which load it.
@Component({
  selector: "app-health-status",
  templateUrl: "./health-status.component.html",
  standalone: false,
})
export class HealthStatusComponent {
  @Input() health: HealthStatus;
  @Input() loading: boolean = false;
}
