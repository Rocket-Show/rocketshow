export class Action {
  compositionName: string;
  remoteDeviceList: string[] = [];
  executeLocally: boolean = true;

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.compositionName = data.compositionName;

    if (data.remoteDeviceList) {
      for (let remoteDevice of data.remoteDeviceList) {
        this.remoteDeviceList.push(remoteDevice);
      }
    }

    this.executeLocally = data.executeLocally;
  }
}
