import { Component, Input } from "@angular/core";
import { ActionTransport } from "../../models/action-transport";
import { CompositionService } from "../../services/composition.service";
import { Composition } from "../../models/composition";

@Component({
    selector: "app-action-transport",
    templateUrl: "./action-transport.component.html",
    styleUrl: "./action-transport.component.scss",
    standalone: false
})
export class ActionTransportComponent {
  @Input()
  action: ActionTransport;

  public typeList: string[] = [];
  public compositionList: string[] = [];

  constructor(private compositionService: CompositionService) {
    this.typeList.push(
      "PLAY",
      "PLAY_AS_SAMPLE",
      "TOGGLE_PLAY",
      "PAUSE",
      "NEXT_COMPOSITION",
      "PREVIOUS_COMPOSITION",
      "STOP",
      "SELECT_COMPOSITION_BY_NAME",
      "SELECT_COMPOSITION_BY_NAME_AND_PLAY"
    );

    this.compositionService
      .getCompositions(true)
      .subscribe((compositionList: Composition[]) => {
        for (let composition of compositionList) {
          this.compositionList.push(composition.name);
        }
      });
  }
}
