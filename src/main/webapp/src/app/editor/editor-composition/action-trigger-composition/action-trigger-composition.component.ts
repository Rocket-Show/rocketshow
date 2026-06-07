import { Component, Input } from "@angular/core";
import { ActionTriggerComposition } from "../../../models/action-trigger-composition";

@Component({
    selector: "app-action-trigger-composition",
    templateUrl: "./action-trigger-composition.component.html",
    styleUrl: "./action-trigger-composition.component.scss",
    standalone: false
})
export class ActionTriggerCompositionComponent {
  @Input()
  trigger: ActionTriggerComposition;
}
