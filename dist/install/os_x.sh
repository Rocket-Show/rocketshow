#!/bin/bash
# 
# Install Rocket Show on a Mac OS X.
# This script needs to be executed as root.
#

# Download the initial directory structure including samples
wget https://rocketshow.net/install/directory.tar.gz
tar xvzf ./directory.tar.gz
rm directory.tar.gz
cd rocketshow

# Download the OS X gstreamer packages
# TODO

# Add execution permissions on the update script
chmod +x update.sh

# Download the current app and version info
wget https://www.rocketshow.net/update/rocketshow.jar
wget https://www.rocketshow.net/update/currentversion2.xml