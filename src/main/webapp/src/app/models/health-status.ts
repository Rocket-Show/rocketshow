import { DeviceInformation } from "./device-information";

export class HealthStatus {
  healthStatusSeverity: string;
  deviceInformation: DeviceInformation;
  freeDiskSpacePercentage: number;
  freeMemory: number;
  temperature: number;
  recentErrorRate: number;
  softwareVersion: string;
  softwareDate: string;
  eepromVersionDate: string;
  raucSlot: string;
  reasons: string[] = [];

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.healthStatusSeverity = data.healthStatusSeverity;
    this.deviceInformation = data.deviceInformation
      ? new DeviceInformation(data.deviceInformation)
      : undefined;
    this.freeDiskSpacePercentage = data.freeDiskSpacePercentage;
    this.freeMemory = data.freeMemory;
    this.temperature = data.temperature;
    this.recentErrorRate = data.recentErrorRate;
    this.softwareVersion = data.softwareVersion;
    this.softwareDate = data.softwareDate;
    this.eepromVersionDate = data.eepromVersionDate;
    this.raucSlot = data.raucSlot;
    this.reasons = data.reasons || [];
  }
}
