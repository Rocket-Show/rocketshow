package com.ascargon.rocketshow.scheduler;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Starting the scheduled compositions at their time.
 */
class SchedulerTimingTest {

    private final static ZoneId ZONE = ZoneId.of("Europe/Zurich");

    // Runs from the passed time on and can be set, like by the internet
    private static class SettableClock extends Clock {

        private final long startNanos = System.nanoTime();
        private volatile Instant start;

        SettableClock(ZonedDateTime time) {
            set(time);
        }

        void set(ZonedDateTime time) {
            start = time.toInstant().minusNanos(System.nanoTime() - startNanos);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return start.plusNanos(System.nanoTime() - startNanos);
        }

    }

    private final Settings settings = new Settings();
    private final SettingsService settingsService = mock(SettingsService.class);
    private final CompositionService compositionService = mock(CompositionService.class);
    private final PlayerService playerService = mock(PlayerService.class);
    private DefaultSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        when(settingsService.getSettings()).thenReturn(settings);
        when(compositionService.getComposition("Show")).thenReturn(new Composition());
    }

    @AfterEach
    void tearDown() {
        if (schedulerService != null) {
            schedulerService.close();
        }
    }

    private static ScheduledComposition daily(String compositionName, String timeOfDay) {
        ScheduledComposition scheduledComposition = new ScheduledComposition();
        scheduledComposition.setCompositionName(compositionName);
        scheduledComposition.setScheduleType(ScheduledComposition.ScheduleType.DAILY);
        scheduledComposition.setTimeOfDay(timeOfDay);
        return scheduledComposition;
    }

    private static ScheduledComposition everySecond(String compositionName) {
        ScheduledComposition scheduledComposition = new ScheduledComposition();
        scheduledComposition.setCompositionName(compositionName);
        scheduledComposition.setScheduleType(ScheduledComposition.ScheduleType.INTERVAL);
        scheduledComposition.setIntervalValue(1);
        scheduledComposition.setIntervalUnit(ScheduledComposition.IntervalUnit.SECONDS);
        return scheduledComposition;
    }

    @Test
    void startsADailyCompositionAtItsTime() throws Exception {
        settings.getScheduledCompositionList().add(daily("Show", "20:00"));

        schedulerService = new DefaultSchedulerService(settingsService, compositionService, playerService, new SettableClock(ZonedDateTime.of(2026, 10, 25, 19, 59, 59, 0, ZONE)));

        verify(playerService, timeout(3000)).play();
        // Once
        verify(playerService, after(1000).times(1)).play();
        verify(playerService).setCompositionName("Show");
    }

    @Test
    void plansAgainWhenTheClockIsSet() throws Exception {
        settings.getScheduledCompositionList().add(daily("Show", "20:00"));

        // Started with the time it was switched off at yesterday
        SettableClock clock = new SettableClock(ZonedDateTime.of(2026, 10, 24, 19, 59, 58, 0, ZONE));
        schedulerService = new DefaultSchedulerService(settingsService, compositionService, playerService, clock);

        // The right time from the internet
        clock.set(ZonedDateTime.of(2026, 10, 25, 18, 0, 0, 0, ZONE));
        schedulerService.checkClock();

        // Not at 18:00
        verify(playerService, after(3500).never()).play();

        // At 20:00
        clock.set(ZonedDateTime.of(2026, 10, 25, 19, 59, 59, 0, ZONE));
        schedulerService.checkClock();
        verify(playerService, timeout(3000)).play();
        verify(playerService, after(1000).times(1)).play();
    }

    @Test
    void startsAnIntervalAgainUntilItIsDisabled() throws Exception {
        ScheduledComposition scheduledComposition = everySecond("Show");
        settings.getScheduledCompositionList().add(scheduledComposition);

        schedulerService = new DefaultSchedulerService(settingsService, compositionService, playerService, Clock.system(ZONE));
        verify(playerService, timeout(3500).atLeast(2)).play();

        scheduledComposition.setEnabled(false);
        schedulerService.reload();
        clearInvocations(playerService);

        verify(playerService, after(2000).never()).play();
    }

    @Test
    void startsNoCompositionWhichDoesNotExist() throws Exception {
        settings.getScheduledCompositionList().add(everySecond("Deleted"));

        schedulerService = new DefaultSchedulerService(settingsService, compositionService, playerService, Clock.system(ZONE));

        verify(playerService, after(2500).never()).play();
    }

}
