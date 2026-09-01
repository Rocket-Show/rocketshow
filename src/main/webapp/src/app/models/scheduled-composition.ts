export class ScheduledComposition {
  uuid: string = "";
  enabled: boolean = true;
  compositionName: string;
  scheduleType: string = "INTERVAL";
  intervalValue: number = 5;
  intervalUnit: string = "MINUTES";
  timeOfDay: string = "20:00";

  // ISO-8601 weekdays (1 = Monday ... 7 = Sunday)
  weekdayList: number[] = [1];

  dayOfMonth: number = 1;
  monthOfYear: number = 1;

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.uuid = data.uuid;
    this.enabled = data.enabled;
    this.compositionName = data.compositionName;
    this.scheduleType = data.scheduleType;
    this.intervalValue = data.intervalValue;
    this.intervalUnit = data.intervalUnit;
    this.timeOfDay = data.timeOfDay;
    this.weekdayList = data.weekdayList ? [...data.weekdayList] : [];
    this.dayOfMonth = data.dayOfMonth;
    this.monthOfYear = data.monthOfYear;
  }
}
