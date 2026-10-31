import { Component, OnInit } from "@angular/core";
import { BsModalRef } from "ngx-bootstrap/modal";
import { HealthStatus } from "../../models/health-status";
import { HealthService } from "../../services/health.service";
import { ToastGeneralErrorService } from "../../services/toast-general-error.service";

// The health of the unit from the advanced settings. Only reads, so unlike the self-test it is safe during a show.
@Component({
  selector: "app-health-dialog",
  templateUrl: "./health-dialog.component.html",
  standalone: false,
})
export class HealthDialogComponent implements OnInit {
  health: HealthStatus;
  loading: boolean = false;

  constructor(
    private bsModalRef: BsModalRef,
    private healthService: HealthService,
    private toastGeneralErrorService: ToastGeneralErrorService
  ) {}

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;

    this.healthService.getHealth().subscribe({
      next: (health) => {
        this.health = health;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.toastGeneralErrorService.show(err);
      },
    });
  }

  close(): void {
    this.bsModalRef.hide();
  }
}
