import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  Output,
} from "@angular/core";

@Component({
  selector: "app-volume-slider",
  templateUrl: "./volume-slider.component.html",
  styleUrl: "./volume-slider.component.scss",
})
export class VolumeSliderComponent {
  // Displayed volume control based on https://www.dr-lex.be/info-stuff/volumecontrols.html
  // Working with input/output between 0 (min volume) and 1 (max volume)

  dbMin: number = -60;
  dbMax: number = 0;

  @Input()
  volume: number;

  @Output()
  volumeChange = new EventEmitter<number>();

  constructor(public changeDetectorRef: ChangeDetectorRef) {}

  private convertVolumePercToDb(volumePerc: number) {
    if(volumePerc === 0) {
      return this.dbMin;
    }
    return Math.round(20.0 * Math.log10((volumePerc * 100) / 100) * 10) / 10;
  }

  private convertVolumeDbToPerc(volumeDb: number) {
    if (!isNaN(volumeDb) && volumeDb >= this.dbMin && volumeDb <= 0) {
      if(volumeDb === this.dbMin) {
        return 0;
      }
      return Math.pow(10, volumeDb / 20.0);
    }
  }

  get volumeSliderValue(): number {
    return this.convertVolumePercToDb(this.volume);
  }

  set volumeSliderValue(value: number) {
    this.volume = this.convertVolumeDbToPerc(value);
    this.volumeChange.emit(this.volume);
  }

  get volumeInputValue(): number {
    return this.convertVolumePercToDb(this.volume) * -1;
  }

  set volumeInputValue(value: number) {
    let volumePerc = this.convertVolumeDbToPerc(value * -1);
    if (volumePerc) {
      this.volume = volumePerc;
      this.volumeChange.emit(this.volume);
    }
  }
}
