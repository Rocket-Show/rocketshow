export class OlaPort {
  id: string = "";
  device: string = "";
  description: string = "";
  output: boolean = false;

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.id = data.id;
    this.device = data.device;
    this.description = data.description;
    this.output = data.output;
  }

  get displayName(): string {
    if (this.device && this.description) {
      return this.id + " - " + this.device + " - " + this.description;
    }

    if (this.device) {
      return this.id + " - " + this.device;
    }

    if (this.description) {
      return this.id + " - " + this.description;
    }

    return this.id;
  }
}
