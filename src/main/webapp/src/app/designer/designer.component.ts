import { Component, OnInit } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AppHttpInterceptor } from '../app-http-interceptor/app-http-interceptor';
import { AccountService } from '../services/account.service';
import { SettingsService } from '../services/settings.service';

interface DesignerUniverse {
  uuid: string;
  name: string;
}

@Component({
    selector: 'app-designer',
    templateUrl: './designer.component.html',
    styleUrls: ['./designer.component.scss'],
    standalone: false
})
export class DesignerComponent implements OnInit {

  // The AI features of the designer work on a device linked to an account of the online designer (Settings -> Account),
  // which the device hands its requests on to. Not linked, or until that is known, the designer doesn't offer them.
  accountLinked = false;

  constructor(
    public appHttpInterceptor: AppHttpInterceptor,
    public settingsService: SettingsService,
    private accountService: AccountService
  ) { }

  ngOnInit() {
    this.accountService.getStatus().subscribe({
      next: (status) => (this.accountLinked = !!status?.linked),
      error: () => (this.accountLinked = false),
    });
  }

  canDeactivate(): Observable<boolean> {
    // TODO
    return of(true);
  }

  get designerUniverses(): DesignerUniverse[] {
    return (this.settingsService.settings?.lightingUniverseList || []).map(
      (universe) => ({
        uuid: universe.uuid,
        name: universe.name,
      })
    );
  }

}
