package com.ascargon.rocketshow;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.image.ImageDisplayingService;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.lighting.designer.FixtureService;
import com.ascargon.rocketshow.midi.MidiDeviceInService;
import com.ascargon.rocketshow.midi.MidiDeviceOutService;
import com.ascargon.rocketshow.raspberry.RaspberryGpioControlActionExecutionService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

@SpringBootApplication
public class RocketShowApplication {

    private static String[] args;
    private static ConfigurableApplicationContext context;

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(RocketShowApplication.class, args);

        // Initialize the notification service
        context.getBean(NotificationService.class);

        // Load the image displayer service to initially display a black screen, if required
        context.getBean(ImageDisplayingService.class);

        // Initialize the Raspberry GPIO listener
        context.getBean(RaspberryGpioControlActionExecutionService.class);

        // Initialize the player to start the default composition, if required
        context.getBean(PlayerService.class);

        // Connect to the MIDI in device, if available
        context.getBean(MidiDeviceInService.class);

        // Connect to the MIDI out device, if available
        context.getBean(MidiDeviceOutService.class);

        // Start the designer preview, if necessary
        context.getBean(DesignerService.class);

        // Update the fixture profiles, if possible
        FixtureService fixtureService = context.getBean(FixtureService.class);
        try {
            fixtureService.updateProfiles();
        } catch (IOException e) {
            // do nothing. maybe we're not connected to the internet. the user
            // is able to update the profiles manually from the webapp.
        }

        RocketShowApplication.args = args;
        RocketShowApplication.context = context;
    }

    public static void restart() {
        // Close the previous context
        context.close();

        // Build a new context
        RocketShowApplication.context = SpringApplication.run(RocketShowApplication.class, args);
    }

}
