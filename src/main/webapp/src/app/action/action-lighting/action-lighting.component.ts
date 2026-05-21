import { Component, Input, OnInit } from "@angular/core";
import { ActionLighting } from "../../models/action-lighting";
import { LightingActionUniverse } from "../../models/lighting-action-universe";
import { LightingActionChannelValue } from "../../models/lighting-action-channel-value";
import { SettingsService } from "../../services/settings.service";
import { LightingUniverse } from "../../models/lighting-universe";

@Component({
    selector: "app-action-lighting",
    templateUrl: "./action-lighting.component.html",
    styleUrl: "./action-lighting.component.scss",
    standalone: false
})
export class ActionLightingComponent implements OnInit {
  @Input()
  action: ActionLighting;

  lightingUniverseList: LightingUniverse[] = [];
  channelList: number[] = [];
  valueList: number[] = [];

  constructor(private settingsService: SettingsService) {
    for (let i = 0; i <= 511; i++) {
      this.channelList.push(i);
    }
    for (let i = 0; i <= 255; i++) {
      this.valueList.push(i);
    }

  }

  ngOnInit() {
    this.loadUniverseNameList();
  }

  private loadUniverseNameList() {
    this.settingsService.getSettings(true).subscribe((settings) => {
      this.lightingUniverseList = settings.lightingUniverseList;

      if (this.lightingUniverseList.length === 0) {
        return;
      }

      for (let lightingActionUniverse of this.action.lightingActionUniverseList) {
        if (!lightingActionUniverse.universeUuid) {
          lightingActionUniverse.universeUuid = this.lightingUniverseList[0].uuid;
        }
      }
    });
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt: any) {
    return evt.related.className.indexOf("no-sortjs") === -1;
  }

  addUniverse() {
    const lightingActionUniverse = new LightingActionUniverse();
    if (this.lightingUniverseList.length > 0) {
      lightingActionUniverse.universeUuid = this.lightingUniverseList[0].uuid;
    }
    this.action.lightingActionUniverseList.push(lightingActionUniverse);
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
