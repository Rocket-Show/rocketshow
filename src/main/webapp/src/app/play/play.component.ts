import { Session } from './../models/session';
import { SessionService } from './../services/session.service';
import { Composition } from './../models/composition';
import { CompositionService } from './../services/composition.service';
import { StateService } from './../services/state.service';
import { Set } from './../models/set';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { State } from '../models/state';
import { TransportService } from '../services/transport.service';
import { Subscription, timer } from 'rxjs';
import { map, finalize, catchError } from 'rxjs/operators';
import { ToastGeneralErrorService } from '../services/toast-general-error.service';
import { ActivityMidiService } from '../services/activity-midi.service';
import { ActivityMidi } from '../models/activity-midi';
import { SettingsService } from '../services/settings.service';
import { ActivityAudioService } from '../services/activity-audio.service';
import { ActivityAudio } from '../models/activity-audio';
import { ActivityAudioBus } from '../models/activity-audio-bus';
import { ActivityAudioChannel } from '../models/activity-audio-channel';
import { ActivityDmxService } from '../services/activity-dmx.service';
import { ActivityDmx } from '../models/activity-dmx';

@Component({
  selector: 'app-play',
  templateUrl: './play.component.html',
  styleUrls: ['./play.component.scss']
})
export class PlayComponent implements OnInit, OnDestroy {

  currentSet: Set;
  currentState: State = new State();

  session: Session;

  sets: Set[];

  positionMillis: number = 0;
  playTime: string = '00:00.000';
  playUpdateSubscription: Subscription;
  lastPlayTime: Date;

  // Is the user currently using the slider?
  sliding: boolean = false;

  manualCompositionSelection: boolean = false;

  totalPlayTime: string = '';

  loadingSet: boolean = false;

  activityMidiIn: boolean = false;
  activityMidiInStopTimeout: any;
  activityMidiOut: boolean = false;
  activityMidiOutStopTimeout: any;

  activityAudio: ActivityAudio;
  activityAudioStopTimeout: any;

  activityDmx: boolean = false;
  activityDmxStopTimeout: any;

