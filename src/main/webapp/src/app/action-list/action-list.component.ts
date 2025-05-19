import { Component, Input } from "@angular/core";
import { Action } from "../models/action";
import { ActionNull } from "../models/action-null";

@Component({
  selector: "app-action-list",
  templateUrl: "./action-list.component.html",
  styleUrl: "./action-list.component.scss",
})
export class ActionListComponent {
  @Input()
  actionList: Action[] = [];

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt) {
    return evt.related.className.indexOf("no-sortjs") === -1;
  }

  addAction() {
    let action = new ActionNull();
    this.actionList.push(action);
  }

  deleteAction(actionIndex: number) {
    this.actionList.splice(actionIndex, 1);
  }

  onActionChange(event: { index: number; newAction: Action }) {
    this.actionList[event.index] = event.newAction;
  }
}
