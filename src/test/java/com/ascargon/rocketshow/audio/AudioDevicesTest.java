package com.ascargon.rocketshow.audio;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The audio devices offered for the audio buses, read from the sound cards of ALSA (/proc/asound/cards).
 */
class AudioDevicesTest {

    private static List<String> devices(String cards) throws Exception {
        return DefaultAudioService.readAudioDevices(new BufferedReader(new StringReader(cards))).stream()
                .map(audioDevice -> audioDevice.getId() + " " + audioDevice.getKey() + ": " + audioDevice.getName())
                .toList();
    }

    @Test
    void offersTheAudioInterfacesButNotTheBuiltInOutputs() throws Exception {
        // A Raspberry Pi 4 with an audio interface by USB and a HAT
        String cards = """
                 0 [Headphones     ]: bcm2835_headpho - bcm2835 Headphones
                                      bcm2835 Headphones
                 1 [vc4hdmi0       ]: vc4-hdmi - vc4-hdmi-0
                                      vc4-hdmi-0
                 2 [vc4hdmi1       ]: vc4-hdmi - vc4-hdmi-1
                                      vc4-hdmi-1
                 3 [CODEC          ]: USB-Audio - USB Audio CODEC
                                      Burr-Brown from TI USB Audio CODEC at usb-0000:01:00.0-1.3, full speed
                 4 [sndrpihifiberry]: HifiberryDacp - snd_rpi_hifiberry_dacplus
                                      snd_rpi_hifiberry_dacplus
                """;

        assertEquals(List.of(
                "3 CODEC: USB-Audio - USB Audio CODEC",
                "4 sndrpihifiberry: HifiberryDacp - snd_rpi_hifiberry_dacplus"
        ), devices(cards));
    }

    @Test
    void readsCardsAfterTheTenth() throws Exception {
        String cards = """
                10 [Device         ]: USB-Audio - USB Audio Device
                                      C-Media Electronics Inc. USB Audio Device at usb-0000:01:00.0-1.2, full speed
                11 [Device_1       ]: USB-Audio - USB Audio Device
                                      C-Media Electronics Inc. USB Audio Device at usb-0000:01:00.0-1.4, full speed
                """;

        assertEquals(List.of(
                "10 Device: USB-Audio - USB Audio Device",
                "11 Device_1: USB-Audio - USB Audio Device"
        ), devices(cards));
    }

    @Test
    void offersNothingWithoutSoundCards() throws Exception {
        assertEquals(List.of(), devices("--- no soundcards ---\n"));
    }

    @Test
    void namesTheDevicesForAlsa() {
        AudioDevice audioDevice = new AudioDevice();
        audioDevice.setKey("Device 1 (USB)");

        assertEquals("Device_1_USB", new DefaultAudioService(null).getAudioDeviceAlsaName(audioDevice));
    }

    @Test
    void readsTheChannelsASoundCardOffers() {
        // E.g. of a new unit for its self-test, which has no audio settings
        assertEquals(2, DefaultAudioService.getMaxChannels("audio/x-raw, format=(string)S16LE, layout=(string)interleaved, rate=(int)[ 44100, 48000 ], channels=(int)2"));
        assertEquals(8, DefaultAudioService.getMaxChannels("audio/x-raw, format=(string)S32LE, rate=(int)48000, channels=(int)[ 1, 8 ]"));
        assertEquals(6, DefaultAudioService.getMaxChannels("audio/x-raw, channels=(int){ 2, 6 }; audio/x-raw, channels=(int)4, channel-mask=(bitmask)0x0000000000000033"));
        assertEquals(0, DefaultAudioService.getMaxChannels("EMPTY"));
    }

}
