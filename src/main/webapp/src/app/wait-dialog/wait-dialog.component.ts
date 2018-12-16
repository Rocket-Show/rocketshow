import { Component, OnInit } from '@angular/core';
import { BsModalRef } from 'ngx-bootstrap';

@Component({
  selector: 'app-wait-dialog',
  templateUrl: './wait-dialog.component.html',
  styleUrls: ['./wait-dialog.component.scss']
})
export class WaitDialogComponent implements OnInit {

  message: string = '';

  constructor(private bsModalRef: BsModalRef) { }

  ngOnInit() {
  }

  public close(): void {
    this.bsModalRef.hide();
  }

}
