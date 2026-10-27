export class Action {
  compositionName: string;
  // Named like in the API of the unit
  remoteDeviceNames: string[] = [];
  executeLocally: boolean = true;
  type: string;

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.compositionName = data.compositionName;

    if (data.remoteDeviceNames) {
      for (let remoteDevice of data.remoteDeviceNames) {
        this.remoteDeviceNames.push(remoteDevice);
      }
    }

    this.executeLocally = data.executeLocally;
    this.type = data.type;
  }
}