  constructor(
    public stateService: StateService,
    private compositionService: CompositionService,
    private transportService: TransportService,
    private sessionService: SessionService,
    private toastGeneralErrorService: ToastGeneralErrorService,
    private activityMidiService: ActivityMidiService,
    public activityAudioService: ActivityAudioService,
    public activityDmxService: ActivityDmxService,
    public settingsService: SettingsService) {

    this.loadSettings();

    this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });
  }

  private loadSettings() {
    this.settingsService.getSettings().pipe(map(settings => {
      this.activityAudio = new ActivityAudio();

      for (let audioBus of settings.audioBusList) {
        let activityAudioBus = new ActivityAudioBus();
        activityAudioBus.name = audioBus.name;
        this.activityAudio.activityAudioBusList.push(activityAudioBus);

        for (var i = 0; i < audioBus.channels; i++) {
          let activityAudioChannel = new ActivityAudioChannel();
          activityAudioChannel.index = i;
          activityAudioBus.activityAudioChannelList.push(activityAudioChannel);
        }
      }
    })).subscribe();
  }

  resetChannelVolumes() {
    // Set all volumes to 0
    for (let bus of this.activityAudio.activityAudioBusList) {
      for (let channel of bus.activityAudioChannelList) {
        channel.volumeDb = -500;
      }
    }
  }

  ngOnInit() {
    // Subscribe to the state-changed service
    this.stateService.state.subscribe((state: State) => {
      this.stateChanged(state);
    });

    // Subscribe to the get connection service
    this.stateService.getsConnected.subscribe(() => {
      this.loadAllSets();
      this.loadCurrentSet();
    });

    // Load the current state
    this.stateService.getState().subscribe((state: State) => {
      this.stateChanged(state);
      this.currentState = state;
    });

    // Load the current session
    this.sessionService.getSession().subscribe(session => {
      this.session = session;
    });

    this.loadAllSets();
    this.loadCurrentSet();

    // Subscribe to MIDI activities
    this.activityMidiService.subject.subscribe((activityMidi: ActivityMidi) => {
      let decayMillis = 50;

      if (activityMidi.midiDirection == 'IN') {
        this.activityMidiIn = true;

        if (this.activityMidiInStopTimeout) {
          clearTimeout(this.activityMidiInStopTimeout);
          this.activityMidiInStopTimeout = undefined;
        }

        this.activityMidiInStopTimeout = setTimeout(() => {
          this.activityMidiInStopTimeout = undefined;
          this.activityMidiIn = false;
        }, decayMillis);
      } else if (activityMidi.midiDirection == 'OUT' && activityMidi.midiDestination != 'DMX') {
        // DMX is monitored separately

        this.activityMidiOut = true;

        if (this.activityMidiOutStopTimeout) {
          clearTimeout(this.activityMidiOutStopTimeout);
          this.activityMidiOutStopTimeout = undefined;
        }

        this.activityMidiOutStopTimeout = setTimeout(() => {
          this.activityMidiOutStopTimeout = undefined;
          this.activityMidiOut = false;
        }, decayMillis);
      }
    });
    this.activityMidiService.startMonitor();

    // Subscribe to audio activities
    this.activityAudioService.subject.subscribe((activityAudio: ActivityAudio) => {
      if (this.activityAudioStopTimeout) {
        clearTimeout(this.activityAudioStopTimeout);
        this.activityAudioStopTimeout = undefined;
      }

      this.activityAudioStopTimeout = setTimeout(() => {
        this.activityAudioStopTimeout = undefined;
        this.resetChannelVolumes();
      }, 200);

      // Map the audio activity from the backend into the frontend monitoring activity,
      // based on the settings (e.g. if more channels are played than specified in the settings,
      // they will not be shown)
      this.resetChannelVolumes()

      for (let settingsBus of this.activityAudio.activityAudioBusList) {
        for (let activityBus of activityAudio.activityAudioBusList) {
          if (settingsBus.name == activityBus.name) {
            for (let settingsChannel of settingsBus.activityAudioChannelList) {
              for (let activityChannel of activityBus.activityAudioChannelList) {
                if (settingsChannel.index == activityChannel.index) {
                  // Increase the sensitivity by factor 5 to make also more silent tracks visible
                  settingsChannel.volumeDb = activityChannel.volumeDb / 5;
                }
              }
            }
          }
        }
      }
    });
    this.activityAudioService.startMonitor();

    // Subscribe to MIDI activities
    this.activityDmxService.subject.subscribe((activityDmx: ActivityDmx) => {
      let decayMillis = 50;

      this.activityDmx = true;

      if (this.activityDmxStopTimeout) {
        clearTimeout(this.activityDmxStopTimeout);
        this.activityDmxStopTimeout = undefined;
      }

      this.activityDmxStopTimeout = setTimeout(() => {
        this.activityDmxStopTimeout = undefined;
        this.activityDmx = false;
      }, decayMillis);
    });
    this.activityDmxService.startMonitor();
  }

  ngOnDestroy() {
    this.activityMidiService.stopMonitor();
    this.activityAudioService.stopMonitor();
    this.activityDmxService.stopMonitor();
  }

  private loadAllSets() {
    this.compositionService.getSets().pipe(map(result => {
      this.sets = result;
    })).subscribe();
  }

  private updateTotalDuration() {
    let totalDurationMillis: number = 0;

    for (let composition of this.currentSet.compositionList) {
      totalDurationMillis += composition.durationMillis;
    }

    this.totalPlayTime = this.msToTime(totalDurationMillis, false);
  }

  private loadCurrentSet() {
    // Load the current set
    this.loadingSet = true;

    this.compositionService.getCurrentSet(true).pipe(finalize(() => {
      this.loadingSet = false;
    })).subscribe((set: Set) => {
      this.currentSet = undefined;

      if (set) {
        this.currentSet = set;
        this.updateTotalDuration();
      }

      if (this.currentSet && !this.currentSet.name) {
        // The default set with all compositions is loaded -> display all compositions
        this.compositionService.getCompositions(true).subscribe((compositions: Composition[]) => {
          this.currentSet.compositionList = compositions;
          this.updateTotalDuration();
          this.loadingSet = false;
        });
      } else {
        this.loadingSet = false;
      }
    });
  }

  selectSet(set: Set) {
    let setName: string = '';

    if (set) {
      setName = set.name;
    }

    this.compositionService.loadSet(setName).subscribe();
  }

  private pad(num: number, size: number): string {
    if (!num) {
      num = 0;
    }

    let padded: string = num.toString();
    while (padded.length < size) {
      padded = '0' + padded;
    }

    return padded;
  }

  private msToTime(millis: number, includeMillis: boolean = true): string {
    let ms: number = Math.round(millis % 1000);
    let seconds: number = Math.floor(((millis % 360000) % 60000) / 1000);
    let minutes: number = Math.floor((millis % 3600000) / 60000);

    if (includeMillis) {
      return this.pad(minutes, 2) + ':' + this.pad(seconds, 2) + '.' + this.pad(ms, 3);
    } else {
      return this.pad(minutes, 2) + ':' + this.pad(seconds, 2);
    }
  }

  private stateChanged(newState: State) {
    this.positionMillis = newState.positionMillis;
    this.playTime = this.msToTime(this.positionMillis);

    if (newState.error) {
      this.toastGeneralErrorService.showMessage(newState.error);
    }

    if (newState.playState == 'PLAYING' && this.currentState.playState != 'PLAYING') {
      if (this.playUpdateSubscription) {
        this.playUpdateSubscription.unsubscribe;
      }

      // Save the last time, we started the composition. Don't use device time, as it may be wrong.
      this.lastPlayTime = new Date();

      let playUpdater = timer(0, 10);
      this.playUpdateSubscription = playUpdater.subscribe(() => {
        let currentTime = new Date();
        let positionMillis = currentTime.getTime() - this.lastPlayTime.getTime() + this.currentState.positionMillis;

        if (!this.sliding && this.currentState.playState != 'STOPPING') {
          if (positionMillis > 0) {
            this.playTime = this.msToTime(positionMillis);
          }

          this.positionMillis = positionMillis;
        }
      });
    }

    if (newState.playState == 'STOPPING' || newState.playState == 'STOPPING' || newState.playState == 'PAUSED') {
      if (this.playUpdateSubscription) {
        this.playUpdateSubscription.unsubscribe();
      }
    }

    // Scroll the corresponding composition into the view, except the user selected the
    // composition here in the app.
    if (this.manualCompositionSelection) {
      // The next time, we receive a new composition state, we should scroll into the view again
      this.manualCompositionSelection = false;
    } else {
      let compositionObject = document.querySelector('#composition' + newState.currentCompositionIndex);
      if (compositionObject) {
        compositionObject.scrollIntoView();
      }

      let compositionSmallObject = document.querySelector('#compositionSmall' + newState.currentCompositionIndex);
      if (compositionSmallObject) {
        compositionSmallObject.scrollIntoView();
      }
    }

    // The current set changed
    if (newState.currentSetName != this.currentState.currentSetName) {
      this.loadCurrentSet();
    }

    if (!newState.error) {
      this.currentState = newState;
    }
  }

  play() {
    this.currentState.playState = 'LOADING';
    this.transportService.play()
      .pipe(catchError((err) => {
        this.stop();
        return this.toastGeneralErrorService.show(err);
      }))
      .subscribe();
  }

  stop() {
    this.transportService.stop().subscribe();
  }

  pause() {
    this.transportService.pause().subscribe();
  }

  slideStart() {
    this.sliding = true;
  }

  slideStop(positionMillis: number) {
    this.lastPlayTime = new Date();
    this.currentState.positionMillis = positionMillis;
    this.playTime = this.msToTime(positionMillis);
    this.transportService.seek(positionMillis).subscribe();

    this.sliding = false;
  }

  slideChange(event: any) {
    this.positionMillis = event.newValue;
    this.playTime = this.msToTime(this.positionMillis);
  }

  nextComposition() {
    this.transportService.nextComposition().subscribe();
  }

  previousComposition() {
    this.transportService.previousComposition().subscribe();
  }

  setComposition(index: number, composition: Composition) {
    this.manualCompositionSelection = true;

    if (this.currentSet && !this.currentSet.name) {
      // We got the default set loaded -> select compositions by name
      this.transportService.setCompositionName(composition.name).subscribe();
    } else {
      // We got a real set loaded -> select compositions by index
      this.transportService.setCompositionIndex(index).subscribe();
    }
  }

  toggleAutoSelectNextSong() {
    this.session.autoSelectNextComposition = !this.session.autoSelectNextComposition;

    this.sessionService.setAutoSelectNextComposition(this.session.autoSelectNextComposition).subscribe();
  }

  audioActivityOpacity(volumeDb: number): number {
    // Normalize the DB value to a value between 0.0 and 1.0
    return Math.pow(10, volumeDb / 20);
  }

}
