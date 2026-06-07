export class OlaPlugin {
  id: number = 0;
  name: string = "";
  conflictList: OlaPlugin[] = [];

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.id = data.id;
    this.name = data.name;

    if (data.conflictList) {
      for (let conflict of data.conflictList) {
        this.conflictList.push(new OlaPlugin(conflict));
      }
    }
  }
}
