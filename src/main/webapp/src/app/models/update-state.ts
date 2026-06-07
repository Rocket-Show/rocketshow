export class UpdateState {
    step: string;
    updatingFromVersion: string;
    progressPercentage: number;
    progressMessage: string;
    error: string;

    constructor(data?: any) {
        if (!data) {
            return;
        }

        this.step = data.step;
        this.updatingFromVersion = data.updatingFromVersion;
        this.progressPercentage = data.progressPercentage;
        this.progressMessage = data.progressMessage;
        this.error = data.error;
    }
}
