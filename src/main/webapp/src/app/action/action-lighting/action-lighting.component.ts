import { Component, Input } from "@angular/core";
import { ActionLighting } from "../../models/action-lighting";
import { LightingActionUniverse } from "../../models/lighting-action-universe";
import { LightingActionChannelValue } from "../../models/lighting-action-channel-value";

@Component({
    selector: "app-action-lighting",
    templateUrl: "./action-lighting.component.html",
    styleUrl: "./action-lighting.component.scss",
    standalone: false
})
export class ActionLightingComponent {
  @Input()
  action: ActionLighting;

  universeNameList: string[] = [];
  channelList: number[] = [];
  valueList: number[] = [];

  constructor() {
    for (let i = 0; i <= 511; i++) {
      this.channelList.push(i);
    }
    for (let i = 0; i <= 255; i++) {
      this.valueList.push(i);
    }

    // TODO build the universeNameList for the typeahead
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt: any) {
    return evt.related.className.indexOf("no-sortjs") === -1;
  }

  addUniverse() {
    this.action.lightingActionUniverseList.push(new LightingActionUniverse());
  }

  deleteUniverse(universeIndex: number) {
    this.action.lightingActionUniverseList.splice(universeIndex, 1);
  }

  deleteChannel(universe: LightingActionUniverse, channelIndex: number) {
    universe.channelValueList.splice(channelIndex, 1);
  }

  addChannel(universe: LightingActionUniverse) {
    let lightingActionChannelValue = new LightingActionChannelValue();
    lightingActionChannelValue.channel = 0;
    lightingActionChannelValue.value = 0;
    universe.channelValueList.push(lightingActionChannelValue);
  }
}
