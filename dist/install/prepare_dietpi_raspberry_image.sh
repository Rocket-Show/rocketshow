#!/bin/bash
#
# Prepare a DietPi image for Rocket Show on a Mac OS X.
#
cd /Volumes/boot
file="dietpi.txt"

# Set the host name
sed -i '' 's/AUTO_SETUP_NET_HOSTNAME=DietPi/AUTO_SETUP_NET_HOSTNAME=RocketShow/g' $file

# Automate the installation
sed -i '' 's/AUTO_SETUP_AUTOMATED=0/AUTO_SETUP_AUTOMATED=1/g' $file

# Don't wait for the network
sed -i '' 's/CONFIG_BOOT_WAIT_FOR_NETWORK=1/CONFIG_BOOT_WAIT_FOR_NETWORK=0/g' $file

# Automatically install Rocket Show
# Does not work. The script hangs infinitely and I don't know why...
#sed -i '' 's/AUTO_SETUP_CUSTOM_SCRIPT_EXEC=0/AUTO_SETUP_CUSTOM_SCRIPT_EXEC=https:\/\/rocketshow.net\/install\/script\/dietpi_raspberry.sh/g' $file

# Set the CPU governor to performance
sed -i '' 's/CONFIG_CPU_GOVERNOR=ondemand/CONFIG_CPU_GOVERNOR=performance/g' $file

# Disable DietPi check for updates
sed -i '' 's/CONFIG_CHECK_DIETPI_UPDATES=1/CONFIG_CHECK_DIETPI_UPDATES=0/g' $file

# Automatically install the WiFi hot spot
sed -i '' 's/#AUTO_SETUP_INSTALL_SOFTWARE_ID=44    #will install Bittorrent transmission/AUTO_SETUP_INSTALL_SOFTWARE_ID=60/g' $file


file="cmdline.txt"

# Disable boot text
sed -i '' 's/console=tty1/console=tty3/g' $file