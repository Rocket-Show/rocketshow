import { LeadSheetService } from './services/lead-sheet.service';
import { StateService } from './services/state.service';
import { CompositionService } from './services/composition.service';
import { Component, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Router, NavigationEnd } from '@angular/router';
import { Observable } from 'rxjs/Rx';
import { SessionService } from './services/session.service';
import { SettingsService } from './services/settings.service';
import { Settings } from './models/settings';
import { Title } from '@angular/platform-browser';

@Component({
  selector: 'body',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
  host: { '[class.body-bg-moving]': 'this.isIntro' }
})
export class AppComponent implements OnInit {

  isIntro: boolean = false;
  isPlay: boolean = false;
  loaded: boolean = false;
  settings: Settings;

  constructor(
    private translateService: TranslateService,
    private router: Router,
    private stateService: StateService,
    private compositionService: CompositionService,
    private sessionService: SessionService,
    private settingsService: SettingsService,
    private titleService: Title,
    private leadSheetService: LeadSheetService) {

    translateService.setDefaultLang('en');
  }

  // Keep a copy of the settings to not change them instantly, when the user
  // just tests something without saving
  private copySettings(settings: Settings) {
    this.settings = JSON.parse(JSON.stringify(settings));

    this.titleService.setTitle('Rocket Show - ' + this.settings.deviceName);
  }

  ngOnInit() {
    this.router.events.subscribe((e) => {
      if (e instanceof NavigationEnd) {
        this.isIntro = false;
        this.isPlay = false;

        switch(e.url) { 
          case '/intro': { 
             this.isIntro = true;
             break; 
          } 
          case '/play': { 
             this.isPlay = true;
             break; 
          }
          case '/': { 
            this.isPlay = true;
            break; 
         }
       } 
      }
    });

    // Load some required data
    Observable.forkJoin(
      this.stateService.getState(),
      this.compositionService.getCompositions(),
      this.compositionService.getSets(),
      this.sessionService.getSession(),
      this.settingsService.getSettings()
    ).subscribe((result) => {
      this.loaded = true;
      this.copySettings(result[4]);

      // Show the intro if required
      if (result[3].firstStart) {
        this.router.navigate(['/intro']);
      }

      // Set the correct language
      this.translateService.use(this.settings.language);
    });

    this.settingsService.settingsChanged.subscribe(() => {
      this.settingsService.getSettings().subscribe((settings) => {
        this.copySettings(settings);
      });
    });
  }

  showLeadSheet() {
    this.leadSheetService.showLeadSheet = true;
  }

}
