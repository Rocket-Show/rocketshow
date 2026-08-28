import { Component, EventEmitter, Input, OnDestroy, Output } from "@angular/core";
import { TranslateService } from "@ngx-translate/core";
import { Subscription } from "rxjs";
import { Composition } from "../../../models/composition";
import { ScheduledComposition } from "../../../models/scheduled-composition";
import { CompositionService } from "../../../services/composition.service";

@Component({
    selector: "app-scheduled-composition",
    templateUrl: "./scheduled-composition.component.html",
    styleUrl: "./scheduled-composition.component.scss",
    standalone: false
})
export class ScheduledCompositionComponent implements OnDestroy {
  @Input()
  scheduledComposition: ScheduledComposition;

  @Input()
  index: number;

  @Output()
  delete = new EventEmitter<number>();

  private langChangeSubscription: Subscription;

  selectUndefinedOptionValue: any = undefined;

  compositions: Composition[] = [];

  scheduleTypeList: string[] = [
    "INTERVAL",
    "DAILY",
    "WEEKLY",
    "MONTHLY",
    "YEARLY",
  ];
  intervalUnitList: string[] = [
    "SECONDS",
    "MINUTES",
    "HOURS",
    "DAYS",
    "WEEKS",
  ];

  // Weekdays and months in the language of the user
  weekdays: { value: number; label: string }[] = [];
  months: { value: number; label: string }[] = [];

  dayOfMonthList: number[] = [];

  constructor(
    private compositionService: CompositionService,
    private translateService: TranslateService
  ) {
    this.compositionService
      .getCompositions()
      .subscribe((compositions: Composition[]) => {
        this.compositions = compositions;
      });

    for (let dayOfMonth = 1; dayOfMonth <= 31; dayOfMonth++) {
      this.dayOfMonthList.push(dayOfMonth);
    }

    this.loadWeekdaysAndMonths();

    this.langChangeSubscription = this.translateService.onLangChange.subscribe(
      () => {
        this.loadWeekdaysAndMonths();
      }
    );
  }

  ngOnDestroy() {
    this.langChangeSubscription.unsubscribe();
  }

  private loadWeekdaysAndMonths() {
    let language =
      this.translateService.currentLang ||
      this.translateService.defaultLang ||
      "en";

    let weekdayFormat = new Intl.DateTimeFormat(language, {
      weekday: "long",
      timeZone: "UTC",
    });

    this.weekdays = [];

    for (let weekday = 1; weekday <= 7; weekday++) {
      // January 1, 2024 was a Monday (ISO-8601 weekday 1)
      this.weekdays.push({
        value: weekday,
        label: weekdayFormat.format(new Date(Date.UTC(2024, 0, weekday))),
      });
    }

    let monthFormat = new Intl.DateTimeFormat(language, {
      month: "long",
      timeZone: "UTC",
    });

    this.months = [];

    for (let month = 1; month <= 12; month++) {
      this.months.push({
        value: month,
        label: monthFormat.format(new Date(Date.UTC(2024, month - 1, 1))),
      });
    }
  }

  isWeekdaySelected(weekday: number): boolean {
    return this.scheduledComposition.weekdayList.indexOf(weekday) > -1;
  }

  setWeekdaySelected(weekday: number, selected: boolean) {
    let index = this.scheduledComposition.weekdayList.indexOf(weekday);

    if (selected && index === -1) {
      this.scheduledComposition.weekdayList.push(weekday);
    } else if (!selected && index > -1) {
      this.scheduledComposition.weekdayList.splice(index, 1);
    }
  }
}
