import { BsModalRef } from "ngx-bootstrap/modal";
import { Observable, Subject } from "rxjs";
import { Component, OnInit } from "@angular/core";

@Component({
  selector: "app-warning-dialog",
  templateUrl: "./warning-dialog.component.html",
  styleUrls: ["./warning-dialog.component.scss"],
})
export class WarningDialogComponent implements OnInit {
  onClose: Subject<number> = new Subject();

  message: string;

  constructor(private bsModalRef: BsModalRef) {}

  ngOnInit() {}

  public ok(): void {
    this.onClose.next(1);
    this.bsModalRef.hide();
  }

  public cancel(): void {
    this.onClose.next(2);
    this.bsModalRef.hide();
  }
}
