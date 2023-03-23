# Rocket Show
A system to automate and play shows including audio, video, lighting (e.g. DMX) and MIDI.

## Development
### Build
Warning: Delete `node_modules/@angular-devkit/build-optimizer/src/.cache` after each NPM package update to make sure, the devkit is not caching an old version (see https://github.com/angular/devkit/issues/913).

1. Build the Java JAR: `mvn install`
2. Start the backend server from target directory: `java -jar rocketshow.jar`
3. Open the web app on http://localhost:8080

For frequent builds, you might want to comment out the frontend-maven-plugin in the POM and make use of the Maven parameter `-DskipTests`.

To debug Gstreamer issues, export GST_DEBUG before starting the server:
```shell
export GST_DEBUG=3
```

Pipelines can be tested using gst-launch-1.0. E.g.:
```shell
gst-launch-1.0 videotestsrc ! videoconvert ! autovideosink
gst-launch-1.0 uridecodebin uri=file:///opt/rocketshow/media/video/clouds.mp4 ! queue ! kmssink
```

While developing the web app, it might be convenient to start an Angular server:
1. Start web frontend server: `cd src/main/webapp && npm install && npx ng serve`
2. Open the web application: http://localhost:4200

On the Mac, Gstreamer and OLA can be installed using Homebrew:
```shell
brew install gstreamer
brew install gst-plugins-base
brew install gst-plugins-good
brew install gst-plugins-bad
brew install gst-plugins-ugly
brew install ola
```

A few testpipelines for Mac:
```shell
gst-launch-1.0 videotestsrc ! videoconvert ! osxvideosink
gst-launch-1.0 uridecodebin uri=file:///opt/rocketshow/media/video/clouds.mp4 ! queue ! osxaudiosink
```

Launch the OLA daemon on Mac:
```shell
olad
```

Launch Rocket Show on the mac:
```shell
./start.sh
```

## Deployment
### Seed directory
The seed directory structure '/dist/rocketshow' can be packed on a mac with this commands (assuming you're currently in the 'dist' directory):
```shell
COPYFILE_DISABLE=true tar -c --exclude='.DS_Store' -zf directory.tar.gz rocketshow
```

### Raspberry Pi Image building
Building is recommended on a Raspberry Pi device with enough storage. Steps to follow:
- Flash an SD card with Raspberry Pi OS
- Unmount and mount the SD card again
- Run the following script
```shell
cd /Volumes/boot
touch ssh
cat <<'EOF' >./userconf.txt
pi:FHzhxyxnV/C1o
EOF
```

Automatically connect to a wifi network (update the country code, SSID and PSK):
```shell
cd /Volumes/boot
cat <<'EOF' >./wpa_supplicant.conf
country=US
ctrl_interface=DIR=/var/run/wpa_supplicant GROUP=netdev
network={
    ssid="Your SSID"
    psk="Your Password"
    key_mgmt=WPA-PSK
}
EOF
```
- Login using `ssh pi@raspberrypi.local` and password `raspberry`
- Prepare the environment according to [https://github.com/RPi-distro/pi-gen] (e.g. install the required dependencies)
- Run the following script (might take about 45 minutes)
```shell
sudo su - root
cd /opt
mkdir build
cd build

git clone https://github.com/RPi-distro/pi-gen.git
cd pi-gen
git checkout tags/2022-09-22-raspios-bullseye

echo "IMG_NAME='RocketShow'" > config

touch ./stage3/SKIP ./stage4/SKIP ./stage5/SKIP
rm stage4/EXPORT* stage5/EXPORT*

# Disable noobs build
rm stage2/EXPORT_NOOBS

# Enhance stage2 with rocketshow
mkdir ./stage2/99-rocket-show

cat <<'EOF' >./stage2/99-rocket-show/00-run-chroot.sh
#!/bin/bash
#
cd /tmp
wget https://rocketshow.net/install/script/raspbian.sh
chmod +x raspbian.sh
./raspbian.sh
rm -rf raspbian.sh
EOF

chmod +x ./stage2/99-rocket-show/00-run-chroot.sh

apt-get update

./build.sh

# rename and zip the image
cd work/RocketShow/export-image

mv "$(date '+%Y-%m-%d')-RocketShow-lite.img" "$(date '+%Y-%m-%d')-RocketShow.img"
zip "$(date '+%Y-%m-%d')-RocketShow.zip" "$(date '+%Y-%m-%d')-RocketShow.img"

# copy the zip to a folder where we can get it with SFTP:
mv "$(date '+%Y-%m-%d')-RocketShow.zip" /home/pi
```


### Update process
- Add the release notes in dist/currentversion2.xml and update the date on top
- Build the jar with Maven
- Copy target/rocketshow.jar to rocketshow.net/update/rocketshow.jar
- Copy dist/currentversion2.xml to rocketshow.net/update/currentversion2.xml
- GIT merge DEV branch to MASTER
- GIT tag with the current version
- Switch to DEV and update POM and dist/currentversion2.xml versions

#### Optional
- Copy install/*.sh scripts to rocketshow.net/install/script/*.sh, if updated
- Copy dist/before.sh, dist/after.sh to rocketshow.net/update/xy.sh, if updated
- Copy the new complete image to rocketshow.net/install/images and change the file latest.php to link the new version
- Copy seed directory directory.tar.gz to rocketshow.net/install, if updated

### Application
The built application should be uploaded to rocketshow.net/update and be named "rocketshow.jar". The file "currentversion2.xml" can be modified accordingly.

## Code structure

### Server

#### Overview

The Rocket Show server is written in Java and uses Spring Boot ([https://spring.io/projects/spring-boot](https://spring.io/projects/spring-boot)). Gstreamer ([https://gstreamer.freedesktop.org/](https://gstreamer.freedesktop.org/)), a framework written in C, is included as multimedia playback backend.

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

The sources of the inluded jar file with the OLA client are copied from an archive into ola-java-client-src.

##### MIDI

MIDI input, output routing and mapping.

##### Raspberry

Raspberry Pi specific services (e.g. GPIO triggers).

##### Util

Various utilities used across the project.

### Web app

TODO