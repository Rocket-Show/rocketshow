#!/bin/bash
# 
# Install RocketShow on a Raspian.
# This script needs to be executed as root.
# 

# Install all required packages (libnss-mdns installs the Bonjour service, if not already installed)
apt-get update
apt-get upgrade

apt-get -y install oracle-java8-jdk omxplayer fbi ola mplayer libnss-mdns dnsmasq hostapd authbind

# Add the rocketshow user
adduser \
  --system \
  --shell /bin/bash \
  --gecos 'Rocket Show' \
  --group \
  --home /home/rocketshow \
  rocketshow

# Set the password
echo rocketshow:thisrocks | chpasswd

# Add the user to the required groups
usermod -a -G video rocketshow
usermod -a -G audio rocketshow
usermod -a -G plugdev rocketshow

# Add the sudoers permission (visudo)
insert="rocketshow      ALL=(ALL) NOPASSWD: ALL"
file="/etc/sudoers"

sed -i "s/root\sALL=(ALL:ALL) ALL/root    ALL=(ALL:ALL) ALL\n$insert/" $file

# Lock the user pi
passwd --lock pi

# Set the required config files to writeable for the rocketshow user
# chmod 777 /boot/config.txt -> Does not work
chmod 777 /etc/wpa_supplicant/wpa_supplicant.conf
chmod 777 /etc/dhcpcd.conf 

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

# Set default port to 80
touch /etc/authbind/byport/{443,80}
chmod 500 /etc/authbind/byport/{443,80}
chown rocketshow:rocketshow /etc/authbind/byport/{443,80}

sed -i 's/8080/80/g' /opt/rocketshow/tomcat/conf/server.xml
sed -i 's/8443/443/g' /opt/rocketshow/tomcat/conf/server.xml

# Make tomcat use authbind
sed -i 's/exec "$PRGDIR"/exec authbind --deep "$PRGDIR"/g' /opt/rocketshow/tomcat/bin/startup.sh

# Install an USB interface reset according to
# https://raspberrypi.stackexchange.com/questions/9264/how-do-i-reset-a-usb-device-using-a-script
cd /opt/rocketshow/bin
chmod +x usbreset

# Overclock the raspberry to sustain streams without underruns
# - Set more memory for the GPU to play larger video files with omxplayer
# - Enable turbo-mode by default (boot_delay avoids sdcard corruption)
# - Overclock the sdcard a little bit to prevent bufferunderruns with ALSA
# - Hide warnings (e.g. temperature icon)
sed -i '1i# ROCKETSHOWSTART\ngpu_mem=256\nforce_turbo=1\nboot_delay=1\ndtparam=sd_overclock=100\navoid_warnings=1\n# ROCKETSHOWEND\n' /boot/config.txt

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

# Install the wireless access point feature
# https://www.raspberrypi.org/documentation/configuration/wireless/access-point.md
systemctl stop dnsmasq
systemctl stop hostapd

printf "\n# ROCKETSHOWSTART\ninterface wlan0\n    static ip_address=192.168.4.1/24\n# ROCKETSHOWEND\n" | tee -a /etc/dhcpcd.conf

service dhcpcd restart

printf "\n# ROCKETSHOWSTART\ninterface=wlan0\n  dhcp-range=192.168.4.2,192.168.4.20,255.255.255.0,24h\naddress=/rocketshow.local/192.168.4.1\n# ROCKETSHOWEND\n" | tee -a /etc/dnsmasq.conf

touch /etc/hostapd/hostapd.conf

chmod 777 /etc/hostapd/hostapd.conf

cat <<'EOF' >/etc/hostapd/hostapd.conf
interface=wlan0
driver=nl80211
ssid=Rocket Show
utf8_ssid=1
hw_mode=g
channel=7
wmm_enabled=0
macaddr_acl=0
auth_algs=1
ignore_broadcast_ssid=0
wpa_key_mgmt=WPA-PSK
wpa_pairwise=TKIP
rsn_pairwise=CCMP
EOF

printf "\n# ROCKETSHOWSTART\nDAEMON_CONF=\"/etc/hostapd/hostapd.conf\"\n# ROCKETSHOWEND\n" | tee -a /etc/default/hostapd

systemctl start hostapd
systemctl start dnsmasq

printf "\n# ROCKETSHOWSTART\nnet.ipv4.ip_forward=1\n# ROCKETSHOWEND\n" | tee -a /etc/sysctl.conf

# Keep the whole directory in its current state for the factory reset
cd /opt
tar -zcvf rocketshow_factory.tar.gz rocketshow

# Create the factory reset script
cat <<'EOF' >/opt/rocketshow_reset.sh
#!/bin/bash
#
rm -rf /opt/rocketshow
cd /opt
tar xvzf /opt/rocketshow_factory.tar.gz
sudo reboot
EOF

chmod +x /opt/rocketshow_reset.sh

# Set the hostname to RocketShow
sed -i '/127.0.1.1/d' /etc/hosts
sed -i "\$a127.0.1.1\tRocketShow" /etc/hosts

sed -i 's/raspberrypi/RocketShow/g' /etc/hostname