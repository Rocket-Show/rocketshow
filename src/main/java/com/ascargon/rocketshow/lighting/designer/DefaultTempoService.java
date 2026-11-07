package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.midi.MidiClockTempo;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.session.SessionService;
import com.ascargon.rocketshow.composition.SetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The tempo from outside (see TempoService). The tempo tapped or set is remembered with the session; the one of a MIDI
 * clock only while it runs.
 *
 * @author Moritz A. Vieli
 */
@Service
public class DefaultTempoService implements TempoService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultTempoService.class);

    // a tap after this long starts tapping anew, and how many taps the tempo is taken from at the most
    static final long TAP_TIMEOUT_MILLIS = 2000;
    private static final int TAP_COUNT = 8;

    // a MIDI clock without a tick for this long doesn't run anymore
    private static final long CLOCK_TIMEOUT_MILLIS = 1000;

    // the tempi the beats can run at
    static final double MIN_BEATS_PER_MINUTE = 20;
    static final double MAX_BEATS_PER_MINUTE = 300;

    private final DesignerService designerService;
    private final SessionService sessionService;
    private final NotificationService notificationService;
    private final PlayerService playerService;
    private final SetService setService;

    // the beats tapped last (real time) and where the tempo from outside is from
    private final List<Long> taps = new ArrayList<>();
    private String source = "project";

    // the MIDI clock: its tempo, when it ticked last (real time), whether its next tick is the first beat of a bar or
    // falls on the song position passed last (in beats)
    private final MidiClockTempo midiClockTempo = new MidiClockTempo();
    private Long lastClockTickMillis;
    private boolean clockStarting = false;
    private double clockStartBeats = 0;
    private double songPositionBeats = 0;

    // the clock everything is timed by (another one in the tests)
    private LongSupplier clock = System::currentTimeMillis;

    // Lazy load the notification and player service to avoid a circular dependency: they tell the apps the state of
    // the device, which includes the tempo.
    public DefaultTempoService(DesignerService designerService, SessionService sessionService, @Lazy NotificationService notificationService, @Lazy PlayerService playerService, SetService setService) {
        this.designerService = designerService;
        this.sessionService = sessionService;
        this.notificationService = notificationService;
        this.playerService = playerService;
        this.setService = setService;

        // the tempo tapped or set before the restart
        Double remembered = sessionService.getSession().getTempoBeatsPerMinute();

        if (remembered != null) {
            source = "set";
            designerService.setOutsideBeatsPerMinute(clamp(remembered));
        }
    }

    // the clock everything is timed by (for the tests)
    void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    private static double clamp(double beatsPerMinute) {
        return Math.max(MIN_BEATS_PER_MINUTE, Math.min(MAX_BEATS_PER_MINUTE, beatsPerMinute));
    }

    private static double round(double beatsPerMinute) {
        return Math.round(beatsPerMinute * 10) / 10.0;
    }

    // tell every app what changed
    private void notifyClients() {
        try {
            notificationService.notifyClients(playerService, setService);
        } catch (Exception e) {
            logger.error("Could not notify the clients about the tempo", e);
        }
    }

    private boolean isClockRunning(long now) {
        return lastClockTickMillis != null && now - lastClockTickMillis < CLOCK_TIMEOUT_MILLIS;
    }

    // the tempo from outside, tapped or set (remembered with the session), from the passed source
    private void setOutside(Double beatsPerMinute, String source) {
        this.source = beatsPerMinute == null ? "project" : source;
        Double tempo = beatsPerMinute == null ? null : clamp(beatsPerMinute);
        designerService.setOutsideBeatsPerMinute(tempo);
        sessionService.getSession().setTempoBeatsPerMinute(tempo == null ? null : round(tempo));
        sessionService.save();
    }

    // the tempo playing, or the default one of the curves, while nothing plays
    private double getPlayingBeatsPerMinute() {
        Double beatsPerMinute = designerService.getTempoBeatsPerMinute();
        return beatsPerMinute == null ? designerService.getBeatClock().getBeatsPerMinute() : beatsPerMinute;
    }

    @Override
    public synchronized void tap() {
        long now = clock.getAsLong();

        if (isClockRunning(now)) {
            // a MIDI clock running gives the tempo and the beats
            return;
        }

        if (taps.isEmpty() || now - taps.getLast() > TAP_TIMEOUT_MILLIS) {
            // the first beat after a pause: the first beat of a bar, at once
            taps.clear();
            taps.add(now);
            designerService.getBeatClock().beatOne(getBeatsPerBar(), true, now);
            notifyClients();
            return;
        }

        taps.add(now);

        while (taps.size() > TAP_COUNT) {
            taps.removeFirst();
        }

        // the tempo of the beats tapped, and the beats moved onto them smoothly
        double beatMillis = (double) (taps.getLast() - taps.getFirst()) / (taps.size() - 1);
        setOutside(60000 / beatMillis, "tap");
        designerService.getBeatClock().alignBeat(now);
        notifyClients();
    }

    @Override
    public synchronized void beatOne() {
        designerService.getBeatClock().beatOne(getBeatsPerBar(), false, clock.getAsLong());
        notifyClients();
    }

    @Override
    public synchronized void setBeatsPerMinute(Double beatsPerMinute) {
        taps.clear();
        setOutside(beatsPerMinute, "set");
        notifyClients();
    }

    // the tempo playing changed by hand: a tempo tapped stays tapped
    private void change(double beatsPerMinute) {
        if (isClockRunning(clock.getAsLong())) {
            // a MIDI clock running gives the tempo
            return;
        }

        setOutside(beatsPerMinute, "tap".equals(source) ? "tap" : "set");
        notifyClients();
    }

    @Override
    public synchronized void halve() {
        change(getPlayingBeatsPerMinute() / 2);
    }

    @Override
    public synchronized void doubleTempo() {
        change(getPlayingBeatsPerMinute() * 2);
    }

    @Override
    public synchronized void nudge(double beatsPerMinute) {
        change(getPlayingBeatsPerMinute() + beatsPerMinute);
    }

    // ---- a MIDI clock ----

    @Override
    public synchronized void midiClockTick(long nanos) {
        long now = clock.getAsLong();
        boolean wasRunning = isClockRunning(now);
        lastClockTickMillis = now;

        if (clockStarting) {
            // the first tick of a clock started (or continued): it falls on its beat
            clockStarting = false;
            taps.clear();
            designerService.getBeatClock().setBeats(clockStartBeats, now);
        }

        MidiClockTempo.Beat beat = midiClockTempo.tick(nanos);

        if (beat == null || beat.beatsPerMinute() < MIN_BEATS_PER_MINUTE || beat.beatsPerMinute() > MAX_BEATS_PER_MINUTE) {
            return;
        }

        // the apps are only told of a tempo changed by a whole beat per minute: a clock ticks all the time
        Double before = designerService.getOutsideBeatsPerMinute();
        boolean told = wasRunning && "clock".equals(source) && before != null && Math.round(before) == Math.round(beat.beatsPerMinute());

        // a MIDI clock isn't remembered with the session: it gives the tempo while it runs
        source = "clock";
        designerService.setOutsideBeatsPerMinute(beat.beatsPerMinute());

        if (beat.onTheBeat()) {
            // the beats of a clock started fall on its beats
            designerService.getBeatClock().alignBeat(now);
        }

        if (!told) {
            notifyClients();
        }
    }

    @Override
    public synchronized void midiClockStart() {
        midiClockTempo.start();
        clockStarting = true;
        clockStartBeats = nextBar();
        songPositionBeats = 0;
    }

    @Override
    public synchronized void midiClockContinue() {
        midiClockTempo.start();
        clockStarting = true;

        // from the song position, on the bars of the beats
        clockStartBeats = nextBar() + songPositionBeats;
    }

    // the first beat of the next bar of the beats, where a clock started counts from
    private double nextBar() {
        int bar = Math.max(1, getBeatsPerBar());
        double beats = designerService.getBeatClock().getBeats(clock.getAsLong());
        return Math.ceil(beats / bar - 1e-9) * bar;
    }

    @Override
    public synchronized void midiClockStop() {
        midiClockTempo.stop();
        clockStarting = false;
        lastClockTickMillis = null;
        notifyClients();
    }

    @Override
    public synchronized void midiSongPosition(int sixteenths) {
        songPositionBeats = Math.max(0, sixteenths) / 4.0;
    }

    // ---- what plays ----

    @Override
    public Double getBeatsPerMinute() {
        return designerService.getTempoBeatsPerMinute();
    }

    @Override
    public synchronized String getSource() {
        return designerService.getOutsideBeatsPerMinute() == null ? "project" : source;
    }

    @Override
    public double getBeats() {
        return designerService.getBeatClock().getBeats(clock.getAsLong());
    }

    @Override
    public int getBeatsPerBar() {
        return designerService.getBeatsPerBar();
    }
}
