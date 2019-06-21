#!/bin/bash
# 
# Update the currently installed Rocket Show system before installing the new JAR.
# 

### PREPARE ###
# Get the currently installed version
CURR_VERSION=$(cat /opt/rocketshow/currentversion2.xml | grep -oPm1 "(?<=<version>)[^<]+")

# Get the new version
NEW_VERSION=$(cat /opt/rocketshow/update/currentversion2.xml | grep -oPm1 "(?<=<version>)[^<]+")

### Install the wireless access point feature ###
# https://www.raspberrypi.org/documentation/configuration/wireless/access-point.md
UPD_VERSION="2.1.3"
if [ $UPD_VERSION = $CURR_VERSION ] || [ $UPD_VERSION != $(printf "$UPD_VERSION\n$CURR_VERSION\n" | sort -V | head -n1) ] ; then
    echo "Installing the wireless access point feature..."
    sudo sed -i '/start)/a \\tsleep 10' /etc/init.d/hostapd
fi

### Install zip ###
UPD_VERSION="1.3.0"
if [ $UPD_VERSION = $CURR_VERSION ] || [ $UPD_VERSION != $(printf "$UPD_VERSION\n$CURR_VERSION\n" | sort -V | head -n1) ] ; then
	sudo apt-get -y install zip
fi