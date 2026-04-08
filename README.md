# Rocket Show

Rocket Show is a system to automate and play shows including audio, video, lighting (e.g. DMX) and MIDI on Raspberry Pi
devices.

Check our website: https://rocketshow.net

## Usage

Refer to [the docs](./docs/index.md) to find out how to use Rocket Show.

## Development

### Build

1. Build: `./mvnw clean package`
2. Start: `java -jar target/rocketshow.jar`
3. Open the web app on http://localhost:8080

Use this command to skip tests and NPM build:
```shell
./mvnw package -Dskip.npm -Dmaven.test.skip=true && java -jar target/rocketshow.jar
```

For frequent builds, you might want to comment out the frontend-maven-plugin in the POM and make use of the Maven
parameter `-DskipTests`.

### Local Angular frontend development

While developing the web app, it might be convenient to start an Angular server:

1. Add this entry to your hosts file (e.g. /etc/hosts on the Mac) to connect to a rocketshow.local server:
  `127.0.0.1 app.rocketshow.local`
2. Install npm packages (force required because some dependency conflicts): `cd src/main/webapp && npm install --force`
3. Start the server with a local backend `npx serve` or a device backend `ng serve --host app.rocketshow.local --port 4200`
4. Open the web application: http://localhost:4200

On the Mac, Gstreamer and OLA can be installed using Homebrew:

### Debugging

To debug Gstreamer issues, export GST_DEBUG before starting the server:

```shell
export GST_DEBUG=3
```

Pipelines can be tested using gst-launch-1.0. E.g.:

```shell
gst-launch-1.0 videotestsrc ! videoconvert ! autovideosink
gst-launch-1.0 uridecodebin uri=file:///opt/rocketshow/media/video/clouds.mp4 ! queue ! kmssink
gst-launch-1.0 uridecodebin uri=file:///opt/rocketshow/media/audio/head_smashed_far_away.wav ! audioconvert ! audioresample ! "audio/x-raw,rate=44100" ! alsasink device=rocketshow
```

To test audio playback with alsa:

```shell
aplay -D rocketshow /opt/rocketshow/media/audio/head_smashed_far_away.wav
```

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

### Build OLA

To build the OLA Client jar required by Rocket Show, follow these steps on a mac:

```
# install protobuf 21 (newer versions of protobuf don't work by OCT 2024, because of too old cpp versions used in dependencies, see https://github.com/protocolbuffers/protobuf/issues/12393)
brew install protobuf@21 autoconf automake libtool cppunit

echo 'export PATH="/opt/homebrew/opt/protobuf@21/bin:$PATH"' >> ~/.zshrc
export LDFLAGS="-L/opt/homebrew/opt/protobuf@21/lib"                
export CPPFLAGS="-I/opt/homebrew/opt/protobuf@21/include"
export PKG_CONFIG_PATH="/opt/homebrew/opt/protobuf@21/lib/pkgconfig"

autoreconf -i
./configure --enable-java-libs
make -j$(nproc)
```

## Deployment

### Seed directory

The defaults directory `/dist/defaults` can be packed on a mac with this commands:

```shell
cd dist
COPYFILE_DISABLE=true tar -c --exclude='.DS_Store' -zf defaults.tar.gz defaults
```

### Raspberry Pi Image building

Building is recommended on a Raspberry Pi device with enough storage. Steps to follow:

- Switch to user `root`:

````shell
sudo su - root
````

- Update apt:

````shell
apt-get update
````

- Prepare the environment according to [https://github.com/RPi-distro/pi-gen](pi-gen Readme) (e.g. install the required
  dependencies)

- Run the following script (might take about 45 minutes)

```shell
cd /opt
rm -rf build
mkdir build
cd build

git clone https://github.com/RPi-distro/pi-gen.git
cd pi-gen
git checkout tags/2025-12-04-raspios-trixie-arm64

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

./build.sh

# rename and zip the image
cd work/RocketShow/export-image

mv "$(date '+%Y-%m-%d')-RocketShow-lite.img" "$(date '+%Y-%m-%d')-RocketShow.img"
zip "$(date '+%Y-%m-%d')-RocketShow.zip" "$(date '+%Y-%m-%d')-RocketShow.img"

# copy the zip to a folder where we can get it with SFTP:
mv "$(date '+%Y-%m-%d')-RocketShow.zip" /home/rocketshow
```

### Update process

- Update POM
- Update dist/currentversion2.xml version/date on top and add the release notes
- Build the jar with Maven `./mvnw clean package`
- Copy target/rocketshow.jar to rocketshow.net/update/test/rocketshow.jar (and parent-directory to release it directly)
- Copy dist/currentversion2.xml to rocketshow.net/update/test/currentversion2.xml (and parent-directory to release it
  directly)
- GIT merge DEV branch to MASTER
- GIT tag with the current version
- Switch back to DEV

#### Optional

- Copy install/*.sh scripts to rocketshow.net/install/script/*.sh, if updated
- Copy dist/before.sh, dist/after.sh to rocketshow.net/update/xy.sh, if updated
- Copy the new complete image to rocketshow.net/install/images and change the file latest.php to link the new version
- Copy seed directory directory.tar.gz to rocketshow.net/install, if updated

### Application

The built application should be uploaded to rocketshow.net/update and be named "rocketshow.jar". The file "
currentversion2.xml" can be modified accordingly.

## Code structure

### Server

#### Overview

The Rocket Show server is written in Java and uses Spring
Boot ([https://spring.io/projects/spring-boot](https://spring.io/projects/spring-boot)).
Gstreamer ([https://gstreamer.freedesktop.org/](https://gstreamer.freedesktop.org/)), a framework written in C, is
included as multimedia playback backend.

Spring services are autowired into each other (dependency injection). The interface is named XyService, the
corresponding implementation is called DefaultXyService.

The class RocketShowApplication serves as the application entry point. Some beans are initialized in the correct order.

There is a player service, which organises all composition players (responsible for the playback of a single
composition). Parallel playbacks are possible as well.

The code is structured in different modules, which are described in more details below.

##### Base functionalities

A few base services and models lie in the root folder (e.g. settings, session-handling).

##### Api

This module is responsible for the communication with the web app and with other Rocket Show devices. A couple of REST
interfaces are exposed as well as some web sockets for time critical topics or where server push is required.

##### Audio

Services related to audio playback.

The Gstreamer audio pipeline looks like this:
```
uridecodebin
-> audioconvert mix-matrix=...
-> audioresample
-> audiomixer
-> capsfilter audio/x-raw,rate=48000,channels=2
(-> level)
-> queue
-> volume
-> alsasink
```

##### Composition

Handling the composition and the composition player.

##### Gstreamer

Rocket Show specific calls to the native Gstreamer C api.

##### Image

Handling the image displaying.

##### Lighting

Responsible for the connection of Rocket Show to the Open Lighting Architecture to control connected lighting
interfaces. Services for designer project playback also lies here.

The sources of the inluded jar file with the OLA client are copied from an archive into ola-java-client-src.

##### MIDI

MIDI input, output routing and mapping.

##### Raspberry

Raspberry Pi specific services (e.g. GPIO triggers).

##### Util

Various utilities used across the project.

### Web app

TODO