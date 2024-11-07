export class OlaPlugin {
  id: number = 0;
  name: string = "";

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.id = data.id;
    this.name = data.name;
  }
}
