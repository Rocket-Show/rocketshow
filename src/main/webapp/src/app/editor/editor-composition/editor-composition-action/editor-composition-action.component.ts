import { Component, OnInit } from "@angular/core";
import { ActionTriggerComposition } from "../../../models/action-trigger-composition";
import { Subject } from "rxjs";
import { BsModalRef } from "ngx-bootstrap/modal";
import { Composition } from "../../../models/composition";

@Component({
  selector: "app-editor-composition-action",
  templateUrl: "./editor-composition-action.component.html",
  styleUrl: "./editor-composition-action.component.scss",
})
export class EditorCompositionActionComponent implements OnInit {
  selectUndefinedOptionValue: any = undefined;

  actionIndex: number;
  actionTrigger: ActionTriggerComposition;
  composition: Composition;

  onClose: Subject<number>;

  constructor(private bsModalRef: BsModalRef) {}

  ngOnInit() {
    this.onClose = new Subject();
  }

  public ok(): void {
    this.onClose.next(1);
    this.bsModalRef.hide();
  }

  public cancel(): void {
    this.onClose.next(2);
    this.bsModalRef.hide();
  }
}
