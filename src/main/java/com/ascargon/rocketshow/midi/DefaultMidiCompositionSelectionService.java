package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.Set;
import com.ascargon.rocketshow.composition.SetComposition;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DefaultMidiCompositionSelectionService implements MidiCompositionSelectionService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultMidiCompositionSelectionService.class);

    private final static int SONG_SELECT_STATUS = 0xF3;
    private final static int BANK_SELECT_MSB_CONTROLLER = 0;
    private final static int BANK_SELECT_LSB_CONTROLLER = 32;
    private final static int PROGRAMS_PER_BANK = 128;
    private final static int CHANNEL_COUNT = 16;

    private final SettingsService settingsService;
    private final CompositionService compositionService;
    private final SetService setService;

    // Lazy to avoid a circular dependency: the player service is built on top of the MIDI services.
    private final PlayerService playerService;

    // Bank select is latched per channel until the program change that uses it arrives
    private final int[] bankMsb = new int[CHANNEL_COUNT];
    private final int[] bankLsb = new int[CHANNEL_COUNT];

    // Selecting loads and prerolls a composition, which must not block the MIDI input thread
    private final ExecutorService selectExecutor = Executors.newSingleThreadExecutor();

    public DefaultMidiCompositionSelectionService(SettingsService settingsService, CompositionService compositionService,
                                                  SetService setService, @Lazy PlayerService playerService) {
        this.settingsService = settingsService;
        this.compositionService = compositionService;
        this.setService = setService;
        this.playerService = playerService;
    }

    @Override
    public void processMidiMessage(MidiMessage midiMessage) {
        Settings settings = settingsService.getSettings();

        if (!Boolean.TRUE.equals(settings.getMidiCompositionSelectionEnabled())) {
            return;
        }

        if (!(midiMessage instanceof ShortMessage shortMessage)) {
            return;
        }

        if (shortMessage.getStatus() == SONG_SELECT_STATUS) {
            // Song select carries no channel and no bank; the song number is the MIDI number
            selectByMidiNumber(shortMessage.getData1() + 1, autoPlay(settings));
            return;
        }

        if (shortMessage.getCommand() == ShortMessage.CONTROL_CHANGE) {
            latchBankSelect(shortMessage);
            return;
        }

        if (shortMessage.getCommand() != ShortMessage.PROGRAM_CHANGE) {
            return;
        }

        Integer channel = settings.getMidiCompositionSelectionChannel();

        if (channel != null && channel != shortMessage.getChannel()) {
            return;
        }

        selectByMidiNumber(getMidiNumber(shortMessage), autoPlay(settings));
    }

    private static boolean autoPlay(Settings settings) {
        return Boolean.TRUE.equals(settings.getMidiCompositionSelectionAutoPlay());
    }

    private void latchBankSelect(ShortMessage shortMessage) {
        int channel = shortMessage.getChannel();

        if (shortMessage.getData1() == BANK_SELECT_MSB_CONTROLLER) {
            bankMsb[channel] = shortMessage.getData2();
        } else if (shortMessage.getData1() == BANK_SELECT_LSB_CONTROLLER) {
            bankLsb[channel] = shortMessage.getData2();
        }
    }

    /**
     * The MIDI number a program change addresses, counted from 1 so it matches the program numbers
     * shown on a master. Without any preceding bank select this is simply the program number plus
     * one; bank select extends the range beyond the 128 programs of a bank.
     */
    private int getMidiNumber(ShortMessage programChange) {
        int channel = programChange.getChannel();
        int bank = (bankMsb[channel] << 7) | bankLsb[channel];

        return bank * PROGRAMS_PER_BANK + programChange.getData1() + 1;
    }

    @Override
    public boolean selectByMidiNumber(int midiNumber, boolean play) {
        return select(new MidiNumberMatcher(midiNumber), String.valueOf(midiNumber), play);
    }

    @Override
    public boolean selectByShowControlCue(String cue, boolean play) {
        if (cue == null || cue.isBlank()) {
            return false;
        }

        return select(new ShowControlCueMatcher(cue.trim()), cue.trim(), play);
    }

    /**
     * Find the composition the master is addressing and select it.
     * <p>
     * The current set is searched first, so a set can renumber its compositions and every show can
     * count its songs from 1. Only if no set is loaded, or the set does not use the number at all,
     * is the number looked up across all compositions.
     */
    private boolean select(Matcher matcher, String addressedAs, boolean play) {
        Set currentSet = setService.getCurrentSet();

        if (currentSet != null) {
            List<SetComposition> setCompositionList = currentSet.getSetCompositionList();

            for (int index = 0; index < setCompositionList.size(); index++) {
                SetComposition setComposition = setCompositionList.get(index);

                if (matcher.matches(setComposition, compositionService.getComposition(setComposition.getName()))) {
                    submitSelect(setComposition.getName(), index, play);
                    return true;
                }
            }
        }

        for (Composition composition : compositionService.getAllCompositions()) {
            if (matcher.matches(null, composition)) {
                submitSelect(composition.getName(), -1, play);
                return true;
            }
        }

        logger.info("No composition is addressed by MIDI as '{}'", addressedAs);
        return false;
    }

    private void submitSelect(String compositionName, int setCompositionIndex, boolean play) {
        selectExecutor.execute(() -> {
            try {
                logger.info("Selecting composition '{}' from MIDI", compositionName);

                if (setCompositionIndex >= 0) {
                    setService.setCurrentCompositionIndex(setCompositionIndex);
                }

                // Don't fall back to the default composition in between when we play right away
                playerService.setComposition(compositionService.getComposition(compositionName), !play, false);

                if (play) {
                    playerService.play();
                }
            } catch (Exception e) {
                logger.error("Could not select composition '" + compositionName + "' from MIDI", e);
            }
        });
    }

    private interface Matcher {

        /**
         * @param setComposition the entry in the current set, or null when searching outside a set
         * @param composition    the composition itself, may be null if the set references a
         *                       composition that no longer exists
         */
        boolean matches(SetComposition setComposition, Composition composition);

    }

    /**
     * Matches the MIDI number, preferring the one the set overrides it with.
     */
    private record MidiNumberMatcher(int midiNumber) implements Matcher {

        @Override
        public boolean matches(SetComposition setComposition, Composition composition) {
            Integer number = setComposition == null ? null : setComposition.getMidiNumber();

            if (number == null) {
                number = composition == null ? null : composition.getMidiNumber();
            }

            return number != null && number == midiNumber;
        }

    }

    /**
     * Matches the show control cue number, preferring the one the set overrides it with, and falling
     * back to the MIDI number so compositions that are only numbered can be addressed by cue too.
     */
    private record ShowControlCueMatcher(String cue) implements Matcher {

        @Override
        public boolean matches(SetComposition setComposition, Composition composition) {
            String configuredCue = setComposition == null ? null : setComposition.getShowControlCue();

            if (configuredCue == null || configuredCue.isBlank()) {
                configuredCue = composition == null ? null : composition.getShowControlCue();
            }

            if (configuredCue != null && !configuredCue.isBlank()) {
                return matchesCueNumber(configuredCue.trim(), cue);
            }

            return new MidiNumberMatcher(parseCueNumber(cue)).matches(setComposition, composition);
        }

        /**
         * Cue numbers are text, but "1", "01" and "1.0" all address the same cue on most consoles,
         * so compare them as numbers whenever both sides are numeric.
         */
        private static boolean matchesCueNumber(String configuredCue, String cue) {
            if (configuredCue.equalsIgnoreCase(cue)) {
                return true;
            }

            try {
                return Double.parseDouble(configuredCue) == Double.parseDouble(cue);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // -1 never matches a MIDI number, which is counted from 1
        private static int parseCueNumber(String cue) {
            try {
                return Integer.parseInt(cue);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

    }

    @PreDestroy
    public void close() {
        selectExecutor.shutdownNow();
    }

}
