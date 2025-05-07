import { Component, Input } from "@angular/core";
import { ActionLighting } from "../../models/action-lighting";

@Component({
  selector: "app-action-lighting",
  templateUrl: "./action-lighting.component.html",
  styleUrl: "./action-lighting.component.scss",
})
export class ActionLightingComponent {
  @Input()
  action: ActionLighting;
}
