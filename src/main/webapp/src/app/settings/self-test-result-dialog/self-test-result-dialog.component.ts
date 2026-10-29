import { Component } from "@angular/core";
import { BsModalRef } from "ngx-bootstrap/modal";
import { SelfTestResult } from "../../models/self-test-result";

// The checks of a self-test of the unit
@Component({
  selector: "app-self-test-result-dialog",
  templateUrl: "./self-test-result-dialog.component.html",
  styleUrl: "./self-test-result-dialog.component.scss",
  standalone: false,
})
export class SelfTestResultDialogComponent {
  selfTestResult: SelfTestResult;

  constructor(private bsModalRef: BsModalRef) {}

  close(): void {
    this.bsModalRef.hide();
  }
}
