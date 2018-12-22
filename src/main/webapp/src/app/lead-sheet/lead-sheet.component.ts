import { LeadSheetService } from './../services/lead-sheet.service';
import { Subscription, timer } from 'rxjs';
import { State } from './../models/state';
import { StateService } from './../services/state.service';
import { Component, OnInit, ElementRef, ViewChild } from '@angular/core';

@Component({
  selector: 'app-lead-sheet',
  templateUrl: './lead-sheet.component.html',
  styleUrls: ['./lead-sheet.component.scss']
})
export class LeadSheetComponent implements OnInit {

  @ViewChild('img') img: ElementRef;

  currentState: State = new State();
  imgStyle: any = {};
  playUpdateSubscription: Subscription;
  lastPlayTime: Date;

  constructor(
    public stateService: StateService,
    public leadSheetService: LeadSheetService
  ) { }

  ngOnInit() {
    // Subscribe to the state-changed service
    this.stateService.state.subscribe((state: State) => {
      this.stateChanged(state);
    });

    // Load the current state
    this.stateService.getState().subscribe((state: State) => {
      this.stateChanged(state);
      this.currentState = state;
    });

    this.leadSheetService.doShow.subscribe(() => {
      // Wait one tick for the image height to be calculated correctly after the show
      setTimeout(() => {
        this.update();
      }, 0);
    });
  }

  imgLoaded() {
    this.update();

    // Wait one tick for the image height to be calculated correctly
    setTimeout(() => {
      this.update();
    }, 0);
  }

  update() {
    if (!this.leadSheetService.showLeadSheet) {
      return;
    }

    let imgHeight = this.img.nativeElement.height;
    let windowHeight = window.innerHeight - 16 * 2 /* Padding */;

    let positionMillis = this.currentState.positionMillis;

    if (this.lastPlayTime) {
      positionMillis = new Date().getTime() - this.lastPlayTime.getTime() + this.currentState.positionMillis;
    }

    let lengthMillis = this.currentState.currentCompositionDurationMillis;

    let scrollHeight = imgHeight - windowHeight;

    if (scrollHeight < 0 || lengthMillis == 0) {
      return;
    }

    let pos = scrollHeight * positionMillis / lengthMillis * -1;

    this.imgStyle = { 'margin-top': pos + 'px' };
  }

  private stateChanged(newState: State) {
    if (newState.playState == 'PLAYING' && this.currentState.playState != 'PLAYING') {
      if (this.playUpdateSubscription) {
        this.playUpdateSubscription.unsubscribe;
      }

      // Save the last time, we started the composition. Don't use device time, as it may be wrong.
      this.lastPlayTime = new Date();

      let playUpdater = timer(0, 10);
      this.playUpdateSubscription = playUpdater.subscribe(() => {
        this.update();
      });
    }

    if (newState.playState == 'STOPPING' || newState.playState == 'STOPPPED' || newState.playState == 'PAUSED') {
      if (this.playUpdateSubscription) {
        this.lastPlayTime = undefined;
        this.playUpdateSubscription.unsubscribe();
      }
    }

    this.currentState = newState;

    this.update();
  }

  close() {
    this.leadSheetService.showLeadSheet = false;
  }

}
