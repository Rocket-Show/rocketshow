import { Component, Input } from "@angular/core";
import { ActionNull } from "../../models/action-null";

@Component({
    selector: "app-action-null",
    templateUrl: "./action-null.component.html",
    styleUrl: "./action-null.component.scss",
    standalone: false
})
export class ActionNullComponent {
  @Input()
  action: ActionNull;
}
