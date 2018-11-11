#!/bin/bash
# 
# Update-script for RocketShow.
# 

# Set the execution permissions on the downloaded scripts
chmod +x /opt/rocketshow/update/before.sh
chmod +x /opt/rocketshow/update/after.sh

# Execute the before script
/bin/bash /opt/rocketshow/update/before.sh

cp /opt/rocketshow/update/currentversion.xml /opt/rocketshow/currentversion.xml
cp /opt/rocketshow/update/current.war /opt/rocketshow/tomcat/webapps/ROOT.war

# Execute the after script
/bin/bash /opt/rocketshow/update/after.sh

# Cleanup the update folder
rm -rf /opt/rocketshow/update/*