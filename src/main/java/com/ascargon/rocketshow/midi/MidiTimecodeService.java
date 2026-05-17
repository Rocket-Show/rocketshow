package com.ascargon.rocketshow.midi;

import java.util.function.LongSupplier;

public interface MidiTimecodeService {

    void start(Object owner, LongSupplier positionMillisSupplier);

    void stop(Object owner);
}
