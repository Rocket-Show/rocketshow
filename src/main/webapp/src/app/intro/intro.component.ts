import { SessionService } from './../services/session.service';
import { AfterViewInit, Component, HostListener, OnInit } from '@angular/core';
import { trigger, state, animate, transition, style, query } from '@angular/animations';
import { Router } from '@angular/router';
import { SettingsService } from '../services/settings.service';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../services/auth.service';

interface Particle {
  x: number;
  y: number;
  size: number;
  opacity: number;
  duration: number;
  delay: number;
  driftX: number;
  driftY: number;
  color: string;
}

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
  ],
  standalone: false
})
export class IntroComponent implements OnInit {

  particles: Particle[] = [];
  particleCount = 80;

  offsetX = 0;
  offsetY = 0;

  language: string = 'en';
  wizardState: string = 'active';
  deviceName: string = '';
  adminPassword1: string = '';
  adminPassword2: string = '';

  constructor(
    private router: Router,
    public settingsService: SettingsService,
    private translateService: TranslateService,
    private sessionService: SessionService,
    private authService: AuthService,
  ) { }

  ngOnInit() {
    this.particles = Array.from({ length: this.particleCount }, () =>
      this.createParticle()
    );
  }

  private createParticle(): Particle {
    return {
      x: this.random(0, 100),
      y: this.random(0, 100),
      size: this.random(2, 10),
      opacity: this.random(0.4, 0.8),
      duration: this.random(12, 28),
      delay: this.random(0, 0),
      driftX: this.random(-120, 120),
      driftY: this.random(-160, 160),
      color: this.randomGlowColor()
    };
  }

  private randomGlowColor(): string {
    const colors = [
      '255,255,255', // white
      '100,220,255', // soft blue
      '210,100,255', // lavender
      '80,240,255', // cyan
      '120,110,255', // purple tint
    ];

    return colors[Math.floor(Math.random() * colors.length)];
  }

  private random(min: number, max: number): number {
    return +(Math.random() * (max - min) + min).toFixed(2);
  }

  switchLanguage(language: string) {
    this.language = language;
    this.translateService.use(language);
  }

  finish() {
    this.wizardState = 'inactive';

    this.translateService.get('intro.default-unit-name').subscribe((result) => {
      if (!this.deviceName) {
        this.deviceName = result;
      }

      // TODO store language, device name and admin pw
      this.authService.setup(this.language, this.deviceName, this.adminPassword1).subscribe(() => {

      });

      // this.settingsService.saveSettings().subscribe(() => {
      //   this.settingsService.settingsChanged.next();
      //   this.sessionService.introFinished().subscribe();

      //   // Show the app as soon as the intro wizard has been hidden
      //   setTimeout(() => {
      //     this.router.navigate(['/play']);
      //   }, 500);
      // });
    });
  }

}