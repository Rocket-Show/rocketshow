#!/bin/bash
# 
# Install Rocket Show on a Debian Stretch.
# This script needs to be executed as root.
# 

# Install all required packages
apt-get update
apt-get upgrade

apt-get -y install oracle-java8-jdk fbi ola mplayer zip

# Install the gstreamer packages
apt-get install libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-libav

# Add the rocketshow user
adduser \
  --system \
  --shell /bin/bash \
  --gecos 'Rocket Show' \
  --group \
  --home /home/rocketshow \
  rocketshow

# Download the initial directory structure including samples
cd /opt
wget https://rocketshow.net/install/directory.tar.gz
tar xvzf ./directory.tar.gz
rm directory.tar.gz

# Create the directories (only, if not included in the initial directory seed)
cd /opt/rocketshow
mkdir -p bin
mkdir -p log
mkdir -p media
mkdir -p media/midi
mkdir -p media/audio
mkdir -p media/video
mkdir -p sets
mkdir -p compositions
mkdir -p tomcat
mkdir -p update

# Add execution permissions on the update script
chmod +x /opt/rocketshow/update.sh

# Install Tomcat (credits to https://wolfpaulus.com/java/tomcat-jessie/)
mkdir -p ~/tmp
cd ~/tmp
wget https://rocketshow.net/install/tomcat/apache-tomcat-8.5.24.tar.gz
tar xvzf ./apache-tomcat-8.5.24.tar.gz
rm apache-tomcat-8.5.24.tar.gz
mv ./apache-tomcat-8.5.24 ./tomcat
mv tomcat /opt/rocketshow
chown -R rocketshow:rocketshow /opt/rocketshow/tomcat/*
chmod +x /opt/rocketshow/tomcat/bin/*.sh

# Create the init script for tomcat
cat <<'EOF' >/etc/init.d/tomcat
#!/bin/bash
#
### BEGIN INIT INFO
# Provides:        tomcat
# Required-Start:  $network
# Required-Stop:   $network
# Default-Start:   2 3 4 5
# Default-Stop:    0 1 6
# Short-Description: Start/Stop Tomcat server
### END INIT INFO
 
PATH=/sbin:/bin:/usr/sbin:/usr/bin
 
start() {
 /bin/su - rocketshow -c /opt/rocketshow/tomcat/bin/startup.sh
}
 
stop() {
 /bin/su - rocketshow -c /opt/rocketshow/tomcat/bin/shutdown.sh 
}
 
case $1 in
  start|stop) $1;;
  restart) stop; start;;
  *) echo "Run as $0 &lt;start|stop|restart&gt;"; exit 1;;
esac
EOF

chmod 755 /etc/init.d/tomcat
update-rc.d tomcat defaults

# Get Tomcat some memory (add this line at the beginning)
sed -i '2iCATALINA_OPTS="-Djava.awt.headless=true -Dfile.encoding=UTF-8 -server -Xms500m -Xmx500m"\n' /opt/rocketshow/tomcat/bin/catalina.sh

# Speedup tomcat start (add this line at the beginning)
sed -i '2iJAVA_OPTS="-Djava.security.egd=file:/dev/urandom"\n' /opt/rocketshow/tomcat/bin/catalina.sh

# Set rocketshows nice priority to 10
sed -i '1irocketshow soft priority 10' /etc/security/limits.conf

# Remove the default webapps
rm -rf /opt/rocketshow/tomcat/webapps/*

# Download current war and versioninfo
cd /opt/rocketshow/tomcat/webapps
wget -O ROOT.war https://www.rocketshow.net/update/current.war
cd /opt/rocketshow
wget https://www.rocketshow.net/update/currentversion.xml

# Set the user rocketshow as owner and add execution permissions
# on the update script
chown -R rocketshow:rocketshow /opt/rocketshow