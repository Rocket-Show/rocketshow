import { Injectable } from '@angular/core';
import { Subject, Subscription, timer } from 'rxjs';
import { UpdateState } from '../models/update-state';
import { $WebSocket, WebSocketConfig } from 'angular2-websocket';
import { environment } from '../../environments/environment';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class UpdateStateService {

  public updateState: Subject<UpdateState> = new Subject();

  // The websocket endpoint url
  private wsUrl: string;

  // The websocket connection
  websocket: $WebSocket;

  public connected: boolean = false;
  private reconnectSubscription: Subscription;

  constructor(private http: HttpClient
  ) {
    // Create the backend-url
    let protocol = 'ws://';

    if (window.location.protocol === 'https:') {
      protocol = 'wss://';
    }

    if (environment.name == 'dev') {
      this.wsUrl = protocol + environment.localBackend + '/';
    } else {
      this.wsUrl = protocol + window.location.hostname + ':' + window.location.port + '/';
    }

    this.wsUrl += 'api/update/state';

    // Connect to the websocket backend
    const wsConfig = { reconnectIfNotNormalClose: false } as WebSocketConfig;
    this.websocket = new $WebSocket(this.wsUrl, null, wsConfig);

    this.websocket.onMessage(
      (msg: MessageEvent) => {
        let updateState: UpdateState = new UpdateState(JSON.parse(msg.data));
        this.receiveState(updateState);
      },
      { autoApply: false }
    );

    this.websocket.onOpen(() => {
      this.connected = true;
    });

    this.websocket.onClose(() => {
      this.connected = false;
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

  receiveState(updateState: UpdateState): void {
    this.updateState.next(updateState);

    if (environment.debug) {
      console.log('Current update state', updateState);
    }
  }

}
