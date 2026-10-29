package com.ascargon.rocketshow.selftest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Under-voltage and throttling of a Raspberry Pi since its start, as its firmware tells (vcgencmd). A weak power
 * supply causes audio dropouts and broken SD cards. Runs after the other checks, which load the unit.
 */
@Service
class PowerSelfTest implements SelfTestStep {

    private final static Logger logger = LoggerFactory.getLogger(PowerSelfTest.class);

    static final String POWER = "power";

    // The bits of "vcgencmd get_throttled"
    private static final long UNDER_VOLTAGE = 1;
    private static final long FREQUENCY_CAPPED = 1 << 1;
    private static final long THROTTLED = 1 << 2;
    private static final long SOFT_TEMPERATURE_LIMIT = 1 << 3;
    private static final long SINCE_START = 16;

    // E.g. "EXT5V_V volt(24)=5.13428000V" of a Raspberry Pi 5
    private static final Pattern VOLTAGE_PATTERN = Pattern.compile("=([\\d.]+)V");

    private final SelfTestSystem system;

    PowerSelfTest(SelfTestSystem system) {
        this.system = system;
    }

    @Override
    public List<String> getCheckNames() {
        return List.of(POWER);
    }

    @Override
    public void run(SelfTestContext context) {
        SelfTestSystem.CommandResult result;

        try {
            result = system.run("vcgencmd", "get_throttled");
        } catch (Exception e) {
            context.report(SelfTestCheck.skipped(POWER, null, "vcgencmd is not available, e.g. not on a Raspberry Pi"));
            return;
        }

        if (result.exitCode() != 0 || !result.output().startsWith("throttled=")) {
            context.report(SelfTestCheck.failed(POWER, null, "vcgencmd get_throttled answered: " + result.output()));
            return;
        }

        long flags = Long.decode(result.output().substring("throttled=".length()).trim());
        context.report(evaluate(flags, getVoltage()));
    }

    // The voltage of the power supply, only a Raspberry Pi 5 measures it
    private String getVoltage() {
        try {
            Matcher matcher = VOLTAGE_PATTERN.matcher(system.run("vcgencmd", "pmic_read_adc", "EXT5V_V").output());
            if (matcher.find()) {
                return String.format(Locale.ROOT, "%.2f V", Double.parseDouble(matcher.group(1)));
            }
        } catch (Exception e) {
            logger.debug("Could not read the voltage of the power supply", e);
        }

        return null;
    }

    // Package visible for the tests
    static SelfTestCheck evaluate(long flags, String voltage) {
        List<String> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        if ((flags & UNDER_VOLTAGE) != 0) {
            problems.add("under-voltage now");
        } else if ((flags & (UNDER_VOLTAGE << SINCE_START)) != 0) {
            problems.add("under-voltage since the start");
        }

        if ((flags & THROTTLED) != 0) {
            problems.add("throttled now");
        } else if ((flags & (THROTTLED << SINCE_START)) != 0) {
            problems.add("throttled since the start");
        }

        if ((flags & (FREQUENCY_CAPPED | FREQUENCY_CAPPED << SINCE_START)) != 0) {
            notes.add("the CPU frequency was capped");
        }

        if ((flags & (SOFT_TEMPERATURE_LIMIT | SOFT_TEMPERATURE_LIMIT << SINCE_START)) != 0) {
            notes.add("the soft temperature limit was reached");
        }

        String noteText = notes.isEmpty() ? "" : ", " + String.join(", ", notes);

        if (!problems.isEmpty()) {
            String problemText = String.join(", ", problems);
            String hint = problemText.contains("under-voltage") ? ": the power supply or its cable is too weak" : ": too hot";
            return SelfTestCheck.failed(POWER, voltage, Character.toUpperCase(problemText.charAt(0)) + problemText.substring(1) + hint + noteText);
        }

        return SelfTestCheck.passed(POWER, voltage, "No under-voltage or throttling since the start" + noteText);
    }

}
