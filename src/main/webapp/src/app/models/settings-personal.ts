export class SettingsPersonal {
    instrumentUuid: string;

    constructor(data?: any) {
        if (!data) {
            return;
        }

        this.instrumentUuid = data.instrumentUuid;
    }

}
