package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.gstreamer.GstApi;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Keeps all used audio devices open to avoid plops and cracks when audio starts playing
 */
@Service
public class DefaultAudioDeviceKeepOpenService implements AudioDeviceKeepOpenService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultAudioDeviceKeepOpenService.class);

    private final SettingsService settingsService;
    private final AudioService audioService;
    private final OperatingSystemInformationService operatingSystemInformationService;

    public DefaultAudioDeviceKeepOpenService(
            SettingsService settingsService,
            AudioService audioService,
            OperatingSystemInformationService operatingSystemInformationService
    ) {
        this.settingsService = settingsService;
        this.audioService = audioService;
        this.operatingSystemInformationService = operatingSystemInformationService;

        this.init();
    }

    public void init() {
        if (!OperatingSystemInformation.SubType.RASPBERRYOS.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            return;
        }

        for (AudioDevice audioDevice : settingsService.getAudioDeviceInUseList()) {
            // Run in an infinite thread to not dispose the class/pipeline after it's not used anymore
            Thread infiniteThread = new Thread(() -> {
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

                BaseSink sink = audioService.getGstAudioSink(audioDevice);
                pipeline.add(sink);

                audiotestsrc.link(audioConvert);
                audioConvert.link(audioResample);
                audioResample.link(capsFilter);
                capsFilter.link(sink);

                pipeline.play();

                logger.info("Keep audio device " + audioDevice.getKey() + " always open to avoid sound artifacts...");

                while (true) {
                    // Perform any task or keep the thread idle
                    try {
                        Thread.sleep(60000); // Sleep for 1 minute to reduce CPU usage
                    } catch (InterruptedException e) {
                        // Handle the interruption if needed
                        break; // Exit the loop if the thread is interrupted
                    }
                }
            });
            infiniteThread.start();
        }
    }

}
