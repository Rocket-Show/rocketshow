# Rocket Show
An app to play shows including audio, video, lighting (e.g. DMX) and MIDI.

## For developers
### Development
#### Build
1. Build the Java JAR: `mvn install`
2. Start the backend server: `java -jar rocketshow.jar`
3. Start web frontend server: `cd src/main/webapp && npx ng serve`

Check the state of the backend: http://localhost:8080/api/system/state \
Open the web application: http://localhost:4200

### Deployment
#### Seed directory
The seed directory structure '/dist/rocketshow' can be packed on a mac with this commands (assuming you're currently in the 'dist' directory):
```shell
COPYFILE_DISABLE=true tar -c --exclude='.DS_Store' -zf directory.tar.gz rocketshow
```

#### Raspberry Pi Image building
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

#### Update process
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

#### Application
The built application should be uploaded to rocketshow.net/update and be named "rocketshow.jar". The file "currentversion2.xml" can be modified accordingly.