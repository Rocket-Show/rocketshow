import { Component, OnInit } from '@angular/core';
import { ApiKey } from '../../models/api-key';
import { UuidService } from '../../services/uuid.service';
import { Subject } from 'rxjs';
import { BsModalRef } from 'ngx-bootstrap/modal';

@Component({
  selector: 'app-new-api-key-dialog',
  templateUrl: './new-api-key-dialog.component.html',
  styleUrl: './new-api-key-dialog.component.scss',
  standalone: false,
})
export class NewApiKeyDialogComponent implements OnInit {

  apiKey: ApiKey;
  onClose: Subject<number> = new Subject();

  constructor(
    private uuidService: UuidService,
    private bsModalRef: BsModalRef
  ) {

  }

  ngOnInit() {
    this.apiKey = new ApiKey();
    this.apiKey.uuid = this.uuidService.getUuid();
    this.apiKey.key = this.generateSecureApiKey();
  }

  private generateSecureApiKey(): string {
    const arr = new Uint8Array(24);
    crypto.getRandomValues(arr);
    const base64 = btoa(String.fromCharCode(...arr))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');

    return `rs_${base64}`;
  }

  public ok(): void {
    this.onClose.next(1);
    this.bsModalRef.hide();
  }

  public cancel(): void {
    this.onClose.next(2);
    this.bsModalRef.hide();
  }

}
