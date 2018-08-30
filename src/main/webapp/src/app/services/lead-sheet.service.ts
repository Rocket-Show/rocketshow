import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class LeadSheetService {

  showLeadSheet: boolean = false;

  constructor() { }
}
