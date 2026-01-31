import { Component, EventEmitter, Input, Output } from "@angular/core";
import { Action } from "../models/action";
import { ActionNull } from "../models/action-null";
import { UuidService } from "../services/uuid.service";
import { SettingsService } from "../services/settings.service";
import { ActionTransport } from "../models/action-transport";
import { ActionSystem } from "../models/action-system";
import { ActionMidi } from "../models/action-midi";
import { ActionLighting } from "../models/action-lighting";
import { ActionRaspberryGpio } from "../models/action-raspberry-gpio";
import { ActionHttp } from "../models/action-http";

@Component({
    selector: "app-action",
    templateUrl: "./action.component.html",
    styleUrl: "./action.component.scss",
    standalone: false
})
export class ActionComponent {
  @Input()
  action: Action;

  @Input()
  index: number;

  @Output()
  change = new EventEmitter<{ index: number; newAction: Action }>();

  @Output()
  delete = new EventEmitter<number>();

  uuid: string;

  constructor(
    private uuidService: UuidService,
    public settingsService: SettingsService
  ) {
    this.uuid = this.uuidService.getUuid();
  }

  onActionTypeChange(newValue: string): void {
    if (newValue === this.action.type) {
      return;
    }

    this.action.type = newValue;

    if (newValue === "NULL") {
      this.action = new ActionNull(this.action);
    } else if (newValue === "SYSTEM") {
      this.action = new ActionSystem(this.action);
    } else if (newValue === "TRANSPORT") {
      this.action = new ActionTransport(this.action);
    } else if (newValue === "MIDI") {
      this.action = new ActionMidi(this.action);
    } else if (newValue === "LIGHTING") {
      this.action = new ActionLighting(this.action);
    } else if (newValue === "RASPBERRY_GPIO") {
      this.action = new ActionRaspberryGpio(this.action);
    } else if (newValue === "HTTP") {
      this.action = new ActionHttp(this.action);
    }

    this.change.emit({ index: this.index, newAction: this.action });
  }
}
