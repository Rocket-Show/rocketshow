import { ToastGeneralErrorService } from './../../services/toast-general-error.service';
import { TranslateService } from '@ngx-translate/core';
import { BsModalService } from 'ngx-bootstrap/modal';
import { CompositionService } from './../../services/composition.service';
import { Set } from './../../models/set';
import { Observable, Subscription } from 'rxjs';
import { map, catchError, finalize } from "rxjs/operators";
import { PendingChangesDialogService } from './../../services/pending-changes-dialog.service';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { WarningDialogService } from '../../services/warning-dialog.service';
import { Composition } from '../../models/composition';
import { ToastrService } from 'ngx-toastr';
import { Settings } from '../../models/settings';
import { SettingsService } from '../../services/settings.service';
import { formatTimecode, parseTimecode } from '../../timecode';

@Component({
    selector: 'app-editor-set',
    templateUrl: './editor-set.component.html',
    styleUrls: ['./editor-set.component.scss'],
    standalone: false
})
export class EditorSetComponent implements OnInit, OnDestroy {

  private subscriptions: Subscription[] = [];

  searchName: string = '';

  loadingSet: boolean = false;
  savingSet: boolean = false;

  private sets: Set[];
  filteredSets: Set[];
  currentSet: Set;

  filteredCompositions: Composition[];
  availableCompositions: Composition[];

  loadingCompositions: boolean = false;
  loadingSets: boolean = false;

  // The set, as it was when we loaded it
  initialSet: Set;

  settings: Settings;

  constructor(
    private compositionService: CompositionService,
    private modalService: BsModalService,
    private warningDialogService: WarningDialogService,
    private pendingChangesDialogService: PendingChangesDialogService,
    private toastrService: ToastrService,
    private translateService: TranslateService,
    private settingsService: SettingsService,
    private toastGeneralErrorService: ToastGeneralErrorService) {
  }

  ngOnInit() {
    this.settingsService.getSettings().subscribe((settings: Settings) => {
      this.settings = settings;
    });

    this.loadSets();
    this.loadAvailableCompositions();

    // Subscribe to composition changes
    this.subscriptions.push(this.compositionService.compositionsChanged.subscribe(() => {
      this.loadAvailableCompositions();
    }));

    // The open set contains a renamed or deleted composition like its saved version on the device, keeping its
    // unsaved changes. Saved with the previous name, the set would lose the composition.
    this.subscriptions.push(this.compositionService.compositionRenamed.subscribe((renamed) => {
      for (let set of this.openSets()) {
        for (let composition of set.compositionList) {
          if (composition.name == renamed.previousName) {
            composition.name = renamed.name;
          }
        }
      }
    }));

    this.subscriptions.push(this.compositionService.compositionDeleted.subscribe((name) => {
      for (let set of this.openSets()) {
        for (let i = set.compositionList.length - 1; i >= 0; i--) {
          if (set.compositionList[i].name == name) {
            set.compositionList.splice(i, 1);
          }
        }
      }
    }));
  }

  ngOnDestroy() {
    for (let subscription of this.subscriptions) {
      subscription.unsubscribe();
    }
  }

  // The open set, as it's edited and as it was loaded
  private openSets(): Set[] {
    return [this.currentSet, this.initialSet].filter((set) => set);
  }

  private loadAvailableCompositions() {
    this.loadingCompositions = true;

    this.compositionService.getCompositions(true).subscribe((compositions: Composition[]) => {
      this.availableCompositions = compositions;
      this.filterCompositions();

      this.loadingCompositions = false;
    });
  }

  private loadSets() {
    this.loadingSets = true;

    this.compositionService.getSets(true).subscribe((sets: Set[]) => {
      this.sets = sets;
      this.filterSets();

      this.loadingSets = false;
    });
  }

  // Filter the set list
  filterSets(searchValue?: string) {
    if (!searchValue) {
      this.filteredSets = this.sets;
      return;
    }

    this.filteredSets = [];

    for (let set of this.sets) {
      if (set.name.toLowerCase().indexOf(searchValue.toLowerCase()) !== -1) {
        this.filteredSets.push(set);
      }
    }
  }

  // Filter the available composition list
  filterCompositions(searchValue?: string) {
    if (!searchValue) {
      this.filteredCompositions = this.availableCompositions;
      return;
    }

    this.filteredCompositions = [];

    for (let composition of this.availableCompositions) {
      if (composition.name.toLowerCase().indexOf(searchValue.toLowerCase()) !== -1) {
        this.filteredCompositions.push(composition);
      }
    }
  }

  private copyInitialSet() {
    let setString = JSON.stringify(this.currentSet);

    this.currentSet = new Set(JSON.parse(setString));
    this.initialSet = new Set(JSON.parse(setString));

    for (const composition of this.currentSet.compositionList) {
      this.formatTimecodeStart(composition);
    }
  }

  checkPendingChanges(): Observable<boolean> {
    return this.pendingChangesDialogService.check(this.initialSet, this.currentSet, 'editor.warning-set-changes');
  }

