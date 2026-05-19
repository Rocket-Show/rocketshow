export class LightingUniverseMapping {
  uuid: string;
  name: string = "";
  olaUniverseId: number = 1;
  olaOutputPortId: string = "";

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.uuid = data.uuid;
    this.name = data.name;
    this.olaUniverseId = data.olaUniverseId;
    this.olaOutputPortId = data.olaOutputPortId || "";
  }
}
