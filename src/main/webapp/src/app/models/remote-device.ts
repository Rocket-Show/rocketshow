export class RemoteDevice {

    name: string = "";
    host: string = "";
    synchronize: boolean = false;
    apiKey: string = "";

    constructor(data?: any) {
        if (!data) {
            return;
        }

        this.name = data.name;
        this.host = data.host;
        this.synchronize = data.synchronize;
        this.apiKey = data.apiKey;
    }

}
