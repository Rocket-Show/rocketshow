import { Subject } from 'rxjs';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { forkJoin } from 'rxjs';
import { map } from "rxjs/operators";
import { HttpClient } from '@angular/common/http';
import { LeadSheet } from '../models/lead-sheet';
import { StateService } from './state.service';
import { State } from '../models/state';
import { SettingsPersonalService } from './settings-personal.service';

@Injectable({
  providedIn: 'root'
})
export class LeadSheetService {

  showLeadSheet: boolean = false;
  leadSheetButtonVisible: boolean = false;
  doShow: Subject<void> = new Subject<void>();
  currentCompositionName: string;
  currentInstrumentUuid: string;
  currentLeadSheetImageBase64: string;

  constructor(
    private http: HttpClient,
    public stateService: StateService,
    private settingsPersonalService: SettingsPersonalService
  ) {
    this.stateService.state.subscribe((state: State) => {
      this.updateCurrentLeadSheet();
    });

    this.updateCurrentLeadSheet();

    this.settingsPersonalService.settingsChanged.subscribe(() => {
      this.updateCurrentLeadSheet();
    });
  }

  private loadCurrentLeadSheet(compositionName: string, instrumentUuid: string) {


    this.currentCompositionName = compositionName;
    this.currentInstrumentUuid = instrumentUuid;

    console.log(compositionName, instrumentUuid);
  }

  private updateCurrentLeadSheet() {
    // Check, whether the current composition or instrument have changed and
    // reload the current lead sheet, if necessary
    this.stateService.getState().subscribe(state => {
      let newCompositionName = state.currentCompositionName;
      let newInstrumentUuid = this.settingsPersonalService.getSettings().instrumentUuid;

      if(newCompositionName != this.currentCompositionName || newInstrumentUuid != this.currentInstrumentUuid) {
        this.loadCurrentLeadSheet(newCompositionName, newInstrumentUuid);
      }
    });
  }

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

  getLeadSheets(): Observable<LeadSheet[]> {
    return this.http.get('lead-sheet/list')
      .pipe(map((response: Array<Object>) => {
        let files: LeadSheet[] = [];

        for (let file of response) {
          files.push(new LeadSheet(file));
        }

        return files;
      }));
  }

  deleteLeadSheet(leadSheet: LeadSheet): Observable<void> {
    return this.http.post('lead-sheet/delete?name=' + leadSheet.name, undefined).pipe(map((response: Response) => {
      return null;
    }));
  }

}
