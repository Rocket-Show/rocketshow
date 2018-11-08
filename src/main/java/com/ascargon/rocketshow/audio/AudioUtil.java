package com.ascargon.rocketshow.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

public class AudioUtil {

    private final static Logger logger = Logger.getLogger(AudioUtil.class);

    private static AudioDevice getAudioDeviceFromString(String line) {
        AudioDevice audioDevice = new AudioDevice();

        audioDevice.setId(Integer.parseInt(line.substring(0, 3).trim()));
        audioDevice.setKey(line.substring(4, 19).trim());
        audioDevice.setName(line.substring(21).trim());

        return audioDevice;
    }

    public static List<AudioDevice> getAudioDevices() {
        List<AudioDevice> audioDeviceList = new ArrayList<>();

        logger.debug("List audio devices...");

        try {
            Process process = new ProcessBuilder("cat", "/proc/asound/cards").start();

            Thread readerThread = new Thread(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                boolean readLine = true;

                try {
                    while ((line = reader.readLine()) != null) {
                        logger.trace("Output from audio device list process: " + line);

                        // Only read the uneven lines. The even ones contain
                        // unneccessary information.
                        if (readLine) {
                            readLine = false;

                            AudioDevice audioDevice = getAudioDeviceFromString(line);

                            if (audioDevice.getName() != null && audioDevice.getName().length() > 0
                                    && !audioDevice.getKey().equals("ALSA")) {

                                audioDeviceList.add(audioDevice);
                            }
                        } else {
                            readLine = true;
                        }
                    }
                } catch (IOException e) {
                    logger.error("Could not read audio device list output", e);
                }
            });

            readerThread.start();

            try {
                readerThread.join();
            } catch (InterruptedException e) {
                logger.error("Could not wait for the list of audio devices", e);
            }
        } catch (IOException e) {
            logger.error("Could not list the audio devices", e);
        }

        logger.debug("Audio devices listed");

        return audioDeviceList;
    }

}