  // Select a set
  selectSet(set: Set) {
    if (this.currentSet && this.currentSet.name == set.name) {
      return;
    }

    this.checkPendingChanges().pipe(map(result => {
      if (result) {
        // Load the details of the selected set
        this.loadingSet = true;

        this.compositionService.getSet(set.name).subscribe((set: Set) => {
          this.currentSet = set;

          this.copyInitialSet();
          this.loadingSet = false;
        });
      }
    })).subscribe();
  }

  // Unselect a set
  unselect() {
    this.currentSet = undefined;
    this.initialSet = undefined;
  }

  // Create a new set
  createSet() {
    this.currentSet = new Set();
    this.copyInitialSet();
  }

  private saveApi(set: Set) {
    this.savingSet = true;

    this.compositionService.saveSet(set).pipe(map(() => {
      this.loadSets();
      this.copyInitialSet();

      this.translateService.get(['editor.toast-set-save-success', 'editor.toast-save-success-title']).subscribe(result => {
        this.toastrService.success(result['editor.toast-set-save-success'], result['editor.toast-save-success-title']);
      });
    }),
    catchError((err) => {
      return this.toastGeneralErrorService.show(err);
    }),
    finalize(() => {
      this.savingSet = false;
    }))
    .subscribe();
  }

  // Save a new set
  save(set: Set) {
    this.currentSet.name = this.currentSet.name.replace(/\//g, '').replace(/\\/g, '');

    if(this.currentSet.name.length < 1) {
      return;
    }

    // Delete the old set, if the name changed
    if (this.initialSet && this.initialSet.name && this.initialSet.name != set.name && this.initialSet.name.length > 0) {
      this.compositionService.deleteSet(this.initialSet.name).pipe(map(() => {
        this.saveApi(set);
      })
      ,catchError((err) => {
        return this.toastGeneralErrorService.show(err);
      }))
      .subscribe();
    } else {
      this.saveApi(set);
    }
  }

  // Delete the set
  delete(set: Set) {
    this.warningDialogService.show('editor.warning-delete-set').pipe(map(result => {
      if (result) {
        this.compositionService.deleteSet(this.initialSet.name).pipe(map(() => {
          this.unselect();
          this.loadSets();

          this.translateService.get(['editor.toast-set-delete-success', 'editor.toast-delete-success-title']).subscribe(result => {
            this.toastrService.success(result['editor.toast-set-delete-success'], result['editor.toast-delete-success-title']);
          });
        }),
        catchError((err) => {
          return this.toastGeneralErrorService.show(err);
        }))
        .subscribe();
      }
    })).subscribe();
  }

  showAvailableComposition(composition: Composition): boolean {
    if (this.currentSet) {
      for (let setComposition of this.currentSet.compositionList) {
        if (setComposition.name == composition.name) {
          return false;
        }
      }
    }

    return true;
  }

  addComposition(composition: Composition) {
    // Copy it: the set-specific settings (auto start, timecode start) belong to this entry, not to
    // the composition itself or to another entry of the same composition
    this.currentSet.compositionList.push(
      Object.assign(new Composition(), composition, {
        timecodeStartMillis: this.getNextTimecodeStartMillis(),
        // Start out inheriting the composition's own number and cue number
        midiNumber: undefined,
        showControlCue: undefined,
      })
    );

    this.formatTimecodeStart(
      this.currentSet.compositionList[this.currentSet.compositionList.length - 1]
    );
  }

  // Default a newly added composition to start right after the previous one, which is the usual
  // layout of a set on a continuous timecode
  private getNextTimecodeStartMillis(): number {
    const compositionList: Composition[] = this.currentSet.compositionList;

    if (!compositionList.length) {
      return 0;
    }

    const last: Composition = compositionList[compositionList.length - 1];

    return (last.timecodeStartMillis || 0) + (last.durationMillis || 0);
  }

  showTimecodeStart(): boolean {
    return (
      this.settings?.midiTimecodeMode == 'SLAVE' &&
      this.settings?.midiTimecodeSlaveMapping == 'SET'
    );
  }

  // The show control cue number falls back to the MIDI number, so the number is also worth showing
  // when only show control is used
  showMidiNumber(): boolean {
    return (
      this.settings?.midiCompositionSelectionEnabled ||
      this.settings?.midiShowControlEnabled
    );
  }

  showShowControlCue(): boolean {
    return this.settings?.midiShowControlEnabled;
  }

  hasSetOverrides(): boolean {
    return this.showTimecodeStart() || this.showMidiNumber() || this.showShowControlCue();
  }

  onTimecodeStartChange(composition: Composition, value: string) {
    // Keep what was typed as it was typed; it is only re-formatted once the field is left
    composition.timecodeStartText = value;
    composition.timecodeStartMillis = parseTimecode(value);
  }

  formatTimecodeStart(composition: Composition) {
    composition.timecodeStartText = formatTimecode(composition.timecodeStartMillis);
  }

  removeComposition(composition: Composition) {
    for (var i = this.currentSet.compositionList.length - 1; i >= 0; i--) {
      if (this.currentSet.compositionList[i].name == composition.name) {
        this.currentSet.compositionList.splice(i, 1);
      }
    }
  }

  toggleAutoStartNextComposition(composition: Composition) {
    composition.autoStartNextComposition = !composition.autoStartNextComposition;
  }

}
