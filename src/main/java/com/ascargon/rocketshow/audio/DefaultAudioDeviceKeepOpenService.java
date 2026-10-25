package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.gstreamer.GstApi;
import com.ascargon.rocketshow.gstreamer.GstreamerInitService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.settings.AudioSettingsChangedEvent;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps all used audio devices open to avoid plops and cracks when audio starts playing
 */
@Service
public class DefaultAudioDeviceKeepOpenService implements AudioDeviceKeepOpenService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultAudioDeviceKeepOpenService.class);

    private final SettingsService settingsService;
    private final AudioService audioService;
    private final OperatingSystemInformationService operatingSystemInformationService;
    private final PlayerService playerService;

    // The pipelines keeping the devices open. Kept here, so they're not disposed after they're not used anymore.
    private final List<Pipeline> pipelines = new ArrayList<>();

    public DefaultAudioDeviceKeepOpenService(
            SettingsService settingsService,
            AudioService audioService,
            OperatingSystemInformationService operatingSystemInformationService,
            PlayerService playerService,

            // import to make sure, Gstreamer is initialized before we use it here
            GstreamerInitService gstreamerInitService
    ) {
        this.settingsService = settingsService;
        this.audioService = audioService;
        this.operatingSystemInformationService = operatingSystemInformationService;
        this.playerService = playerService;

        this.init();
    }

    public synchronized void init() {
        if (!OperatingSystemInformation.SubType.RASPBERRYOS.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            return;
        }

        // Just set the first sink to i
        boolean provideClock = true;

        for (AudioDevice audioDevice : audioService.getAudioDeviceInUseList(settingsService.getSettings())) {
            Pipeline pipeline = new Pipeline();

            Element audiotestsrc = ElementFactory.make("audiotestsrc", "audiotestsrc");
            audiotestsrc.set("wave", 4 /* silence */);
            pipeline.add(audiotestsrc);

            Element audioConvert = ElementFactory.make("audioconvert", "audioconvert");
            pipeline.add(audioConvert);

            Element audioResample = ElementFactory.make("audioresample", "audioresample");
            pipeline.add(audioResample);

            Element capsFilter = ElementFactory.make("capsfilter", "capsfilter");
            Caps caps = GstApi.GST_API.gst_caps_from_string("audio/x-raw,rate=" + settingsService.getSettings().getAudioRate());
            capsFilter.set("caps", caps);
            pipeline.add(capsFilter);

            BaseSink sink = audioService.getGstAudioSink(audioDevice, provideClock);
            pipeline.add(sink);

            audiotestsrc.link(audioConvert);
            audioConvert.link(audioResample);
            audioResample.link(capsFilter);
            capsFilter.link(sink);

            pipeline.play();
            pipelines.add(pipeline);

            logger.info("Keep audio device " + audioDevice.getKey() + " always open to avoid sound artifacts...");

            provideClock = false;
        }
    }

    private synchronized void close() {
        for (Pipeline pipeline : pipelines) {
            pipeline.stop();
            pipeline.dispose();
        }

        pipelines.clear();
    }

    // Keep the devices of the changed audio settings open instead, without a restart. ALSA numbers the devices shared
    // by several pipelines in the order of the settings, so all pipelines open with the old settings close first:
    // these and the default composition. A composition playing keeps its devices until it stops.
    @EventListener
    public synchronized void audioSettingsChanged(AudioSettingsChangedEvent event) {
        close();

        try {
            playerService.restartDefaultComposition();
        } catch (Exception e) {
            logger.error("Could not play the default composition again", e);
        }

        init();
    }

}
