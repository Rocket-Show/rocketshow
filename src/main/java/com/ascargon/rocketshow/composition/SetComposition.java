package com.ascargon.rocketshow.composition;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * A composition inside a set containing less information than a "real"
 * composition.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class SetComposition {

	private String name;
	private long durationMillis;
	private boolean autoStartNextComposition = false;

	// The absolute MIDI timecode position (in milliseconds) at which this composition starts within
	// the set, used when following an incoming MIDI timecode with the per-set mapping.
	private long timecodeStartMillis = 0;

	// Overrides the composition's own MIDI number within this set, so every set can number its
	// compositions from 1. Null to use the composition's own number.
	private Integer midiNumber;

	// Overrides the composition's own MIDI Show Control cue number within this set. Empty to use the
	// composition's own cue number.
	private String showControlCue;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(long durationMillis) {
		this.durationMillis = durationMillis;
	}

	public boolean isAutoStartNextComposition() {
		return autoStartNextComposition;
	}

	public void setAutoStartNextComposition(boolean autoStartNextComposition) {
		this.autoStartNextComposition = autoStartNextComposition;
	}

	public long getTimecodeStartMillis() {
		return timecodeStartMillis;
	}

	public void setTimecodeStartMillis(long timecodeStartMillis) {
		this.timecodeStartMillis = timecodeStartMillis;
	}

	public Integer getMidiNumber() {
		return midiNumber;
	}

	public void setMidiNumber(Integer midiNumber) {
		this.midiNumber = midiNumber;
	}

	public String getShowControlCue() {
		return showControlCue;
	}

	public void setShowControlCue(String showControlCue) {
		this.showControlCue = showControlCue;
	}

}
