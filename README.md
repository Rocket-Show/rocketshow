# Rocket Show
An app to automate and play shows including audio, video, lighting (e.g. DMX) and MIDI.

## Development
### Build
1. Build the Java JAR: `mvn install`
2. Start the backend server from target directory: `java -jar rocketshow.jar`
3. Open the web app on http://localhost:8080

For frequent builds, you might want to comment out the frontend-maven-plugin in the POM and make use of the Maven parameter `-DskipTests`.

To debug Gstreamer issues, export GST_DEBUG before starting the server:
```shell
export GST_DEBUG=3
```

While developing the web app, it might be convenient to start an Angular server:
1. Start web frontend server: `cd src/main/webapp && npx ng serve`
2. Open the web application: http://localhost:4200

## Deployment
### Seed directory
The seed directory structure '/dist/rocketshow' can be packed on a mac with this commands (assuming you're currently in the 'dist' directory):
```shell
COPYFILE_DISABLE=true tar -c --exclude='.DS_Store' -zf directory.tar.gz rocketshow
```

### Raspberry Pi Image building
These steps describe how to build a Raspberry Pi image based on the DietPi distribution on a Mac OS X. Raspbian Light is not supported, because audio playback is laggy.

1. Flash an image with DietPi 6.17 ARMv6-Stretch to an SD card.
2. Remove the card from the Mac and add it again.
3. There should now be a directory /Volumes/boot available
4. Execute the shell script dist/install/prepare_dietpi_raspberry_image.sh
5. This script prepared the configuration for Rocket Show.
6. Safely remove the SD card and use a Raspberry Pi *connected to the internet* to boot it. According to DietPi, unfortunately there is currently no possibility to build the image without a Raspberry Pi.
7. Let the Raspberry Pi finish its boot process and install all required software.
8. SSH into it (username = root, password = dietpi) and run the following code:
```
cd /tmp
wget https://rocketshow.net/install/script/dietpi_raspberry.sh
chmod +x dietpi_raspberry.sh
./dietpi_raspberry.sh
rm -rf dietpi_raspberry.sh
sudo reboot
```
9. Let the system start itself a first time, login again with ssh and shut down using ```shutdown -h now```
10. Add the SD card back to the Mac.
11. Find its drive name with ```diskutil list```.
12. Unmount the disk. E.g. ```diskutil umountDisk /dev/disk2```.
13 Create an image of the card. E.g. ```sudo dd if=/dev/disk2 of=/Users/vio/sdcard.img bs=512```.
14. Transfer the image to a Linux (e.g. VirtualBox), because gparted is needed for the next steps.
15. Use https://raw.githubusercontent.com/Drewsif/PiShrink/master/pishrink.sh to shrink the image.
16. Zip the image using ```gzip -9 rocketshow.img```.

### Update process
- Add the release notes in update/currentversion2.xml and build the war ("mvn install")
- Copy seed directory directory.tar.gz to rocketshow.net/install, if updated
- Copy target/rocketshow.jar to rocketshow.net/update/rocketshow.jar
- Copy install/*.sh scripts to rocketshow.net/install/script/*.sh, if updated
- Copy update/currentversion2.xml to rocketshow.net/update/currentversion2.xml
- Copy update/before.sh, update/after.sh to rocketshow.net/update/xy.sh, if updated
- Copy the new complete image to rocketshow.net/install/images and change the file latest.php to link the new version
- GIT merge DEV branch to MASTER
- GIT tag with the current version
- Switch to DEV and update POM and update/currentversion2.xml versions

### Application
The built application should be uploaded to rocketshow.net/update and be named "rocketshow.jar". The file "currentversion2.xml" can be modified accordingly.

## Code structure

### Server

#### Overview

The Rocket Show server is written in Java and uses Spring Boot ([https://spring.io/projects/spring-boot](https://spring.io/projects/spring-boot))  for easier configuration and dependency injection. Gstreamer ([https://gstreamer.freedesktop.org/](https://gstreamer.freedesktop.org/)), a framework written in C, is included as multimedia playback backend.

Spring services are autowired into each other (dependency injection). The interface is named XyService, the corresponding implementation is called DefaultXyService.

The class RocketShowApplication serves as the application entry point. Some beans are initialized in the correct order.

There is a player service, which organises all composition players (responsible for the playback of a single composition). Parallel playbacks are possible as well.

The code is structured in different modules, which are described in more details below.

##### Base functionalities

A few base services and models lie in the root folder (e.g. settings, session-handling).

##### Api

This module is responsible for the communication with the web app and with other Rocket Show devices. A couple of REST interfaces are exposed as well as some web sockets for time critical topics or where server push is required.

##### Audio

Services related to audio playback.

##### Composition

Handling the composition and the composition player.

##### Gstreamer

Rocket Show specific calls to the native Gstreamer C api.

##### Image

Handling the image displaying.

##### Lighting

Responsible for the connection of Rocket Show to the Open Lighting Architecture to control connected lighting interfaces. Services for designer project playback also lies here.

##### MIDI

MIDI input, output routing and mapping.

##### Raspberry

Raspberry Pi specific services (e.g. GPIO triggers).

##### Util

Various utilities used across the project.

### Web app

TODO