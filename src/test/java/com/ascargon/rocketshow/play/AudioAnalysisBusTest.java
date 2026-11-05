package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.audio.ActivityNotificationAudioService;
import com.ascargon.rocketshow.audio.AudioAnalysisService;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which audio buses the pipeline of a composition analyzes for the pixel effects, and which channels of their device
 * it measures of each. A mistake lets e.g. the click drive the lights.
 */
class AudioAnalysisBusTest {

    private final Settings settings = new Settings();
    private final AudioAnalysisService audioAnalysisService = new AudioAnalysisService();
    private final OperatingSystemInformation operatingSystemInformation = new OperatingSystemInformation();
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

    private AudioBus addAudioBus(String uuid, AudioDevice audioDevice, int channels) {
        AudioBus audioBus = new AudioBus();
        audioBus.setUuid(uuid);
        audioBus.setName(uuid);
        audioBus.setAudioDevice(audioDevice);
        audioBus.setChannels(channels);
        settings.getAudioBusList().add(audioBus);
        return audioBus;
    }

    @BeforeEach
    void setUp() {
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        operatingSystemInformation.setType(OperatingSystemInformation.Type.LINUX);
        OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(operatingSystemInformation);

        compositionPipelineBuilder = new CompositionPipelineBuilder(settingsService, mock(CapabilitiesService.class), mock(ActivityNotificationAudioService.class),
                mock(ActivityNotificationMidiService.class), operatingSystemInformationService, mock(AudioService.class), mock(MidiRouterFactory.class),
                mock(HdmiService.class), audioAnalysisService);
    }

    @Test
    void analyzesTheBusesOfADeviceTheEffectsFollow() {
        AudioBus music = addAudioBus("music", interfaceDevice, 2);
        addAudioBus("click", interfaceDevice, 2);
        AudioBus drums = addAudioBus("drums", interfaceDevice, 2);
        AudioBus room = addAudioBus("room", mainDevice, 2);
        addAudioBus("silent", null, 2);

        assertEquals(List.of(), compositionPipelineBuilder.getAnalyzedBuses(interfaceDevice));

        audioAnalysisService.setRequiredBuses(List.of("music", "drums", "room", "silent"));

        assertEquals(List.of(music, drums), compositionPipelineBuilder.getAnalyzedBuses(interfaceDevice));
        assertEquals(List.of(room), compositionPipelineBuilder.getAnalyzedBuses(mainDevice));

        // on a Mac, every bus plays on the one device there is
        operatingSystemInformation.setType(OperatingSystemInformation.Type.OS_X);

        assertEquals(4, compositionPipelineBuilder.getAnalyzedBuses(mainDevice).size());
    }

    @Test
    void measuresTheChannelsOfABusAmongTheOnesOfItsDevice() {
        AudioBus music = addAudioBus("music", interfaceDevice, 2);
        AudioBus click = addAudioBus("click", interfaceDevice, 1);
        AudioBus wide = addAudioBus("wide", interfaceDevice, 4);

        assertArrayEquals(new float[][]{{1, 0, 0, 0, 0, 0}, {0, 1, 0, 0, 0, 0}}, compositionPipelineBuilder.getBusChannelMatrix(music, 6));
        assertArrayEquals(new float[][]{{0, 0, 1, 0, 0, 0}}, compositionPipelineBuilder.getBusChannelMatrix(click, 6));

        // as far as the device has channels for it
        assertArrayEquals(new float[][]{{0, 0, 0, 1, 0, 0}, {0, 0, 0, 0, 1, 0}, {0, 0, 0, 0, 0, 1}}, compositionPipelineBuilder.getBusChannelMatrix(wide, 6));
        assertEquals(0, compositionPipelineBuilder.getBusChannelMatrix(wide, 2).length);
    }

}
