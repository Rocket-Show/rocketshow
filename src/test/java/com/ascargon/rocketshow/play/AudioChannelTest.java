package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.audio.ActivityNotificationAudioService;
import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.AudioService;
import com.ascargon.rocketshow.midi.ActivityNotificationMidiService;
import com.ascargon.rocketshow.midi.MidiRouterFactory;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.video.HdmiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which output channels of an audio device the channels of a file play on. A mistake sends e.g. the click
 * to the audience.
 */
class AudioChannelTest {

    private final Settings settings = new Settings();
    private CompositionPipelineBuilder compositionPipelineBuilder;

    private final AudioDevice mainDevice = audioDevice(1, "Device");
    private final AudioDevice interfaceDevice = audioDevice(2, "UMC1820");

    private static AudioDevice audioDevice(int id, String key) {
        AudioDevice audioDevice = new AudioDevice();
        audioDevice.setId(id);
        audioDevice.setKey(key);
        audioDevice.setName(key);
        return audioDevice;
    }

    private AudioBus addAudioBus(String name, AudioDevice audioDevice, int channels) {
        AudioBus audioBus = new AudioBus();
        audioBus.setName(name);
        audioBus.setAudioDevice(audioDevice);
        audioBus.setChannels(channels);
        settings.getAudioBusList().add(audioBus);
        return audioBus;
    }

    @BeforeEach
    void setUp() {
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        OperatingSystemInformation operatingSystemInformation = new OperatingSystemInformation();
        operatingSystemInformation.setType(OperatingSystemInformation.Type.LINUX);
        OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(operatingSystemInformation);

        compositionPipelineBuilder = new CompositionPipelineBuilder(settingsService, mock(CapabilitiesService.class), mock(ActivityNotificationAudioService.class),
                mock(ActivityNotificationMidiService.class), operatingSystemInformationService, mock(AudioService.class), mock(MidiRouterFactory.class), mock(HdmiService.class));
    }

    // The output channel each input channel of a file plays on, -1 for none
    private List<Integer> getOutputChannels(AudioBus audioBus, int deviceChannels, int fileChannels) {
        List<Integer> outputChannels = new ArrayList<>();

        for (int input = 0; input < fileChannels; input++) {
            int outputChannel = -1;

            for (int output = 0; output < deviceChannels; output++) {
                float volume = compositionPipelineBuilder.getChannelVolume(audioBus, output, input, 0.8f);

                if (volume != 0) {
                    assertEquals(0.8f, volume);
                    assertEquals(-1, outputChannel, "Input channel " + input + " plays on several outputs");
                    outputChannel = output;
                }
            }

            outputChannels.add(outputChannel);
        }

        return outputChannels;
    }

    @Test
    void playsAFileOnTheChannelsOfItsBus() {
        AudioBus click = addAudioBus("Click", interfaceDevice, 2);
        AudioBus backing = addAudioBus("Backing", interfaceDevice, 6);

        assertEquals(List.of(0, 1), getOutputChannels(click, 8, 2));
        assertEquals(List.of(2, 3, 4, 5, 6, 7), getOutputChannels(backing, 8, 6));
    }

    @Test
    void countsOnlyTheBusesOfTheSameDevice() {
        addAudioBus("Main", mainDevice, 2);
        AudioBus click = addAudioBus("Click", interfaceDevice, 2);
        AudioBus backing = addAudioBus("Backing", interfaceDevice, 2);

        assertEquals(List.of(0, 1), getOutputChannels(click, 4, 2));
        assertEquals(List.of(2, 3), getOutputChannels(backing, 4, 2));
    }

    @Test
    void dropsChannelsTheBusDoesNotHave() {
        AudioBus click = addAudioBus("Click", interfaceDevice, 2);
        addAudioBus("Backing", interfaceDevice, 2);

        // A file with more channels than its bus doesn't play on the next bus
        assertEquals(List.of(0, 1, -1, -1), getOutputChannels(click, 4, 4));
    }

    @Test
    void skipsBusesWithoutDevice() {
        addAudioBus("Unused", null, 2);
        AudioBus click = addAudioBus("Click", interfaceDevice, 2);
        AudioBus backing = addAudioBus("Backing", interfaceDevice, 2);

        assertEquals(List.of(2, 3), getOutputChannels(backing, 4, 2));
        assertEquals(List.of(0, 1), getOutputChannels(click, 4, 2));
    }

    @Test
    void tellsBusesWithTheSameNameApart() {
        AudioBus first = addAudioBus("Bus", interfaceDevice, 2);
        AudioBus second = addAudioBus("Bus", interfaceDevice, 2);
        AudioBus unnamed = addAudioBus(null, interfaceDevice, 2);

        assertEquals(List.of(0, 1), getOutputChannels(first, 6, 2));
        assertEquals(List.of(2, 3), getOutputChannels(second, 6, 2));
        assertEquals(List.of(4, 5), getOutputChannels(unnamed, 6, 2));
    }

}
