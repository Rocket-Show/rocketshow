import { Component, Input } from "@angular/core";
import { ActionHttp } from "../../models/action-http";

@Component({
  selector: "app-action-http",
  templateUrl: "./action-http.component.html",
  styleUrl: "./action-http.component.scss",
})
export class ActionHttpComponent {
  @Input()
  action: ActionHttp;
}
