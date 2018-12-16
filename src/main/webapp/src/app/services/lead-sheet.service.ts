import { Subject } from 'rxjs';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class LeadSheetService {

  showLeadSheet: boolean = false;

  doShow: Subject<void> = new Subject<void>();

  constructor() { }

  show() {
    this.showLeadSheet = true;
    this.doShow.next();
  }

  close() {
    this.showLeadSheet = false;
  }

  isShow(): boolean {
    return this.showLeadSheet;
  }

}
