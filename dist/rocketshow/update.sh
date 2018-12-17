#!/bin/bash
# 
# Update-script for RocketShow.
# 

# Set the execution permissions on the downloaded scripts
chmod +x ./update/before.sh
chmod +x ./update/after.sh

# Execute the before script
/bin/bash ./update/before.sh

cp ./update/currentversion2.xml ./currentversion2.xml
cp ./update/rocketshow.jar ./rocketshow.war

# Execute the after script
/bin/bash ./update/after.sh

# Cleanup the update folder
rm -rf ./update/*