import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';
import { HttpClientModule, HttpClient, HTTP_INTERCEPTORS } from '@angular/common/http';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { MultiTranslateHttpLoader } from "ngx-translate-multi-http-loader";
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

import { ArraySortPipe } from './array-sort-pipe';
import { SortablejsModule } from 'angular-sortablejs';
import { AlertModule, AccordionModule, PopoverModule, TypeaheadModule, BsDropdownModule } from 'ngx-bootstrap';
import { ModalModule } from 'ngx-bootstrap';
import { DropzoneModule } from 'ngx-dropzone-wrapper';
import { DropzoneConfigInterface } from 'ngx-dropzone-wrapper';
import { ToastrModule } from 'ngx-toastr';
import { NgxBootstrapSliderModule } from 'ngx-bootstrap-slider';
import { DesignerModule } from '@rocketshow/designer';

import { PendingChangesGuard } from './pending-changes.guard';

import { AppHttpInterceptor } from './app-http-interceptor/app-http-interceptor';
import { StateService } from './services/state.service';
import { TransportService } from './services/transport.service';
import { CompositionService } from './services/composition.service';
import { FileService } from './services/file.service';
import { SettingsService } from './services/settings.service';
import { SessionService } from './services/session.service';
import { WarningDialogService } from './services/warning-dialog.service';
import { WarningDialogComponent } from './warning-dialog/warning-dialog.component';
import { PendingChangesDialogService } from './services/pending-changes-dialog.service';
import { UpdateService } from './services/update.service';
import { InfoDialogService } from './services/info-dialog.service';
import { ToastGeneralErrorService } from './services/toast-general-error.service';
import { WaitDialogService } from './services/wait-dialog.service';

import { AppComponent } from './app.component';
import { IntroComponent } from './intro/intro.component';
import { ConnectionComponent } from './connection/connection.component';
import { PlayComponent } from './play/play.component';
import { SettingsComponent } from './settings/settings.component';
import { SettingsSystemComponent } from './settings/settings-system/settings-system.component';
import { SettingsAdvancedComponent } from './settings/settings-advanced/settings-advanced.component';
import { SettingsMidiComponent } from './settings/settings-midi/settings-midi.component';
import { SettingsNetworkComponent } from './settings/settings-network/settings-network.component';
import { SettingsAudioComponent } from './settings/settings-audio/settings-audio.component';
import { SettingsVideoComponent } from './settings/settings-video/settings-video.component';
import { SettingsLightingComponent } from './settings/settings-lighting/settings-lighting.component';
import { EditorComponent } from './editor/editor.component';
import { EditorCompositionComponent } from './editor/editor-composition/editor-composition.component';
import { EditorSetComponent } from './editor/editor-set/editor-set.component';
import { EditorCompositionFileComponent } from './editor/editor-composition/editor-composition-file/editor-composition-file.component';
import { RoutingDetailsComponent } from './routing-details/routing-details.component';
import { UpdateDialogComponent } from './update-dialog/update-dialog.component';
import { WaitDialogComponent } from './wait-dialog/wait-dialog.component';
import { InfoDialogComponent } from './info-dialog/info-dialog.component';
import { RemoteDeviceSelectionComponent } from './remote-device-selection/remote-device-selection.component';
import { MidiRoutingComponent } from './midi-routing/midi-routing.component';
import { MidiMappingComponent } from './midi-mapping/midi-mapping.component';
import { SettingsInfoComponent } from './settings/settings-info/settings-info.component';
import { LeadSheetComponent } from './lead-sheet/lead-sheet.component';
import { EditorCompositionLeadSheetComponent } from './editor/editor-composition/editor-composition-lead-sheet/editor-composition-lead-sheet.component';
import { SettingsBandComponent } from './settings/settings-band/settings-band.component';
import { SettingsPersonalComponent } from './settings/settings-personal/settings-personal.component';
import { DesignerComponent } from './designer/designer.component';

const appRoutes: Routes = [
  { path: 'intro', component: IntroComponent },
  { path: 'play', component: PlayComponent },
  { path: 'editor', component: EditorComponent, canDeactivate: [PendingChangesGuard] },
  { path: 'designer', component: DesignerComponent, canDeactivate: [PendingChangesGuard] },
  { path: 'settings', component: SettingsComponent, canDeactivate: [PendingChangesGuard] },
  {
    path: '',
    redirectTo: '/play',
    pathMatch: 'full'
  },
  { path: '**', component: PlayComponent }
];

const DROPZONE_CONFIG: DropzoneConfigInterface = {
};

@NgModule({
  declarations: [
    ArraySortPipe,
    AppComponent,
    IntroComponent,
    PlayComponent,
    SettingsComponent,
    EditorComponent,
    EditorCompositionComponent,
    EditorSetComponent,
    ConnectionComponent,
    EditorCompositionFileComponent,
    RoutingDetailsComponent,
    SettingsSystemComponent,
    WarningDialogComponent,
    SettingsAdvancedComponent,
    UpdateDialogComponent,
    WaitDialogComponent,
    InfoDialogComponent,
    SettingsMidiComponent,
    SettingsNetworkComponent,
    SettingsAudioComponent,
    SettingsVideoComponent,
    RemoteDeviceSelectionComponent,
    SettingsLightingComponent,
    MidiRoutingComponent,
    MidiMappingComponent,
    SettingsInfoComponent,
    LeadSheetComponent,
    EditorCompositionLeadSheetComponent,
    SettingsBandComponent,
    SettingsPersonalComponent,
    DesignerComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    RouterModule.forRoot(
      appRoutes,
      { enableTracing: false, useHash: true }
    ),
    BrowserAnimationsModule,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient]
      }
    }),
    FormsModule,
    SortablejsModule.forRoot({
      animation: 300,
      handle: '.list-sort-handle'
    }),
    AlertModule.forRoot(),
    ModalModule.forRoot(),
    DropzoneModule.forRoot(DROPZONE_CONFIG),
    ToastrModule.forRoot({
      newestOnTop: true
    }),
    NgxBootstrapSliderModule,
    AccordionModule.forRoot(),
    PopoverModule.forRoot(),
    TypeaheadModule.forRoot(),
    BsDropdownModule.forRoot(),
    DesignerModule
  ],
  providers: [
    AppHttpInterceptor,
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AppHttpInterceptor,
      multi: true
    },
    PendingChangesGuard,
    StateService,
    TransportService,
    CompositionService,
    FileService,
    SettingsService,
    SessionService,
    WarningDialogService,
    PendingChangesDialogService,
    UpdateService,
    WaitDialogService,
    InfoDialogService,
    ToastGeneralErrorService
  ],
  entryComponents: [
    EditorCompositionFileComponent,
    RoutingDetailsComponent,
    WarningDialogComponent,
    UpdateDialogComponent,
    WaitDialogComponent,
    InfoDialogComponent,
    EditorCompositionLeadSheetComponent
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }

export function HttpLoaderFactory(http: HttpClient) {
  return new MultiTranslateHttpLoader(http, [
    { prefix: "./assets/i18n/", suffix: ".json" },
    { prefix: "./assets/designer/i18n/", suffix: ".json" },
  ]);
}
