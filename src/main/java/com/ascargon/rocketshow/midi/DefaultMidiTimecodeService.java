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
    private Object owner;
    private int quarterFrameMessageType = 0;

    public DefaultMidiTimecodeService(SettingsService settingsService, MidiDeviceOutService midiDeviceOutService) {
        this.settingsService = settingsService;
        this.midiDeviceOutService = midiDeviceOutService;
    }

    @Override
    public synchronized void start(Object owner, LongSupplier positionMillisSupplier) {
        Settings settings = settingsService.getSettings();
        if (!Boolean.TRUE.equals(settings.getMidiTimecodeEnabled())) {
            return;
        }

        stopCurrent();

        this.owner = owner;
        quarterFrameMessageType = 0;
        MidiTimecodeFrameRate frameRate = settings.getMidiTimecodeFrameRate();

        try {
            midiDeviceOutService.sendMessage(MidiTimecodeMessageFactory.createFullFrameMessage(positionMillisSupplier.getAsLong(), frameRate));
        } catch (InvalidMidiDataException e) {
            logger.error("Could not send MIDI timecode full-frame message", e);
        }

        long periodNanos = Math.round(1_000_000_000.0 / (frameRate.getFramesPerSecond() * 4.0));
        sendTimecodeHandle = scheduler.scheduleAtFixedRate(() -> sendQuarterFrame(positionMillisSupplier, frameRate), 0, periodNanos, TimeUnit.NANOSECONDS);
    }

    private void sendQuarterFrame(LongSupplier positionMillisSupplier, MidiTimecodeFrameRate frameRate) {
        Settings settings = settingsService.getSettings();
        if (!Boolean.TRUE.equals(settings.getMidiTimecodeEnabled()) || !midiDeviceOutService.isConnected()) {
            return;
        }

        try {
            midiDeviceOutService.sendMessage(MidiTimecodeMessageFactory.createQuarterFrameMessage(positionMillisSupplier.getAsLong(), frameRate, quarterFrameMessageType));
            quarterFrameMessageType = (quarterFrameMessageType + 1) % 8;
        } catch (InvalidMidiDataException e) {
            logger.error("Could not send MIDI timecode quarter-frame message", e);
        }
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
        owner = null;
    }

    @PreDestroy
    private void close() {
        stopCurrent();
        scheduler.shutdownNow();
    }
}
