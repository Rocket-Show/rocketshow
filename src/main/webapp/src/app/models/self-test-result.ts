// The result of one check of the self-test of the unit, e.g. the audio of one sound card
export class SelfTestCheck {
  name: string;
  target: string;
  // PASSED, FAILED or SKIPPED
  result: string;
  details: string;

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.name = data.name;
    this.target = data.target;
    this.result = data.result;
    this.details = data.details;
  }
}

export class SelfTestResult {
  // FAILED, if any check failed
  result: string;
  startedAt: string;
  durationMillis: number;
  softwareVersion: string;
  serial: string;
  checks: SelfTestCheck[] = [];

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.result = data.result;
    this.startedAt = data.startedAt;
    this.durationMillis = data.durationMillis;
    this.softwareVersion = data.softwareVersion;
    this.serial = data.serial;
    this.checks = (data.checks || []).map((check: any) => new SelfTestCheck(check));
  }
}
