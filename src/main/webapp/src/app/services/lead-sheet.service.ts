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
import { CompositionService } from './composition.service';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class LeadSheetService {

  showLeadSheet: boolean = false;
  doShow: Subject<void> = new Subject<void>();
  currentCompositionName: string;
  currentInstrumentUuid: string;
  currentLeadSheetUrl: string;

  constructor(
    private http: HttpClient,
    public stateService: StateService,
    private settingsPersonalService: SettingsPersonalService,
    private compositionService: CompositionService
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
    this.currentLeadSheetUrl = undefined;
    
    this.compositionService.getComposition(compositionName).subscribe(composition => {
      // Check, whether this composition has a lead sheet with the user's instrument UUID
      for(let leadSheet of composition.leadSheetList) {
        if(leadSheet.instrumentUuid == instrumentUuid) {
          // There is a lead sheet for the user available
          if (environment.name == 'dev') {
            this.currentLeadSheetUrl = 'http://' + environment.localBackend + '/';
          } else {
            this.currentLeadSheetUrl = '/'
          }

          this.currentLeadSheetUrl += 'api/lead-sheet/image?name=' + leadSheet.name;
          break;
        }
      }
    });

    this.currentCompositionName = compositionName;
    this.currentInstrumentUuid = instrumentUuid;
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
