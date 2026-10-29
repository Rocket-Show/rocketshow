package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.midi.InvalidMidiDataException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Service
public class DefaultMidiTimecodeService implements MidiTimecodeService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultMidiTimecodeService.class);

    private final SettingsService settingsService;
    private final MidiDeviceOutService midiDeviceOutService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> sendTimecodeHandle;
    private QuarterFrameSender quarterFrameSender;
    private Object owner;

    // Sends the quarter frames of a start. A receiver puts 8 of them together to one timecode, so they carry the
    // position at the first one, which MidiTimecodeQuarterFrameSequence takes care of.
    private class QuarterFrameSender implements Runnable {

        private final LongSupplier positionMillisSupplier;
        private final MidiTimecodeFrameRate frameRate;
        private final MidiTimecodeQuarterFrameSequence sequence = new MidiTimecodeQuarterFrameSequence();

        QuarterFrameSender(LongSupplier positionMillisSupplier, MidiTimecodeFrameRate frameRate) {
            this.positionMillisSupplier = positionMillisSupplier;
            this.frameRate = frameRate;
        }

        @Override
        public void run() {
            synchronized (DefaultMidiTimecodeService.this) {
                // Stopped, while waiting to send
                if (quarterFrameSender != this) {
                    return;
                }

                Settings settings = settingsService.getSettings();
                if (settings.getMidiTimecodeMode() != MidiTimecodeMode.MASTER || !midiDeviceOutService.isConnected()) {
                    return;
                }

                try {
                    midiDeviceOutService.sendMessage(sequence.next(positionMillisSupplier.getAsLong(), frameRate));
                } catch (InvalidMidiDataException e) {
                    logger.error("Could not send MIDI timecode quarter-frame message", e);
                }
            }
        }

    }

    public DefaultMidiTimecodeService(SettingsService settingsService, MidiDeviceOutService midiDeviceOutService) {
        this.settingsService = settingsService;
        this.midiDeviceOutService = midiDeviceOutService;
    }

    @Override
    public synchronized void start(Object owner, LongSupplier positionMillisSupplier) {
        Settings settings = settingsService.getSettings();
        if (settings.getMidiTimecodeMode() != MidiTimecodeMode.MASTER) {
            return;
        }

        stopCurrent();

        this.owner = owner;
        MidiTimecodeFrameRate frameRate = settings.getMidiTimecodeFrameRate();

        try {
            midiDeviceOutService.sendMessage(MidiTimecodeMessageFactory.createFullFrameMessage(positionMillisSupplier.getAsLong(), frameRate));
        } catch (InvalidMidiDataException e) {
            logger.error("Could not send MIDI timecode full-frame message", e);
        }

        long periodNanos = Math.round(1_000_000_000.0 / (frameRate.getFramesPerSecond() * 4.0));
        quarterFrameSender = new QuarterFrameSender(positionMillisSupplier, frameRate);
        sendTimecodeHandle = scheduler.scheduleAtFixedRate(quarterFrameSender, 0, periodNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public synchronized void stop(Object owner) {
        if (this.owner != owner) {
            return;
        }

        stopCurrent();
    }

    private void stopCurrent() {
        if (sendTimecodeHandle != null) {
            sendTimecodeHandle.cancel(false);
            sendTimecodeHandle = null;
        }
        quarterFrameSender = null;
        owner = null;
    }

    @PreDestroy
    private synchronized void close() {
        stopCurrent();
        scheduler.shutdownNow();
    }
}
