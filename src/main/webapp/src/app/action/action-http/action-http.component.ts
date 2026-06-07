import { Component, Input, SimpleChanges } from "@angular/core";
import { ActionHttp } from "../../models/action-http";

@Component({
    selector: "app-action-http",
    templateUrl: "./action-http.component.html",
    styleUrl: "./action-http.component.scss",
    standalone: false
})
export class ActionHttpComponent {
  @Input()
  action: ActionHttp;

  methodList: string[] = [];

  // Editable array for ngModel binding
  headerEntries: { key: string; value: string }[] = [];

  constructor() {
    this.methodList.push("GET");
    this.methodList.push("POST");
    this.methodList.push("PUT");
    this.methodList.push("DELETE");
    this.methodList.push("PATCH");
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes["action"]) {
      this.syncFromMap();
    }
  }

  syncFromMap(): void {
    this.headerEntries = Array.from(this.action.headerList.entries()).map(
      ([key, value]) => ({ key, value })
    );
  }

  syncToMap(): void {
    this.action.headerList.clear();
    this.headerEntries.forEach((entry) => {
      if (entry.key.trim()) {
        this.action.headerList.set(entry.key, entry.value);
      }
    });
  }

  addHeader(): void {
    this.headerEntries.push({ key: "", value: "" });
    this.syncToMap();
  }

  removeHeader(index: number): void {
    this.headerEntries.splice(index, 1);
    this.syncToMap();
  }

  onEntryChange(): void {
    this.syncToMap();
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt: any) {
    return evt.related.className.indexOf("no-sortjs") === -1;
  }
}
