package com.ascargon.rocketshow.scheduler;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A composition, which is started by a timer (e.g. every 5 minutes or each day at 20:00).
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
@Getter
@Setter
@EqualsAndHashCode
public class ScheduledComposition {

    // How the execution times are calculated
    public enum ScheduleType {
        // Repeatedly, after a fixed amount of time
        INTERVAL,

        // Each day at a specific time
        DAILY,

        // On specific weekdays at a specific time
        WEEKLY,

        // Each month on a specific day at a specific time
        MONTHLY,

        // Each year on a specific date at a specific time
        YEARLY
    }

    // The unit of the interval, if the schedule type is INTERVAL
    public enum IntervalUnit {
        SECONDS,
        MINUTES,
        HOURS,
        DAYS,
        WEEKS
    }

    private String uuid;

    // Should this entry be taken into account?
    private boolean enabled = true;

    // The name of the composition to be started
    private String compositionName;

    private ScheduleType scheduleType = ScheduleType.INTERVAL;

    // Start the composition every intervalValue intervalUnits (e.g. every 5 minutes),
    // if the schedule type is INTERVAL
    private Integer intervalValue = 5;
    private IntervalUnit intervalUnit = IntervalUnit.MINUTES;

    // The local time of the day (e.g. "20:00") on all schedule types except INTERVAL
    private String timeOfDay = "20:00";

    // The weekdays to start the composition on (ISO-8601: 1 = Monday ... 7 = Sunday),
    // if the schedule type is WEEKLY
    private List<Integer> weekdayList = new ArrayList<>();

    // The day of the month (1 - 31), if the schedule type is MONTHLY or YEARLY. Months, which are
    // shorter than the specified day, use their last day.
    private Integer dayOfMonth = 1;

    // The month (1 = January ... 12 = December), if the schedule type is YEARLY
    private Integer monthOfYear = 1;

    @XmlElement(name = "weekday")
    @XmlElementWrapper(name = "weekdayList")
    public List<Integer> getWeekdayList() {
        return weekdayList;
    }

}
