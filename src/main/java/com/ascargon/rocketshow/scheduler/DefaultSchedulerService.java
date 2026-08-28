package com.ascargon.rocketshow.scheduler;

import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.settings.ScheduledCompositionsChangedEvent;
import com.ascargon.rocketshow.settings.SettingsService;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DefaultSchedulerService implements SchedulerService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultSchedulerService.class);

    private final SettingsService settingsService;
    private final CompositionService compositionService;
    private final PlayerService playerService;

    // Plans the next execution of each entry
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "scheduler");
        thread.setDaemon(true);
        return thread;
    });

    // Starts the compositions. Starting a composition may take a while (e.g. because it waits for
    // remote devices to be loaded) and must not delay the next planned executions.
    private final ExecutorService playExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "scheduler-play");
        thread.setDaemon(true);
        return thread;
    });

    private final List<ScheduledEntry> scheduledEntryList = new ArrayList<>();

    // Incremented on each reload to let already planned executions of an outdated schedule know
    // that they must neither be executed nor planned again
    private final AtomicLong generation = new AtomicLong();

    public DefaultSchedulerService(
            SettingsService settingsService,
            CompositionService compositionService,

            // Lazy load the playerService to avoid a circular dependency, because the playerService
            // is only needed as soon as a composition is actually started
            @Lazy PlayerService playerService
    ) {
        this.settingsService = settingsService;
        this.compositionService = compositionService;
        this.playerService = playerService;

        reload();
    }

    @EventListener
    public void scheduledCompositionsChanged(ScheduledCompositionsChangedEvent event) {
        reload();
    }

    @Override
    public synchronized void reload() {
        long currentGeneration = generation.incrementAndGet();

        for (ScheduledEntry scheduledEntry : scheduledEntryList) {
            scheduledEntry.cancel();
        }

        scheduledEntryList.clear();

        for (ScheduledComposition scheduledComposition : settingsService.getSettings().getScheduledCompositionList()) {
            if (!scheduledComposition.isEnabled()) {
                continue;
            }

            if (scheduledComposition.getCompositionName() == null || scheduledComposition.getCompositionName().isBlank()) {
                logger.warn("No composition is set on a scheduled composition. It will not be started.");
                continue;
            }

            ScheduledEntry scheduledEntry = new ScheduledEntry(scheduledComposition);
            scheduledEntryList.add(scheduledEntry);
            scheduleNext(scheduledEntry, currentGeneration);
        }
    }

    /**
     * Plan the next execution of the specified entry.
     */
    private synchronized void scheduleNext(ScheduledEntry scheduledEntry, long scheduleGeneration) {
        if (scheduleGeneration != generation.get()) {
            // The schedule has been reloaded meanwhile
            return;
        }

        ScheduledComposition scheduledComposition = scheduledEntry.getScheduledComposition();
        Instant nextExecution = getNextExecution(scheduledComposition, ZonedDateTime.now());

        if (nextExecution == null) {
            logger.warn("The schedule of composition '{}' is incomplete. It will not be started.", scheduledComposition.getCompositionName());
            return;
        }

        long delayMillis = Math.max(0, Duration.between(Instant.now(), nextExecution).toMillis());

        logger.debug("Start the composition '{}' in {} millis", scheduledComposition.getCompositionName(), delayMillis);

        scheduledEntry.setFuture(scheduler.schedule(() -> {
            // Plan the next execution first, so it's not delayed by the composition being started
            scheduleNext(scheduledEntry, scheduleGeneration);

            if (scheduleGeneration == generation.get()) {
                playExecutor.execute(() -> startComposition(scheduledComposition));
            }
        }, delayMillis, TimeUnit.MILLISECONDS));
    }

    private void startComposition(ScheduledComposition scheduledComposition) {
        String compositionName = scheduledComposition.getCompositionName();

        if (compositionService.getComposition(compositionName) == null) {
            logger.warn("The scheduled composition '{}' does not exist and cannot be started", compositionName);
            return;
        }

        logger.info("Start the scheduled composition '{}'", compositionName);

        try {
            playerService.setCompositionName(compositionName);
            playerService.play();
        } catch (Exception e) {
            logger.error("Could not start the scheduled composition '{}'", compositionName, e);
        }
    }

    /**
     * Get the next point in time, at which the specified composition has to be started, or null, if the
     * schedule is not defined properly.
     */
    static Instant getNextExecution(ScheduledComposition scheduledComposition, ZonedDateTime now) {
        if (scheduledComposition.getScheduleType() == null) {
            return null;
        }

        if (ScheduledComposition.ScheduleType.INTERVAL.equals(scheduledComposition.getScheduleType())) {
            Long intervalMillis = getIntervalMillis(scheduledComposition);

            if (intervalMillis == null) {
                return null;
            }

            return now.toInstant().plusMillis(intervalMillis);
        }

        // All other schedule types are started at a specific time of the day
        LocalTime timeOfDay = getTimeOfDay(scheduledComposition);

        if (timeOfDay == null) {
            return null;
        }

        switch (scheduledComposition.getScheduleType()) {
            case DAILY -> {
                for (int dayCount = 0; dayCount <= 1; dayCount++) {
                    Instant nextExecution = getExecutionAfter(now.toLocalDate().plusDays(dayCount), timeOfDay, now);

                    if (nextExecution != null) {
                        return nextExecution;
                    }
                }
            }
            case WEEKLY -> {
                List<Integer> weekdayList = getWeekdayList(scheduledComposition);

                if (weekdayList.isEmpty()) {
                    return null;
                }

                // Check the whole next week, starting today
                for (int dayCount = 0; dayCount <= 7; dayCount++) {
                    LocalDate date = now.toLocalDate().plusDays(dayCount);

                    if (!weekdayList.contains(date.getDayOfWeek().getValue())) {
                        continue;
                    }

                    Instant nextExecution = getExecutionAfter(date, timeOfDay, now);

                    if (nextExecution != null) {
                        return nextExecution;
                    }
                }
            }
            case MONTHLY -> {
                Integer dayOfMonth = getDayOfMonth(scheduledComposition);

                if (dayOfMonth == null) {
                    return null;
                }

                for (int monthCount = 0; monthCount <= 1; monthCount++) {
                    Instant nextExecution = getExecutionAfter(getDate(YearMonth.from(now).plusMonths(monthCount), dayOfMonth), timeOfDay, now);

                    if (nextExecution != null) {
                        return nextExecution;
                    }
                }
            }
            case YEARLY -> {
                Integer dayOfMonth = getDayOfMonth(scheduledComposition);
                Integer monthOfYear = getMonthOfYear(scheduledComposition);

                if (dayOfMonth == null || monthOfYear == null) {
                    return null;
                }

                for (int yearCount = 0; yearCount <= 1; yearCount++) {
                    Instant nextExecution = getExecutionAfter(getDate(YearMonth.of(now.getYear() + yearCount, monthOfYear), dayOfMonth), timeOfDay, now);

                    if (nextExecution != null) {
                        return nextExecution;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get the specified date and time, if it lies in the future, otherwise null.
     */
    private static Instant getExecutionAfter(LocalDate date, LocalTime timeOfDay, ZonedDateTime now) {
        ZonedDateTime execution = ZonedDateTime.of(date, timeOfDay, now.getZone());

        if (!execution.isAfter(now)) {
            return null;
        }

        return execution.toInstant();
    }

    /**
     * Get the specified day in the specified month. Months, which are shorter than the specified day,
     * use their last day (e.g. the 31st becomes the 30th in April).
     */
    private static LocalDate getDate(YearMonth yearMonth, int dayOfMonth) {
        return yearMonth.atDay(Math.min(dayOfMonth, yearMonth.lengthOfMonth()));
    }

    private static List<Integer> getWeekdayList(ScheduledComposition scheduledComposition) {
        List<Integer> weekdayList = new ArrayList<>();

        for (Integer weekday : scheduledComposition.getWeekdayList()) {
            if (weekday != null && weekday >= 1 && weekday <= 7) {
                weekdayList.add(weekday);
            }
        }

        return weekdayList;
    }

    private static Integer getDayOfMonth(ScheduledComposition scheduledComposition) {
        Integer dayOfMonth = scheduledComposition.getDayOfMonth();

        if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31) {
            return null;
        }

        return dayOfMonth;
    }

    private static Integer getMonthOfYear(ScheduledComposition scheduledComposition) {
        Integer monthOfYear = scheduledComposition.getMonthOfYear();

        if (monthOfYear == null || monthOfYear < 1 || monthOfYear > 12) {
            return null;
        }

        return monthOfYear;
    }

    private static Long getIntervalMillis(ScheduledComposition scheduledComposition) {
        Integer intervalValue = scheduledComposition.getIntervalValue();

        if (intervalValue == null || intervalValue < 1 || scheduledComposition.getIntervalUnit() == null) {
            return null;
        }

        return switch (scheduledComposition.getIntervalUnit()) {
            case SECONDS -> Duration.ofSeconds(intervalValue).toMillis();
            case MINUTES -> Duration.ofMinutes(intervalValue).toMillis();
            case HOURS -> Duration.ofHours(intervalValue).toMillis();
            case DAYS -> Duration.ofDays(intervalValue).toMillis();
            case WEEKS -> Duration.ofDays(intervalValue * 7L).toMillis();
        };
    }

    private static LocalTime getTimeOfDay(ScheduledComposition scheduledComposition) {
        String timeOfDay = scheduledComposition.getTimeOfDay();

        if (timeOfDay == null || timeOfDay.isBlank()) {
            return null;
        }

        try {
            return LocalTime.parse(timeOfDay.trim());
        } catch (DateTimeParseException e) {
            logger.warn("The time of the day '{}' of a scheduled composition could not be read", timeOfDay);
            return null;
        }
    }

    @PreDestroy
    public void close() {
        scheduler.shutdownNow();
        playExecutor.shutdownNow();
    }

    /**
     * A scheduled composition together with its next planned execution.
     */
    private static class ScheduledEntry {

        @Getter
        private final ScheduledComposition scheduledComposition;

        @Setter
        private ScheduledFuture<?> future;

        private ScheduledEntry(ScheduledComposition scheduledComposition) {
            this.scheduledComposition = scheduledComposition;
        }

        private void cancel() {
            if (future != null) {
                future.cancel(false);
            }
        }

    }

}
