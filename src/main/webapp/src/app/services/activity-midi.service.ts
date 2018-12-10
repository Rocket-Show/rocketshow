import { Injectable } from '@angular/core';
import { $WebSocket, WebSocketConfig } from 'angular2-websocket/angular2-websocket';
import { Subject } from 'rxjs';
import * as Rx from 'rxjs/Rx';
import { HttpClient } from '@angular/common/http';
import { ActivityMidi } from '../models/activity-midi';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ActivityMidiService {

  public subject: Subject<ActivityMidi> = new Rx.Subject();

  // The websocket endpoint url
  private wsUrl: string;

  // The websocket connection
  websocket: $WebSocket;

  constructor(private http: HttpClient
    ) {
      // Create the backend-url
      if (environment.name == 'dev') {
        this.wsUrl = 'ws://' + environment.localBackend + '/';
      } else {
        this.wsUrl = 'ws://' + window.location.hostname + ':' + window.location.port + '/';
      }
  
      this.wsUrl += 'api/activity/midi';

      // Connect to the websocket backend
      const wsConfig = { reconnectIfNotNormalClose: true } as WebSocketConfig;
      this.websocket = new $WebSocket(this.wsUrl, null, wsConfig);
  
      this.websocket.onMessage(
        (msg: MessageEvent) => {
          this.subject.next(new ActivityMidi(JSON.parse(msg.data)));
        },
        { autoApply: false }
      );
    }

}
