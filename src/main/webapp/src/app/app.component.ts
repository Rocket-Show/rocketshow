import { LeadSheetService } from "./services/lead-sheet.service";
import { StateService } from "./services/state.service";
import { CompositionService } from "./services/composition.service";
import { Component, OnInit } from "@angular/core";
import { TranslateService } from "@ngx-translate/core";
import { Router, NavigationEnd, ActivatedRoute } from "@angular/router";
import { forkJoin } from "rxjs";
import { SessionService } from "./services/session.service";
import { SettingsService } from "./services/settings.service";
import { Settings } from "./models/settings";
import { Title } from "@angular/platform-browser";
import { OperatingSystemInformationService } from "./services/operating-system-information.service";
import { AuthService } from "./services/auth.service";
import { filter, map, pairwise, startWith } from "rxjs/operators";

@Component({
  selector: "body",
  templateUrl: "./app.component.html",
  styleUrls: ["./app.component.scss"],
  host: { "[class.body-bg-moving]": "this.isIntro" },
  standalone: false
})
export class AppComponent implements OnInit {
  isIntro: boolean = false;
  isPlay: boolean = false;
  loaded: boolean = false;
  settings: Settings;
  mobileAppHost: boolean = false;

  constructor(
    private translateService: TranslateService,
    private router: Router,
    private stateService: StateService,
    private compositionService: CompositionService,
    private sessionService: SessionService,
    private settingsService: SettingsService,
    private titleService: Title,
    private leadSheetService: LeadSheetService,
    private operatingSystemInformationService: OperatingSystemInformationService,
    private route: ActivatedRoute,
    public authService: AuthService,
  ) {
    translateService.setDefaultLang("en");
  }

  // Keep a copy of the settings to not change them instantly, when the user
  // just tests something without saving
  private applySettings(settings: Settings) {
    this.settings = settings;

    this.titleService.setTitle("Rocket Show - " + this.settings.deviceName);
  }

  ngOnInit() {
    this.router.events.subscribe((e) => {
      if (e instanceof NavigationEnd) {
        const mobileAppHost =
          this.route.snapshot.queryParamMap.get("mobileAppHost");
        if (mobileAppHost === "true") {
          this.mobileAppHost = true;
        }

        this.isIntro = false;
        this.isPlay = false;

        switch (e.url) {
          case "/intro": {
            this.isIntro = true;
            break;
          }
          case "/play": {
            this.isPlay = true;
            break;
          }
          case "/": {
            this.isPlay = true;
            break;
          }
        }
      }
    });

    // Load some required data
    if (this.authService.currentState && this.authService.currentState.authenticated) {
      this.loadInitialData()
    }
    this.authService.state.subscribe((state) => {
      if (state.authenticated) {
        this.loadInitialData()
      } else if (!state.passwordConfigured) {
        // Not logged in and no password yet configured -> show intro wizard
        this.navigateIntro();
      }
    });
  }

  private navigateIntro() {
    this.router.navigate(["/intro"]);
  }

  private loadInitialData() {
    forkJoin(
      this.stateService.getState(),
      this.compositionService.getCompositions(),
      this.compositionService.getSets(),
      this.sessionService.getSession(),
      this.settingsService.getSettings(),
      this.operatingSystemInformationService.getOperatingSystemInformation()
    ).subscribe((result) => {
      this.loaded = true;
      this.applySettings(result[4]);

      // Show the intro if required
      if (result[3].firstStart) {
        this.navigateIntro();
      }

      // Set the correct language
      this.translateService.use(this.settings.language);
    });

    this.settingsService.settingsChanged.subscribe(() => {
      this.settingsService.getSettings().subscribe((settings) => {
        this.applySettings(settings);
      });
    });
  }

  showLeadSheet() {
    this.leadSheetService.show();
  }

  leadSheetButtonVisible(): boolean {
    if (this.leadSheetService.currentLeadSheetUrl) {
      return true;
    }

    return false;
  }

  mobileHostBack() {
    (<any>window).ReactNativeWebView.postMessage("navigateBack");
  }
}
