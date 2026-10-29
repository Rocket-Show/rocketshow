import { Component, EventEmitter, Input, Output } from '@angular/core';

// What the Play screen shows: the compositions of the set or the scenes of the designer projects
@Component({
  selector: 'app-play-mode-switch',
  templateUrl: './play-mode-switch.component.html',
  styleUrls: ['./play-mode-switch.component.scss'],
  standalone: false
})
export class PlayModeSwitchComponent {

  @Input() mode: 'compositions' | 'scenes' = 'compositions';

  // a composition plays, while the scenes are shown
  @Input() compositionPlaying: boolean = false;

  @Output() modeChange = new EventEmitter<'compositions' | 'scenes'>();

  select(mode: 'compositions' | 'scenes') {
    if (mode !== this.mode) {
      this.modeChange.emit(mode);
    }
  }

}
