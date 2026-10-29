package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.StateChangeReturn;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultAudioService implements AudioService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultAudioService.class);

    // The channels in the caps of a sound card, e.g. "channels=(int)[ 1, 8 ]" or "channels=(int){ 2, 6 }"
    private static final Pattern CHANNELS_PATTERN = Pattern.compile("channels=\\(int\\)(\\[[^]]*]|\\{[^}]*}|\\d+)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final OperatingSystemInformationService operatingSystemInformationService;

    public DefaultAudioService(OperatingSystemInformationService operatingSystemInformationService) {
        this.operatingSystemInformationService = operatingSystemInformationService;
    }

    private static AudioDevice getAudioDeviceFromString(String line) {
        AudioDevice audioDevice = new AudioDevice();

        audioDevice.setId(Integer.parseInt(line.substring(0, 3).trim()));
        audioDevice.setKey(line.substring(4, 19).trim());
        audioDevice.setName(line.substring(21).trim());

        return audioDevice;
    }

    @Override
    public List<AudioDevice> getAudioDevices() {
        logger.debug("List audio devices...");

        try (BufferedReader reader = Files.newBufferedReader(Path.of("/proc/asound/cards"))) {
            List<AudioDevice> audioDeviceList = readAudioDevices(reader);
            logger.debug("Audio devices listed");
            return audioDeviceList;
        } catch (IOException e) {
            logger.error("Could not list audio devices", e);
        }

        return new ArrayList<>();
    }

    // Read the sound cards of ALSA (/proc/asound/cards), without the ones built into the Raspberry Pi. Package visible
    // for the tests.
    static List<AudioDevice> readAudioDevices(BufferedReader reader) throws IOException {
        List<AudioDevice> audioDeviceList = new ArrayList<>();
        String line;
        int lineIndex = 0;

        while ((line = reader.readLine()) != null) {
            logger.trace("Output from audio device list file: {}", line);

            if (lineIndex % 2 == 0 && !line.startsWith("---")) {
                AudioDevice audioDevice = getAudioDeviceFromString(line);

                if (audioDevice.getName() != null
                        && !audioDevice.getName().isEmpty()
                        && !"ALSA".equals(audioDevice.getKey())
                        && !"Headphones".equals(audioDevice.getKey())
                        && !"vc4hdmi0".equals(audioDevice.getKey())
                        && !"vc4hdmi1".equals(audioDevice.getKey())
                ) {
                    audioDeviceList.add(audioDevice);
                }
            }

            lineIndex++;
        }

        return audioDeviceList;
    }

    @Override
    public int getMaxAvailableSinkChannels() {
        // Build a test Gstreamer pipeline and query the channel count from the sink
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicInteger maxChannels = new AtomicInteger(9999);

        // TODO Is a little dangerous. If there are too many channels specified in alsa,
        // audio settings cannot be opened anymore, because even this pipeline here crashes.
        // Possible solution: Test different pipelines and increase channel count from 1 to n,
        // stop, as soon as the pipeline does not play anymore. However, the last value for the
        // specified audio interface should be saved.
//        final Pipeline pipeline = new Pipeline();
//
//        Element audioTestSrc = ElementFactory.make("audiotestsrc", "audiotestsrc");
//        audioTestSrc.set("volume", 0d);
//        pipeline.add(audioTestSrc);
//
//        Element audioConvert = ElementFactory.make("audioconvert", "audioconvert");
//        pipeline.add(audioConvert);
//
//        // Add a capsfilter to enforce multi-channel out. Otherwise only 2 will be mixed
//        Element capsFilter = ElementFactory.make("capsfilter", "capsfilter");
//        Caps caps = GstApi.GST_API.gst_caps_from_string("audio/x-raw,channels=8");
//        capsFilter.set("caps", caps);
//        pipeline.add(capsFilter);
//
//        final BaseSink sink = getGstAudioSink();
//        pipeline.add(sink);
//
//        audioTestSrc.link(audioConvert);
//        audioConvert.link(capsFilter);
//        capsFilter.link(sink);
//
//        Bus bus = GstApi.GST_API.gst_element_get_bus(pipeline);
//        bus.connect((GstObject source, State old, State newState, State pending) -> {
//            if (source.getTypeName().equals("GstPipeline")) {
//                if (newState == State.PLAYING) {
//                    maxChannels.getAndSet(sink.getSinkPads().get(0).getNegotiatedCaps().getStructure(0).getInteger("channels"));
//
//                    pipeline.stop();
//                    pipeline.dispose();
//
//                    countDownLatch.countDown();
//                }
//            }
//        });
//        bus.connect((Bus.ERROR) (GstObject source, int code, String message) -> {
//            pipeline.stop();
//            pipeline.dispose();
//            logger.error("GST: " + message);
//            countDownLatch.countDown();
//        });
//        GstApi.GST_API.gst_object_unref(bus);
//
//        pipeline.play();
//
//        try {
//            // Add a timeout, because the selected audio interface might not work
//            countDownLatch.await(2, TimeUnit.SECONDS);
//        } catch (InterruptedException e) {
//            logger.error("Error while evaluating max available channels for the selected audio interface", e);
//        }

        return maxChannels.get();
    }

    @Override
    public String getAudioDeviceAlsaName(AudioDevice audioDevice) {
        // The audio device key could contain spaces and, while quite uncommon, also
        // other special characters. we need to change it to get a name suitable for
        // ALSA devices used in .asoundrc.
        String formatted = audioDevice.getKey().replace(" ", "_");

        // Remove all special characters using regex
        formatted = formatted.replaceAll("[^a-zA-Z0-9_]", "");

        return formatted;
    }

    @Override
    public BaseSink getGstAudioSink(AudioDevice audioDevice, boolean provideClock) {
        String sinkName = "alsasink";

        if (OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            logger.debug("Create audio sink for OS X...");
            sinkName = "osxaudiosink";
        }

        BaseSink sink = (BaseSink) ElementFactory.make(sinkName, "audiosink_" + getAudioDeviceAlsaName(audioDevice));

        if (!OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            sink.set("device", "rs_" + getAudioDeviceAlsaName(audioDevice));

            // Should be set by default but make sure it's really n'sync
            sink.set("sync", true);

            sink.set("provide-clock", provideClock);
        }

        return sink;
    }

    @Override
    public int getChannelCountByAudioDevice(Settings settings, AudioDevice audioDevice) {
        int count = 0;

        if (OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            return 2;
        }

        for (AudioBus audioBus : settings.getAudioBusList()) {
            // A bus without device doesn't play
            if (audioBus.getAudioDevice() != null && audioBus.getAudioDevice().getId() == audioDevice.getId()) {
                count += audioBus.getChannels();
            }
        }

        return count;
    }

    @Override
    public List<AudioDevice> getAudioDeviceInUseList(Settings settings) {
        // Get all uniquely used audio devices
        Set<AudioDevice> audioDeviceSet = new HashSet<>();
        for (AudioBus audioBus : settings.getAudioBusList()) {
            if (audioBus.getAudioDevice() != null) {
                audioDeviceSet.add(audioBus.getAudioDevice());
            }
        }
        // Always in the same order, e.g. to write the same .asoundrc for the same settings
        return audioDeviceSet.stream().sorted(Comparator.comparingInt(AudioDevice::getId)).toList();
    }

    private static String getHardwareDeviceName(AudioDevice audioDevice) {
        return "hw:CARD=" + audioDevice.getKey();
    }

    @Override
    public BaseSink getGstHardwareAudioSink(AudioDevice audioDevice) {
        BaseSink sink = (BaseSink) ElementFactory.make("alsasink", "hardwaresink_" + getAudioDeviceAlsaName(audioDevice));
        sink.set("device", getHardwareDeviceName(audioDevice));
        sink.set("sync", true);
        return sink;
    }

    @Override
    public Element getGstHardwareAudioSource(AudioDevice audioDevice) {
        Element source = ElementFactory.make("alsasrc", "hardwaresource_" + getAudioDeviceAlsaName(audioDevice));
        source.set("device", getHardwareDeviceName(audioDevice));
        return source;
    }

    @Override
    public int getHardwareChannelCount(AudioDevice audioDevice, boolean capture) {
        Element element = capture ? getGstHardwareAudioSource(audioDevice) : getGstHardwareAudioSink(audioDevice);

        try {
            // Ready opens the sound card, which then tells its formats
            if (element.setState(State.READY) == StateChangeReturn.FAILURE) {
                return 0;
            }

            Caps caps = element.getStaticPad(capture ? "src" : "sink").queryCaps(null);
            return getMaxChannels(caps.toString());
        } catch (Exception e) {
            logger.warn("Could not read the channels of the sound card " + audioDevice.getKey(), e);
            return 0;
        } finally {
            element.setState(State.NULL);
            element.dispose();
        }
    }

    // The most channels in caps. Package visible for the tests.
    static int getMaxChannels(String caps) {
        int maxChannels = 0;
        Matcher matcher = CHANNELS_PATTERN.matcher(caps);

        while (matcher.find()) {
            Matcher numbers = NUMBER_PATTERN.matcher(matcher.group(1));
            while (numbers.find()) {
                maxChannels = Math.max(maxChannels, Integer.parseInt(numbers.group()));
            }
        }

        return maxChannels;
    }

}
