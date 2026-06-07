import { Component, OnInit } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AppHttpInterceptor } from '../app-http-interceptor/app-http-interceptor';
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

  constructor(
    public appHttpInterceptor: AppHttpInterceptor,
    public settingsService: SettingsService
  ) { }

  ngOnInit() {
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
