import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BuildInfo } from '../../models/build-info';

@Component({
    selector: 'app-settings-info',
    templateUrl: './settings-info.component.html',
    styleUrls: ['./settings-info.component.scss'],
    standalone: false
})
export class SettingsInfoComponent implements OnInit {

  buildInfo: BuildInfo;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.http.get<BuildInfo>('system/build-info').subscribe(info => {
      this.buildInfo = info;
    });
  }

}
