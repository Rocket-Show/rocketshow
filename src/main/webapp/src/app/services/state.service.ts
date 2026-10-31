import { Injectable } from '@angular/core';
import { AppWebSocket, WebSocketConfig } from './web-socket';
import { exhaustMap, filter, map, pairwise, startWith } from "rxjs/operators";
import { EMPTY, Observable, Subject, Subscription, timer } from 'rxjs';
import { environment } from '../../environments/environment';
import { State } from '../models/state';
import { HttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';

@Injectable()
export class StateService {

  public state: Subject<State> = new Subject();
  // the state received last
  currentState: State;

  // The websocket endpoint url
  private wsUrl: string;

  // The websocket connection
  public websocket: AppWebSocket;

  public connected: boolean = false;
  public initiallyLoaded: boolean = false;
  public getsConnected: Subject<void> = new Subject();

  private reconnectSubscription: Subscription;

  constructor(
    private http: HttpClient,
    private authService: AuthService,
  ) {
    if (this.authService.currentState && this.authService.currentState.authenticated) {
      this.init;
    }
    this.authService.state.subscribe((state) => {
      if (state.authenticated) {
        if (this.websocket) {
          // E.g. logged in again after the device rebooted -> keep the connection, it reconnects with the new session
          this.websocket.connect();
        } else {
          this.init();
        }
      } else {
        this.websocket?.close(true);
        this.websocket = null;
        if (this.reconnectSubscription) {
          this.reconnectSubscription.unsubscribe();
        }
      }
    });
  }

  private init() {
    let protocol = 'ws://';

    if (window.location.protocol === 'https:') {
      protocol = 'wss://';
    }

    // Create the backend-url
    if (environment.name == 'dev') {
      this.wsUrl = protocol + environment.localBackend + '/';
    } else {
      this.wsUrl = protocol + window.location.hostname + ':' + window.location.port + '/';
    }

    this.wsUrl += 'api/state';

    // Connect to the websocket backend
    const wsConfig = { reconnectIfNotNormalClose: false } as WebSocketConfig;
    this.websocket = new AppWebSocket(this.wsUrl, undefined, wsConfig);

    this.websocket.onMessage(
      (msg: MessageEvent) => {
        let state: State = new State(JSON.parse(msg.data));
        this.receiveState(state);
      },
      { autoApply: false }
    );

    this.websocket.onOpen(() => {
      this.getState().subscribe((state: State) => {
        this.receiveState(state);
      });
    });

    this.websocket.onClose(() => {
      console.log('WebSocket connection closed');
      this.connected = false;
      this.initiallyLoaded = true;
    });

    // try to reconnect manually and don't rely on reconnectIfNotNormalClose, because
    // this too slow sometimes (exponential backoff timer). try to reconnect
    // each 5 seconds. The device refuses the websocket without telling why, if it
    // forgot the session (e.g. rebooted) -> check the session first and log in again.
    let reconnectTimer = timer(5000, 5000);
    this.reconnectSubscription = reconnectTimer.pipe(
      filter(() => !this.connected),
      exhaustMap(() => this.authService.sessionLost())
    ).subscribe((sessionLost) => {
      if (sessionLost) {
        this.authService.init().subscribe();
      } else {
        this.websocket.connect();
      }
    });
  }

  receiveState(state: State): void {
    this.state.next(state);
    this.currentState = state;

    if (environment.debug) {
      console.log('Current state', state);
    }
  }

  getState(): Observable<State> {
    if (!this.authService.currentState || !this.authService.currentState.authenticated) {
      return EMPTY;
    }

    return this.http.get('system/state')
      .pipe(map(response => {
        this.currentState = new State(response);

        if (!this.connected) {
          this.getsConnected.next();
        }

        this.connected = true;
        this.initiallyLoaded = true;

        return this.currentState;
      }));
  }

}
