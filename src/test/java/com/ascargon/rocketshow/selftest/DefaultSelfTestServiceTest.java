package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.update.VersionInfo;
import com.ascargon.rocketshow.update.VersionService;
import com.ascargon.rocketshow.util.DeviceInformation;
import com.ascargon.rocketshow.util.DeviceInformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Running the steps of the self-test, with steps of the test: what is refused, skipped, expected and restored.
 */
class DefaultSelfTestServiceTest {

    private final PlayerService playerService = mock(PlayerService.class);
    private final VersionService versionService = mock(VersionService.class);
    private final DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);

    // What happened, in order: prepare, run and restore of each step
    private final List<String> events = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        VersionInfo versionInfo = new VersionInfo();
        versionInfo.setVersion("3.1.0");
        when(versionService.getCurrentVersionInfo()).thenReturn(versionInfo);

        DeviceInformation deviceInformation = new DeviceInformation();
        deviceInformation.setSerial("SPARK-0042");
        when(deviceInformationService.getDeviceInformation()).thenReturn(deviceInformation);
    }

    // A step reporting its checks like the run passed
    private class TestStep implements SelfTestStep {

        private final List<String> checkNames;
        private final Consumer<SelfTestContext> run;

        TestStep(List<String> checkNames, Consumer<SelfTestContext> run) {
            this.checkNames = checkNames;
            this.run = run;
        }

        @Override
        public List<String> getCheckNames() {
            return checkNames;
        }

        @Override
        public void prepare() {
            events.add("prepare " + checkNames.getFirst());
        }

        @Override
        public void run(SelfTestContext context) {
            events.add("run " + checkNames.getFirst());
            run.accept(context);
        }

        @Override
        public void restore() {
            events.add("restore " + checkNames.getFirst());
        }

    }

    private TestStep step(String name, Consumer<SelfTestContext> run) {
        return new TestStep(List.of(name), run);
    }

    private TestStep passing(String name) {
        return step(name, context -> context.report(SelfTestCheck.passed(name, null, "fine")));
    }

    private TestStep skipping(String name) {
        return step(name, context -> context.report(SelfTestCheck.skipped(name, null, "No loopback cable")));
    }

    private DefaultSelfTestService service(SelfTestStep... steps) {
        return new DefaultSelfTestService(playerService, versionService, deviceInformationService, List.of(steps));
    }

    private static SelfTestRequest expecting(String... names) {
        SelfTestRequest request = new SelfTestRequest();
        request.setExpect(new ArrayList<>(List.of(names)));
        return request;
    }

    private static SelfTestCheck check(SelfTestResult result, String name) {
        return result.getChecks().stream().filter(check -> check.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void runsEveryStepAndReportsTheUnit() throws Exception {
        SelfTestResult result = service(passing("health"), passing("audio")).run(null);

        assertEquals(SelfTestOutcome.PASSED, result.getResult());
        assertEquals(List.of("health", "audio"), result.getChecks().stream().map(SelfTestCheck::getName).toList());
        assertEquals("3.1.0", result.getSoftwareVersion());
        assertEquals("SPARK-0042", result.getSerial());
        assertTrue(result.getStartedAt().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ"), result.getStartedAt());
        assertEquals(List.of("prepare health", "prepare audio", "run health", "run audio", "restore health", "restore audio"), events);
    }

    @Test
    void stopsTheDefaultCompositionFirstAndPlaysItAgainAtTheEnd() throws Exception {
        service(passing("audio")).run(new SelfTestRequest());

        InOrder inOrder = inOrder(playerService);
        inOrder.verify(playerService).stopDefaultComposition();
        inOrder.verify(playerService).resumeDefaultComposition();
        assertEquals("prepare audio", events.getFirst());
    }

    @Test
    void refusesWhileACompositionPlays() throws Exception {
        when(playerService.isPlayingComposition()).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service(passing("audio")).run(null));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertTrue(events.isEmpty(), events.toString());
        verify(playerService, never()).stopDefaultComposition();
    }

    @Test
    void refusesWhileAnotherSelfTestRuns() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        DefaultSelfTestService service = service(step("audio", context -> {
            running.countDown();
            try {
                finish.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            context.report(SelfTestCheck.passed("audio", null, "fine"));
        }));

        CompletableFuture<SelfTestResult> first = CompletableFuture.supplyAsync(() -> {
            try {
                return service.run(null);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertTrue(running.await(10, TimeUnit.SECONDS));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.run(null));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        finish.countDown();
        assertEquals(SelfTestOutcome.PASSED, first.get(10, TimeUnit.SECONDS).getResult());

        // And again, once the first one is done
        finish.countDown();
        assertEquals(SelfTestOutcome.PASSED, service.run(null).getResult());
    }

    @Test
    void refusesToExpectACheckWhichDoesNotExist() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service(passing("audio")).run(expecting("midi-loopbak")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("midi-loopbak"), exception.getReason());
        assertTrue(events.isEmpty(), events.toString());
    }

    @Test
    void refusesWhatAStepCannotRunSafely() throws Exception {
        TestStep refusing = new TestStep(List.of("gpio-loopback"), context -> {
        }) {
            @Override
            public void validate(SelfTestRequest request) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GPIO 17 is reserved");
            }
        };

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service(passing("audio"), refusing).run(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(events.isEmpty(), events.toString());
        verify(playerService, never()).stopDefaultComposition();
    }

    @Test
    void aSkippedCheckDoesNotFailTheSelfTest() throws Exception {
        SelfTestResult result = service(passing("midi-out"), skipping("midi-loopback")).run(null);

        assertEquals(SelfTestOutcome.PASSED, result.getResult());
        assertEquals(SelfTestOutcome.SKIPPED, check(result, "midi-loopback").getResult());
    }

    @Test
    void anExpectedCheckFailsWhenSkipped() throws Exception {
        SelfTestResult result = service(passing("midi-out"), skipping("midi-loopback")).run(expecting("midi-loopback"));

        assertEquals(SelfTestOutcome.FAILED, result.getResult());
        assertEquals(SelfTestOutcome.FAILED, check(result, "midi-loopback").getResult());
        assertEquals("Expected, but skipped: No loopback cable", check(result, "midi-loopback").getDetails());
    }

    @Test
    void aFailedCheckFailsTheSelfTest() throws Exception {
        SelfTestResult result = service(passing("health"), step("audio", context -> context.report(SelfTestCheck.failed("audio", "Card 1", "No level measured")))).run(null);

        assertEquals(SelfTestOutcome.FAILED, result.getResult());
        assertEquals(SelfTestOutcome.PASSED, check(result, "health").getResult());
    }

    @Test
    void aThrowingStepFailsItsChecksAndTheOthersStillRunAndRestore() throws Exception {
        TestStep throwing = new TestStep(List.of("midi-out", "midi-loopback"), context -> {
            context.report(SelfTestCheck.passed("midi-out", null, "200 messages sent"));
            throw new IllegalStateException("The MIDI interface was unplugged");
        });

        SelfTestResult result = service(passing("audio"), throwing, passing("dmx")).run(null);

        assertEquals(SelfTestOutcome.FAILED, result.getResult());
        assertEquals(SelfTestOutcome.PASSED, check(result, "midi-out").getResult());
        assertEquals(SelfTestOutcome.FAILED, check(result, "midi-loopback").getResult());
        assertEquals("The MIDI interface was unplugged", check(result, "midi-loopback").getDetails());
        assertEquals(SelfTestOutcome.PASSED, check(result, "dmx").getResult());
        assertTrue(events.containsAll(List.of("restore audio", "restore midi-out", "restore dmx")), events.toString());
        verify(playerService).resumeDefaultComposition();
    }

    @Test
    void restoresWhenAStepCannotBePrepared() throws Exception {
        TestStep failingPrepare = new TestStep(List.of("gpio-out"), context -> {
        }) {
            @Override
            public void prepare() {
                throw new IllegalStateException("GPIO is gone");
            }
        };

        assertThrows(IllegalStateException.class, () -> service(passing("audio"), failingPrepare).run(null));

        assertTrue(events.containsAll(List.of("restore audio", "restore gpio-out")), events.toString());
        verify(playerService).resumeDefaultComposition();
    }

    @Test
    void oneStepWhichCannotRestoreDoesNotKeepTheOthersFromIt() throws Exception {
        TestStep failingRestore = new TestStep(List.of("gpio-out"), context -> context.report(SelfTestCheck.passed("gpio-out", null, "fine"))) {
            @Override
            public void restore() {
                throw new IllegalStateException("GPIO is gone");
            }
        };

        SelfTestResult result = service(failingRestore, passing("dmx")).run(null);

        assertEquals(SelfTestOutcome.PASSED, result.getResult());
        assertTrue(events.contains("restore dmx"), events.toString());
        verify(playerService).resumeDefaultComposition();
    }

    @Test
    void stopsWhenACompositionStarts() throws Exception {
        AtomicBoolean playing = new AtomicBoolean(false);
        when(playerService.isPlayingComposition()).thenAnswer(invocation -> playing.get());

        TestStep startingAShow = step("audio", context -> {
            // Someone pressed play meanwhile
            playing.set(true);
            try {
                assertTrue(!context.await(10_000));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            context.report(SelfTestCheck.skipped("audio", null, SelfTestContext.STOPPED));
        });

        long start = System.currentTimeMillis();
        SelfTestResult result = service(passing("health"), startingAShow, passing("dmx")).run(expecting("dmx"));

        assertTrue(System.currentTimeMillis() - start < 5000, "Waited for the whole check");
        assertEquals(SelfTestOutcome.PASSED, check(result, "health").getResult());
        assertEquals(SelfTestOutcome.SKIPPED, check(result, "audio").getResult());
        // The remaining steps don't run, an expected one fails
        assertTrue(!events.contains("run dmx"), events.toString());
        assertEquals(SelfTestOutcome.FAILED, check(result, "dmx").getResult());
        assertEquals("Expected, but skipped: " + SelfTestContext.STOPPED, check(result, "dmx").getDetails());
        assertTrue(events.contains("restore dmx"), events.toString());
    }

    @Test
    void everyCheckGetsAResult() throws Exception {
        TestStep silent = new TestStep(List.of("gpio-out", "gpio-loopback"), context -> context.report(SelfTestCheck.passed("gpio-out", null, "fine")));

        SelfTestResult result = service(silent).run(null);

        assertEquals(SelfTestOutcome.FAILED, check(result, "gpio-loopback").getResult());
        assertEquals("No result", check(result, "gpio-loopback").getDetails());
    }

    @Test
    void reportsTheSelfTestWithoutAVersion() throws Exception {
        when(versionService.getCurrentVersionInfo()).thenThrow(new IllegalStateException("No version file"));

        SelfTestResult result = service(passing("health")).run(null);

        assertEquals(SelfTestOutcome.PASSED, result.getResult());
        assertNull(result.getSoftwareVersion());
    }

}
