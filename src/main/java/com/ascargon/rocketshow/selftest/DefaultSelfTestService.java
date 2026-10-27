package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.update.VersionService;
import com.ascargon.rocketshow.util.DeviceInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runs the steps of the self-test one after the other. Nothing it does may disturb a show: it refuses to run while a
 * composition plays, stops as soon as one starts, and puts back everything its steps changed, also after a failing
 * step.
 */
@Service
public class DefaultSelfTestService implements SelfTestService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultSelfTestService.class);

    private final PlayerService playerService;
    private final VersionService versionService;
    private final DeviceInformationService deviceInformationService;
    private final List<SelfTestStep> steps;

    // The steps switch outputs and divert inputs, so only one self-test runs at a time
    private final ReentrantLock lock = new ReentrantLock();

    @Autowired
    public DefaultSelfTestService(
            PlayerService playerService,
            VersionService versionService,
            DeviceInformationService deviceInformationService,
            HealthSelfTest healthSelfTest,
            StorageSelfTest storageSelfTest,
            AudioSelfTest audioSelfTest,
            VideoSelfTest videoSelfTest,
            MidiSelfTest midiSelfTest,
            GpioSelfTest gpioSelfTest,
            DmxSelfTest dmxSelfTest
    ) {
        this(playerService, versionService, deviceInformationService,
                List.of(healthSelfTest, storageSelfTest, audioSelfTest, videoSelfTest, midiSelfTest, gpioSelfTest, dmxSelfTest));
    }

    // Package visible for the tests, with steps of their own
    DefaultSelfTestService(PlayerService playerService, VersionService versionService, DeviceInformationService deviceInformationService, List<SelfTestStep> steps) {
        this.playerService = playerService;
        this.versionService = versionService;
        this.deviceInformationService = deviceInformationService;
        this.steps = steps;
    }

    @Override
    public SelfTestResult run(SelfTestRequest request) throws Exception {
        // An empty request runs every check the unit has the hardware for
        SelfTestRequest selfTestRequest = request == null ? new SelfTestRequest() : request;
        if (selfTestRequest.getExpect() == null) {
            selfTestRequest.setExpect(new ArrayList<>());
        }
        if (selfTestRequest.getGpioLoopback() == null) {
            selfTestRequest.setGpioLoopback(new ArrayList<>());
        }

        validate(selfTestRequest);

        if (!lock.tryLock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A self-test is running already");
        }

        try {
            // The self-test never interrupts a show. The default composition only plays between them.
            if (playerService.isPlayingComposition()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A composition is playing");
            }

            return runSteps(selfTestRequest);
        } finally {
            lock.unlock();
        }
    }

    private void validate(SelfTestRequest request) {
        Set<String> checkNames = new LinkedHashSet<>();
        for (SelfTestStep step : steps) {
            checkNames.addAll(step.getCheckNames());
        }

        for (String name : request.getExpect()) {
            if (!checkNames.contains(name)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no check " + name + ", only " + String.join(", ", checkNames));
            }
        }

        for (SelfTestStep step : steps) {
            step.validate(request);
        }
    }

    private SelfTestResult runSteps(SelfTestRequest request) throws Exception {
        SelfTestResult result = new SelfTestResult();
        result.setStartedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        long start = System.currentTimeMillis();

        SelfTestContext context = new SelfTestContext(request, playerService::isPlayingComposition);

        logger.info("Self-test started");

        try {
            playerService.stopDefaultComposition();

            for (SelfTestStep step : steps) {
                step.prepare();
            }

            for (SelfTestStep step : steps) {
                runStep(step, context);
            }
        } finally {
            restore();
        }

        for (SelfTestCheck check : context.getChecks()) {
            // E.g. a factory fixture expects its loopback cables
            if (check.getResult() == SelfTestOutcome.SKIPPED && request.getExpect().contains(check.getName())) {
                check.setResult(SelfTestOutcome.FAILED);
                check.setDetails("Expected, but skipped: " + check.getDetails());
            }

            if (check.getResult() == SelfTestOutcome.FAILED) {
                logger.warn("Self-test check {}", check);
            } else {
                logger.info("Self-test check {}", check);
            }
        }

        result.getChecks().addAll(context.getChecks());
        result.setResult(result.getChecks().stream().anyMatch(check -> check.getResult() == SelfTestOutcome.FAILED) ? SelfTestOutcome.FAILED : SelfTestOutcome.PASSED);
        result.setDurationMillis(System.currentTimeMillis() - start);
        result.setSerial(deviceInformationService.getDeviceInformation().getSerial());

        try {
            result.setSoftwareVersion(versionService.getCurrentVersionInfo().getVersion());
        } catch (Exception e) {
            logger.warn("Could not read the software version for the self-test", e);
        }

        logger.info("Self-test {} in {} ms", result.getResult(), result.getDurationMillis());

        return result;
    }

    private void runStep(SelfTestStep step, SelfTestContext context) {
        if (!context.isStopped()) {
            try {
                step.run(context);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                logger.warn("Self-test step {} failed", step.getCheckNames(), e);

                for (String name : step.getCheckNames()) {
                    if (!context.isReported(name)) {
                        context.report(SelfTestCheck.failed(name, null, String.valueOf(e.getMessage())));
                    }
                }
            }
        }

        // Every check gets a result
        for (String name : step.getCheckNames()) {
            if (!context.isReported(name)) {
                context.report(context.isStopped() ? SelfTestCheck.skipped(name, null, SelfTestContext.STOPPED) : SelfTestCheck.failed(name, null, "No result"));
            }
        }
    }

    // Every step puts back what it changed, even when another one can't
    private void restore() {
        for (SelfTestStep step : steps) {
            try {
                step.restore();
            } catch (Exception e) {
                logger.error("Could not restore after the self-test step {}", step.getCheckNames(), e);
            }
        }

        try {
            playerService.resumeDefaultComposition();
        } catch (Exception e) {
            logger.error("Could not play the default composition again after the self-test", e);
        }
    }

}
