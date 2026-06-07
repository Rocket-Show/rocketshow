import { Injectable } from '@angular/core';
import { AppWebSocket, WebSocketConfig } from './web-socket';
import { Subject } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { SettingsService } from './settings.service';
import { ActivityAudio } from '../models/activity-audio';

@Injectable({
  providedIn: 'root'
})
export class ActivityAudioService {

  public subject: Subject<ActivityAudio> = new Subject();

  // The websocket endpoint url
  private wsUrl: string;

  // The websocket connection
  websocket: AppWebSocket;

  monitors: number = 0;

  constructor(private http: HttpClient, settingsService: SettingsService
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

    this.wsUrl += 'api/activity/audio';
  }

  startMonitor() {
    this.monitors++;

    if (!this.websocket) {
      // Connect to the websocket backend
      const wsConfig = { reconnectIfNotNormalClose: true } as WebSocketConfig;
      this.websocket = new AppWebSocket(this.wsUrl, undefined, wsConfig);

      this.websocket.onMessage(
        (msg: MessageEvent) => {
          this.subject.next(new ActivityAudio(JSON.parse(msg.data)));
        },
        { autoApply: false }
      );
    }
  }

  stopMonitor() {
    this.monitors--;

    if (this.monitors < 1 && this.websocket) {
      this.websocket.close();
      this.websocket = undefined;
    }
  }
}
