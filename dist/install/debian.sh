#!/bin/bash
# 
# Install Rocket Show on a Debian Stretch.
# This script needs to be executed as root.
# 

# Install all required packages
apt-get update
apt-get upgrade

apt-get -y install default-jre ola

# Install the gstreamer packages
apt-get install libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-libav

# Download the initial directory structure including samples
wget https://rocketshow.net/install/directory.tar.gz
tar xvzf ./directory.tar.gz
rm directory.tar.gz
cd rocketshow

# Add execution permissions on the update script
chmod +x update.sh

# Download the current app and version info
wget https://www.rocketshow.net/update/rocketshow.jar
wget https://www.rocketshow.net/update/currentversion2.xml