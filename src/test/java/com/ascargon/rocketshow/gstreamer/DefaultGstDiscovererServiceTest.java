package com.ascargon.rocketshow.gstreamer;

import com.sun.jna.Pointer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading the duration and the audio channels of uploaded media, like when saving a composition. Needs GStreamer with
 * the base and good plugins (installed in CI), skipped without.
 */
class DefaultGstDiscovererServiceTest {

    @TempDir
    Path directory;

    private final DefaultGstDiscovererService discovererService = new DefaultGstDiscovererService();

    @BeforeAll
    static void initGstreamer() {
        TestGstreamer.assumeInstalled("wavparse", "videotestsrc", "jpegenc", "jpegdec", "avimux", "avidemux");
    }

    private Path wav(String name, int channels, int millis) throws Exception {
        int rate = 44100;
        int frames = rate * millis / 1000;
        AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
        Path file = directory.resolve(name);

        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(new byte[frames * channels * 2]), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file.toFile());
        }

        return file;
    }

    @Test
    void readsTheDurationAndTheChannelsOfAudio() throws Exception {
        Pointer stereo = discovererService.getDiscovererInformation(wav("stereo.wav", 2, 1500).toString());
        assertEquals(1500, discovererService.getDurationMillis(stereo));
        assertEquals(2, discovererService.getChannels(stereo));

        // E.g. the tracks of a band, played on several outputs
        Pointer multichannel = discovererService.getDiscovererInformation(wav("multichannel.wav", 6, 500).toString());
        assertEquals(500, discovererService.getDurationMillis(multichannel));
        assertEquals(6, discovererService.getChannels(multichannel));
    }

    @Test
    void readsTheDurationOfVideo() throws Exception {
        Path file = directory.resolve("clip.avi");
        Pipeline pipeline = (Pipeline) Gst.parseLaunch("videotestsrc num-buffers=50 ! video/x-raw,width=64,height=48,framerate=25/1 ! jpegenc ! avimux ! filesink location=" + file);
        CountDownLatch written = new CountDownLatch(1);
        pipeline.getBus().connect((Bus.EOS) source -> written.countDown());
        pipeline.play();
        assertTrue(written.await(10, TimeUnit.SECONDS));
        pipeline.stop();
        pipeline.dispose();

        Pointer video = discovererService.getDiscovererInformation(file.toString());
        assertEquals(2000, discovererService.getDurationMillis(video));
        assertEquals(0, discovererService.getChannels(video));
    }

    @Test
    void refusesAFileWhichIsNoMedia() throws Exception {
        Path file = directory.resolve("song.wav");
        Files.writeString(file, "this is no audio");

        assertThrows(Exception.class, () -> discovererService.getDiscovererInformation(file.toString()));
    }

}
