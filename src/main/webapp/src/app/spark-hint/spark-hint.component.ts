import { Component, HostBinding, Input, OnDestroy, OnInit } from "@angular/core";
import { Subscription } from "rxjs";
import { TranslateService } from "@ngx-translate/core";

import { DeviceInformation } from "../models/device-information";
import { DeviceInformationService } from "../services/device-information.service";
import { SparkUpsellService } from "../services/spark-upsell.service";

/**
 * The "SPARK also does this" note that sits next to a setting the community
 * edition has to solve with extra hardware, or cannot do at all.
 *
 * It renders nothing on a ready to use unit: the owner of a SPARK already has
 * what the note offers, and an advert for the box on their desk is worse than
 * no note at all. It also stays hidden until the device information has
 * actually arrived, so a SPARK never flashes the hint while the request is
 * still in flight.
 *
 * The hiding happens on the host element rather than around the markup, so a
 * caller can put a spacing class on the tag (`<app-spark-hint class="mt-3">`)
 * without that spacing surviving as a gap on the units where nothing renders.
 */
@Component({
  selector: "app-spark-hint",
  templateUrl: "./spark-hint.component.html",
  styleUrl: "./spark-hint.component.scss",
  standalone: false
})
export class SparkHintComponent implements OnInit, OnDestroy {

  // Names the spot this hint sits in. It travels to the website as utm_content
  // and is reported there as the placement that earned the visit, so keep the
  // value stable once a hint is live.
  @Input()
  placement: string;

  // Translation key of the sentence to show.
  @Input()
  text: string;

  // The page to open. Defaults to the SPARK page.
  @Input()
  path: string;

  url: string;

  private deviceInformation: DeviceInformation;
  private langChangeSubscription: Subscription;

  constructor(
    private deviceInformationService: DeviceInformationService,
    private sparkUpsellService: SparkUpsellService,
    private translateService: TranslateService
  ) {
  }

  @HostBinding("style.display")
  get display(): string {
    return this.deviceInformation && !this.deviceInformation.available ? null : "none";
  }

  ngOnInit() {
    this.deviceInformationService.getDeviceInformation().subscribe((deviceInformation) => {
      this.deviceInformation = deviceInformation;
    });

    this.buildUrl();

    // The language is switched inside the app (in the settings and in the
    // intro wizard) and the link carries it, so it has to be rebuilt then.
    this.langChangeSubscription = this.translateService.onLangChange.subscribe(() => {
      this.buildUrl();
    });
  }

  ngOnDestroy() {
    if (this.langChangeSubscription) {
      this.langChangeSubscription.unsubscribe();
    }
  }

  private buildUrl() {
    this.url = this.sparkUpsellService.getUrl(this.placement, this.path || "/spark");
  }

}
