import { Injectable } from '@angular/core';
import { AppWebSocket, WebSocketConfig } from './web-socket';
import { filter, map, pairwise, startWith } from "rxjs/operators";
import { EMPTY, Observable, Subject, Subscription, timer } from 'rxjs';
import { environment } from '../../environments/environment';
import { State } from '../models/state';
import { HttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';

@Injectable()
export class StateService {

  public state: Subject<State> = new Subject();
  private currentState: State;

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
        this.init()
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
    // each 5 seconds.
    let reconnectTimer = timer(0, 5000);
    this.reconnectSubscription = reconnectTimer.subscribe(() => {
      if (!this.connected) {
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
