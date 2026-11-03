package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.audio.ActivityNotificationAudioService;
import com.ascargon.rocketshow.audio.AudioService;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.gstreamer.TestGstreamer;
import com.ascargon.rocketshow.midi.ActivityNotificationMidiService;
import com.ascargon.rocketshow.midi.MidiRouterFactory;
import com.ascargon.rocketshow.settings.Capabilities;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.video.HdmiService;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.ascargon.rocketshow.play.GstreamerTestUnit.composition;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Whether the display switches to the mode of each video before it plays (force-modesetting of kmssink), as set.
 * The pipeline is only built, not played: there is no display. Needs kmssink of GStreamer's bad plugins (installed in
 * CI), skipped without.
 */
class VideoDisplayModeTest {

    @TempDir
    Path directory;

    private SettingsService settingsService;
    private CompositionPipelineBuilder compositionPipelineBuilder;

    @BeforeAll
    static void initGstreamer() {
        TestGstreamer.assumeInstalled("uridecodebin", "queue", "videoconvert", "kmssink");
    }

    @BeforeEach
    void setUp() throws Exception {
        settingsService = TestSettings.inDirectory(directory);

        Capabilities capabilities = new Capabilities();
        capabilities.setGstreamer(true);
        CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
        when(capabilitiesService.getCapabilities()).thenReturn(capabilities);

        // A Raspberry Pi 4 or 5, which shows the videos on kmssink
        OperatingSystemInformation operatingSystemInformation = new OperatingSystemInformation();
        operatingSystemInformation.setType(OperatingSystemInformation.Type.LINUX);
        operatingSystemInformation.setSubType(OperatingSystemInformation.SubType.RASPBERRYOS);
        OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(operatingSystemInformation);

        HdmiService hdmiService = mock(HdmiService.class);
        when(hdmiService.isConnected()).thenReturn(true);

        compositionPipelineBuilder = new CompositionPipelineBuilder(settingsService, capabilitiesService,
                mock(ActivityNotificationAudioService.class), mock(ActivityNotificationMidiService.class),
                operatingSystemInformationService, mock(AudioService.class), mock(MidiRouterFactory.class), hdmiService);
    }

    // The force-modesetting of the sink the video of a composition plays on
    private boolean forcesModesetting() throws Exception {
        VideoCompositionFile video = new VideoCompositionFile();
        video.setName("clip.mp4");
        video.setDurationMillis(1000);
        Composition composition = composition(1000, video);

        CompositionPipeline compositionPipeline = compositionPipelineBuilder.build(composition, 0, false,
                mock(CompositionPipelineBuilder.MasterEventListener.class), Runnable::run);

        try {
            Element sink = compositionPipeline.getMasterPipeline().getElementByName("kmssink");
            assertNotNull(sink, "The video plays on kmssink");
            return (Boolean) sink.get("force-modesetting");
        } finally {
            compositionPipeline.stop();
        }
    }

    @Test
    void showsTheVideoInTheModeOfTheDisplayByDefault() throws Exception {
        assertEquals(false, forcesModesetting());
    }

    @Test
    void switchesTheDisplayToTheModeOfTheVideoIfSet() throws Exception {
        settingsService.getSettings().setVideoForceModesetting(true);

        assertEquals(true, forcesModesetting());
    }

    @Test
    void leavesSinksWithoutModesAsTheyAre() {
        // E.g. the sink of the Mac, or the one of the tests
        settingsService.getSettings().setVideoForceModesetting(true);
        Element sink = ElementFactory.make("fakesink", "videosink");

        assertDoesNotThrow(() -> compositionPipelineBuilder.setDisplayMode(sink));
    }

}
