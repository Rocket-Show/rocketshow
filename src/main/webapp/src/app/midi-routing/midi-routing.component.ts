import { RoutingDetailsComponent } from './../routing-details/routing-details.component';
import { BsModalService } from 'ngx-bootstrap/modal';
import { MidiRouting } from './../models/midi-routing';
import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'app-midi-routing',
  templateUrl: './midi-routing.component.html',
  styleUrls: ['./midi-routing.component.scss']
})
export class MidiRoutingComponent implements OnInit {
  @Input() midiRoutingList: MidiRouting[];

  constructor(
    private modalService: BsModalService) { }

  ngOnInit() {
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt) {
    return evt.related.className.indexOf('no-sortjs') === -1;
  }

  // Edit the routing details
  editRouting(midiRoutingIndex: number, addNew: boolean = false) {
    // Create a backup of the current list
    let listCopy = JSON.parse(JSON.stringify(this.midiRoutingList));

    if (addNew) {
      // Add a new routing, if necessary
      let newRouting: MidiRouting = new MidiRouting();
      newRouting.midiDestination = 'OUT_DEVICE';
      listCopy.push(newRouting);
      midiRoutingIndex = listCopy.length - 1;
    }

    // Show the routing details dialog
    let routingDialog = this.modalService.show(RoutingDetailsComponent, { keyboard: true, animated: true, backdrop: false, ignoreBackdropClick: true, class: "" });
    (<RoutingDetailsComponent>routingDialog.content).midiRouting = listCopy[midiRoutingIndex];

    (<RoutingDetailsComponent>routingDialog.content).onClose.subscribe(result => {
      if (result === 1) {
        // OK has been pressed -> save
        this.midiRoutingList[midiRoutingIndex] = listCopy[midiRoutingIndex];
      }
    });
  }

  deleteRouting(midiRoutingIndex: number) {
    this.midiRoutingList.splice(midiRoutingIndex, 1);
  }

}
