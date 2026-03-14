
export class ApiKey {

  uuid: string;

  // Only stored once, hashed and never loaded again
  key: string;

  description: string;

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.uuid = data.uuid;
    this.description = data.description;
  }
}
