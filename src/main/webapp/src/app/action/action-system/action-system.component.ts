import { Component, Input } from "@angular/core";
import { ActionSystem } from "../../models/action-system";

@Component({
    selector: "app-action-system",
    templateUrl: "./action-system.component.html",
    styleUrl: "./action-system.component.scss",
    standalone: false
})
export class ActionSystemComponent {
  @Input()
  action: ActionSystem;
}
