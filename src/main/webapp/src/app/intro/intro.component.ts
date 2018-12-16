import { SessionService } from './../services/session.service';
import { Component, OnInit } from '@angular/core';
import { trigger, state, animate, transition, style, query } from '@angular/animations';
import { Router } from '@angular/router';
import { Settings } from '../models/settings';
import { SettingsService } from '../services/settings.service';
import { TranslateService } from '@ngx-translate/core';
import { map } from "rxjs/operators";

@Component({
  selector: 'app-intro',
  templateUrl: './intro.component.html',
  styleUrls: ['./intro.component.scss'],
  animations: [
    trigger('wizardState', [
      state('inactive', style({
        opacity: 0,
        marginTop: "-100px"
      })),
      state('active', style({
        opacity: 1,
        marginTop: 0
      })),
      transition('active => inactive', animate('500ms ease-out'))
    ])
  ]
})
export class IntroComponent implements OnInit {

  settings: Settings;

  wizardState: string = 'active';

  deviceName: string = '';

  constructor(
    private router: Router,
    public settingsService: SettingsService,
    private translateService: TranslateService,
    private sessionService: SessionService) { }

  ngOnInit() {
    this.settingsService.getSettings().pipe(map(result => {
      this.settings = result;
    })).subscribe();
  }

  switchLanguage(language: string) {
    this.settings.language = language;
    this.translateService.use(language);
  }

  finish() {
    this.wizardState = 'inactive';

    this.translateService.get('intro.default-unit-name').subscribe((result) => {
      if(!this.deviceName) {
        this.deviceName = result;
      }
      this.settings.deviceName = this.deviceName;
      this.settingsService.saveSettings(this.settings).subscribe(() => {
        this.settingsService.settingsChanged.next();
        this.sessionService.introFinished().subscribe();
  
        // Show the app as soon as the intro wizard has been hidden
        setTimeout(() => {
          this.router.navigate(['/play']);
        }, 500);
      });
    });
  }

}
