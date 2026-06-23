export class RaspberryPiPin {
  id: number;
  displayText: string;

  constructor(id: number, displayText?: string) {
    this.id = id;
    this.displayText = displayText;

    if (!this.displayText) {
      this.displayText = this.id.toString();
    }
  }
}
