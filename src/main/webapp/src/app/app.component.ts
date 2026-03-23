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
import { DeviceInformation } from "./models/device-information";
import { DeviceInformationService } from "./services/device-information.service";

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
    private deviceInformationService: DeviceInformationService,
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
            if (this.authService.currentState?.passwordConfigured) {
              this.navigatePlay();
            } else {
              this.isIntro = true;
            }
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
          } else if (e.url === "/intro") {
            // Not logged in but password is already configured -> move away from the intro
            this.navigatePlay();
          }
        });
      }
    });
  }

  private navigateIntro() {
    this.router.navigate(["/intro"]);
    this.isIntro = true;
  }

  private navigatePlay() {
    this.router.navigate(["/"]);
    this.isPlay = true;
  }

  private loadInitialData() {
    forkJoin({
      state: this.stateService.getState(),
      compositions: this.compositionService.getCompositions(),
      sets: this.compositionService.getSets(),
      session: this.sessionService.getSession(),
      settings: this.settingsService.getSettings(),
      osInfo: this.operatingSystemInformationService.getOperatingSystemInformation(),
      deviceInformation: this.deviceInformationService.getDeviceInformation(),
    }).subscribe((result) => {
      this.loaded = true;
      this.applySettings(result.settings);

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
