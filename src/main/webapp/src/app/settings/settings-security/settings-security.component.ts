import { Component, OnDestroy, OnInit } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { SettingsService } from '../../services/settings.service';
import { Settings } from '../../models/settings';
import { Subscription } from 'rxjs';
import { map } from 'rxjs/operators';

@Component({
  selector: 'app-settings-security',
  templateUrl: './settings-security.component.html',
  styleUrl: './settings-security.component.scss',
  standalone: false
})
export class SettingsSecurityComponent implements OnInit, OnDestroy {

  private settingsChangedSubscription: Subscription;
  settings: Settings;

  constructor(
    public authService: AuthService,
    private settingsService: SettingsService
  ) {

  }

  private loadSettings() {
    this.settingsService.getSettings().pipe(map(result => {
      this.settings = result;
    })).subscribe();
  }

  ngOnInit() {
    this.loadSettings();

    this.settingsChangedSubscription = this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });
  }

  ngOnDestroy() {
    this.settingsChangedSubscription.unsubscribe();
  }

  public logout() {
    this.authService.logout().subscribe({
      next: () => {
        this.authService.init().subscribe();
      },
      error: (err) => { }
    });
  }

  public changePassword() {
    // TODO
  }

  public addApiKey() {
    // TODO
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt: any) {
    return evt.related.className.indexOf("no-sortjs") === -1;
  }

}
