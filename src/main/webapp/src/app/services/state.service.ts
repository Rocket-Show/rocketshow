import { Injectable } from '@angular/core';
import { $WebSocket, WebSocketConfig } from 'angular2-websocket/angular2-websocket';
import { map } from "rxjs/operators";
import { Observable, Subject, Subscription, timer } from 'rxjs';
import { environment } from '../../environments/environment';
import { State } from '../models/state';
import { HttpClient } from '@angular/common/http';

@Injectable()
export class StateService {

  public state: Subject<State> = new Subject();
  private currentState: State;

  // The websocket endpoint url
  private wsUrl: string;

  // The websocket connection
  public websocket: $WebSocket;

  public connected: boolean = false;
  public getsConnected: Subject<void> = new Subject();

  private reconnectSubscription: Subscription;

  constructor(private http: HttpClient
  ) {
    // Create the backend-url
    if (environment.name == 'dev') {
      this.wsUrl = 'ws://' + environment.localBackend + '/';
    } else {
      this.wsUrl = 'ws://' + window.location.hostname + ':' + window.location.port + '/';
    }

    this.wsUrl += 'api/state';

    // Connect to the websocket backend
    const wsConfig = { reconnectIfNotNormalClose: false } as WebSocketConfig;
    this.websocket = new $WebSocket(this.wsUrl, null, wsConfig);

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
      this.connected = false;
    });

    // try to reconnect manually and don't rely on reconnectIfNotNormalClose, because
    // this too slow sometimes (exponential backoff timer). try to reconnect
    // each 5 seconds.
    let reconnectTimer = timer(0, 5000);
    this.reconnectSubscription = reconnectTimer.subscribe(() => {
      if(!this.connected) {
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
    return this.http.get('system/state')
      .pipe(map(response => {
        this.currentState = new State(response);

        if(!this.connected) {
          this.getsConnected.next();
        }

        this.connected = true;

        return this.currentState;
      }));
  }

}
