export class DeviceInformation {
  available: boolean;
  fileVersion: string;
  country: string;
  serial: string;
  model: string;
  hardwareRevision: string;
  sku: string;

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.available = data.available;
    this.fileVersion = data.fileVersion;
    this.country = data.country;
    this.serial = data.serial;
    this.model = data.model;
    this.hardwareRevision = data.hardwareRevision;
    this.sku = data.sku;
  }
}
