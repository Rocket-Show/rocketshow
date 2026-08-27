package com.ascargon.rocketshow.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultSchedulerServiceTest {

    private final static ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private ScheduledComposition getIntervalComposition(int intervalValue, ScheduledComposition.IntervalUnit intervalUnit) {
        ScheduledComposition scheduledComposition = new ScheduledComposition();
        scheduledComposition.setCompositionName("my composition");
        scheduledComposition.setScheduleType(ScheduledComposition.ScheduleType.INTERVAL);
        scheduledComposition.setIntervalValue(intervalValue);
        scheduledComposition.setIntervalUnit(intervalUnit);
        return scheduledComposition;
    }

    private ScheduledComposition getScheduledComposition(ScheduledComposition.ScheduleType scheduleType, String timeOfDay) {
        ScheduledComposition scheduledComposition = new ScheduledComposition();
        scheduledComposition.setCompositionName("my composition");
        scheduledComposition.setScheduleType(scheduleType);
        scheduledComposition.setTimeOfDay(timeOfDay);
        return scheduledComposition;
    }

    private ScheduledComposition getDailyComposition(String timeOfDay) {
        return getScheduledComposition(ScheduledComposition.ScheduleType.DAILY, timeOfDay);
    }

    private ScheduledComposition getWeeklyComposition(String timeOfDay, Integer... weekdays) {
        ScheduledComposition scheduledComposition = getScheduledComposition(ScheduledComposition.ScheduleType.WEEKLY, timeOfDay);
        scheduledComposition.setWeekdayList(List.of(weekdays));
        return scheduledComposition;
    }

    private ScheduledComposition getMonthlyComposition(String timeOfDay, Integer dayOfMonth) {
        ScheduledComposition scheduledComposition = getScheduledComposition(ScheduledComposition.ScheduleType.MONTHLY, timeOfDay);
        scheduledComposition.setDayOfMonth(dayOfMonth);
        return scheduledComposition;
    }

    private ScheduledComposition getYearlyComposition(String timeOfDay, Integer monthOfYear, Integer dayOfMonth) {
        ScheduledComposition scheduledComposition = getScheduledComposition(ScheduledComposition.ScheduleType.YEARLY, timeOfDay);
        scheduledComposition.setMonthOfYear(monthOfYear);
        scheduledComposition.setDayOfMonth(dayOfMonth);
        return scheduledComposition;
    }

    @Test
    void getNextExecutionAddsTheIntervalToTheCurrentTime() {
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 14, 10, 15, 0, 0, ZONE);

        assertEquals(
                ZonedDateTime.of(2026, 3, 14, 10, 20, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getIntervalComposition(5, ScheduledComposition.IntervalUnit.MINUTES), now));

        assertEquals(
                ZonedDateTime.of(2026, 3, 14, 12, 15, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getIntervalComposition(2, ScheduledComposition.IntervalUnit.HOURS), now));

        assertEquals(
                ZonedDateTime.of(2026, 3, 14, 10, 15, 30, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getIntervalComposition(30, ScheduledComposition.IntervalUnit.SECONDS), now));

        assertEquals(
                ZonedDateTime.of(2026, 3, 17, 10, 15, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getIntervalComposition(3, ScheduledComposition.IntervalUnit.DAYS), now));

        assertEquals(
                ZonedDateTime.of(2026, 3, 28, 10, 15, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getIntervalComposition(2, ScheduledComposition.IntervalUnit.WEEKS), now));
    }

    @Test
    void getNextExecutionReturnsNullOnAnIncompleteInterval() {
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 14, 10, 15, 0, 0, ZONE);

        assertNull(DefaultSchedulerService.getNextExecution(getIntervalComposition(0, ScheduledComposition.IntervalUnit.MINUTES), now));

        ScheduledComposition scheduledComposition = getIntervalComposition(5, ScheduledComposition.IntervalUnit.MINUTES);
        scheduledComposition.setIntervalValue(null);
        assertNull(DefaultSchedulerService.getNextExecution(scheduledComposition, now));
    }

    @Test
    void getNextExecutionUsesTheTimeOfTheSameDayIfItHasNotPassedYet() {
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 14, 10, 15, 0, 0, ZONE);

        assertEquals(
                ZonedDateTime.of(2026, 3, 14, 20, 30, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getDailyComposition("20:30"), now));
    }

    @Test
    void getNextExecutionUsesTheTimeOfTheNextDayIfItHasAlreadyPassed() {
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 14, 20, 30, 0, 0, ZONE);

        assertEquals(
                ZonedDateTime.of(2026, 3, 15, 20, 30, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getDailyComposition("20:30"), now));
    }

    @Test
    void getNextExecutionKeepsTheLocalTimeOverADaylightSavingTimeChange() {
        // The clocks are set forward by one hour in the night from March 28 to March 29, 2026
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 28, 21, 0, 0, 0, ZONE);
        Instant nextExecution = DefaultSchedulerService.getNextExecution(getDailyComposition("20:00"), now);

        assertEquals(
                ZonedDateTime.of(2026, 3, 29, 20, 0, 0, 0, ZONE).toInstant(),
                nextExecution);

        // 22 hours instead of 23, because of the hour, which is skipped in between
        assertEquals(22 * 60 * 60, nextExecution.getEpochSecond() - now.toInstant().getEpochSecond());
    }

    @Test
    void getNextExecutionUsesTheNextSelectedWeekday() {
        // March 14, 2026 is a Saturday
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 14, 10, 15, 0, 0, ZONE);

        // Today, because the time has not passed yet
        assertEquals(
                ZonedDateTime.of(2026, 3, 14, 20, 0, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getWeeklyComposition("20:00", 6, 7), now));

        // Tomorrow (Sunday), because the time has already passed today
        assertEquals(
                ZonedDateTime.of(2026, 3, 15, 8, 0, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getWeeklyComposition("08:00", 6, 7), now));

        // Next Monday
        assertEquals(
                ZonedDateTime.of(2026, 3, 16, 20, 0, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getWeeklyComposition("20:00", 1), now));

        // The same weekday one week later, because the time has already passed today
        assertEquals(
                ZonedDateTime.of(2026, 3, 21, 8, 0, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getWeeklyComposition("08:00", 6), now));
    }

    @Test
    void getNextExecutionReturnsNullWithoutAnyWeekday() {
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 14, 10, 15, 0, 0, ZONE);

        assertNull(DefaultSchedulerService.getNextExecution(getWeeklyComposition("20:00"), now));
        assertNull(DefaultSchedulerService.getNextExecution(getWeeklyComposition("20:00", 0, 8), now));
    }

    @Test
    void getNextExecutionUsesTheNextDayOfTheMonth() {
        // The same month, because the day has not passed yet
        assertEquals(
                ZonedDateTime.of(2026, 3, 15, 20, 0, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getMonthlyComposition("20:00", 15),
                        ZonedDateTime.of(2026, 3, 14, 10, 15, 0, 0, ZONE)));

        // The next month, because the day has already passed
        assertEquals(
                ZonedDateTime.of(2026, 4, 15, 20, 0, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getMonthlyComposition("20:00", 15),
                        ZonedDateTime.of(2026, 3, 15, 20, 0, 0, 0, ZONE)));
    }

    @Test
    void getNextExecutionUsesTheLastDayOfShorterMonths() {
        // February 2026 has 28 days
        assertEquals(
                ZonedDateTime.of(2026, 2, 28, 20, 0, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getMonthlyComposition("20:00", 31),
                        ZonedDateTime.of(2026, 2, 10, 10, 15, 0, 0, ZONE)));

        // April has 30 days
        assertEquals(
                ZonedDateTime.of(2026, 4, 30, 20, 0, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getMonthlyComposition("20:00", 31),
                        ZonedDateTime.of(2026, 4, 10, 10, 15, 0, 0, ZONE)));
    }

    @Test
    void getNextExecutionUsesTheNextDateOfTheYear() {
        // The same year, because the date has not passed yet
        assertEquals(
                ZonedDateTime.of(2026, 8, 1, 20, 0, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getYearlyComposition("20:00", 8, 1),
                        ZonedDateTime.of(2026, 3, 14, 10, 15, 0, 0, ZONE)));

        // The next year, because the date has already passed
        assertEquals(
                ZonedDateTime.of(2027, 2, 1, 20, 0, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getYearlyComposition("20:00", 2, 1),
                        ZonedDateTime.of(2026, 3, 14, 10, 15, 0, 0, ZONE)));

        // February 29 only exists in leap years
        assertEquals(
                ZonedDateTime.of(2027, 2, 28, 20, 0, 0, 0, ZONE).toInstant(),
                DefaultSchedulerService.getNextExecution(getYearlyComposition("20:00", 2, 29),
                        ZonedDateTime.of(2026, 3, 14, 10, 15, 0, 0, ZONE)));
    }

    @Test
    void getNextExecutionReturnsNullOnAnInvalidDate() {
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 14, 10, 15, 0, 0, ZONE);

        assertNull(DefaultSchedulerService.getNextExecution(getMonthlyComposition("20:00", 0), now));
        assertNull(DefaultSchedulerService.getNextExecution(getMonthlyComposition("20:00", 32), now));
        assertNull(DefaultSchedulerService.getNextExecution(getMonthlyComposition("20:00", null), now));
        assertNull(DefaultSchedulerService.getNextExecution(getYearlyComposition("20:00", 13, 1), now));
        assertNull(DefaultSchedulerService.getNextExecution(getYearlyComposition("20:00", null, 1), now));
    }

    @Test
    void getNextExecutionReturnsNullOnAnInvalidTimeOfDay() {
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 14, 10, 15, 0, 0, ZONE);

        assertNull(DefaultSchedulerService.getNextExecution(getDailyComposition(""), now));
        assertNull(DefaultSchedulerService.getNextExecution(getDailyComposition("25:00"), now));
        assertNull(DefaultSchedulerService.getNextExecution(getDailyComposition(null), now));
        assertNull(DefaultSchedulerService.getNextExecution(getWeeklyComposition(null, 1), now));
        assertNull(DefaultSchedulerService.getNextExecution(getMonthlyComposition(null, 1), now));
    }

}
