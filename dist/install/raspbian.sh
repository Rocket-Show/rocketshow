#!/bin/bash
# 
# Install Rocket Show on a Raspian Bullseye.
# This script needs to be executed as root.
# 

# Install all required packages
# - libnss-mdns installs the Bonjour service, if not already installed
# - dhcpcd is used to set a static IP address for the wifi hotspot feature
# - dnsmasq is a DHCP server
# - hostapd installs the wifi host features
apt-get update
apt-get upgrade -y

apt-get -y install unzip openjdk-17-jdk dhcpcd dnsmasq hostapd fbi ola libnss-mdns iptables libasound2 alsa-utils openssh-sftp-server libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-libav gstreamer1.0-tools gstreamer1.0-alsa gstreamer1.0-gl

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
usermod -a -G gpio rocketshow

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
cd rocketshow

# Add execution permissions on the update script
chmod +x update.sh

# Download the current set of fixtures
wget https://rocketshow.net/designer/downloads/fixtures.zip
unzip fixtures.zip -d fixtures
rm fixtures.zip

# Download current JAR and version info
wget https://www.rocketshow.net/update/rocketshow.jar
wget https://www.rocketshow.net/update/currentversion2.xml

# Set the user rocketshow as owner and add execution permissions
# on the update script
chown -R rocketshow:rocketshow /opt/rocketshow

# Install the wireless access point feature
# https://www.raspberrypi.org/documentation/configuration/wireless/access-point.md
systemctl unmask hostapd
systemctl enable hostapd
systemctl stop dnsmasq
systemctl stop hostapd

# Configure the wlan0 interface to have a static ip address
nmcli connection modify "wlan0" ipv4.addresses "192.168.4.1/24"

# Configure dnsmasq (DHCP server)
printf "\n# ROCKETSHOWSTART\naddress=/rocketshow.local/192.168.4.1\ninterface=wlan0\nlisten-address=192.168.4.1\ndhcp-range=192.168.4.2,192.168.4.20,255.255.255.0,24h\n# ROCKETSHOWEND\n" | tee -a /etc/dnsmasq.conf

touch /etc/hostapd/hostapd.conf

chmod 777 /etc/hostapd/hostapd.conf

cat <<'EOF' >/etc/hostapd/hostapd.conf
interface=wlan0
driver=nl80211
ssid=Rocket Show
utf8_ssid=1
hw_mode=g
channel=7
country_code=US
wmm_enabled=0
macaddr_acl=0
auth_algs=1
ignore_broadcast_ssid=0
wpa_key_mgmt=WPA-PSK
wpa_pairwise=TKIP
rsn_pairwise=CCMP
EOF

printf "\n# ROCKETSHOWSTART\nnet.ipv4.ip_forward=1\n# ROCKETSHOWEND\n" | tee -a /etc/sysctl.conf

# Set the country code (required in order for wlan0 and hostapd to work)
raspi-config nonint do_wifi_country US

# Delay hostapd startup to make sure it waits for the interfaces to be ready
HOSTAPD_OVERRIDE_CONF="[Unit]
After=network.target sys-subsystem-net-devices-wlan0.device
Requires=sys-subsystem-net-devices-wlan0.device

[Service]
ExecStartPre=/bin/sleep 5
"
HOSTAPD_OVERRIDE_DIR="/etc/systemd/system/hostapd.service.d"
sudo mkdir -p "$HOSTAPD_OVERRIDE_DIR"
echo "$HOSTAPD_OVERRIDE_CONF" | sudo tee "$HOSTAPD_OVERRIDE_DIR/override.conf" > /dev/null

# Install pi4j
curl -s get.pi4j.com | bash

# Add execution permissions to the start script
chmod +x start.sh

# Add a service to automatically start the app on boot and redirect port 80 to 8080
cat <<'EOF' >/etc/rc.local
#!/bin/sh -e
#
iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8080
su - rocketshow -c 'cd /opt/rocketshow && /opt/rocketshow/start.sh &'
exit 0
EOF

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

# Add a default ALSA device for Java sound to work
cat <<'EOF' >/home/rocketshow/.asoundrc
pcm.!default {
  type plug
  slave {
    pcm "hw:0,0"
  }
}

EOF

chown -R rocketshow:rocketshow /home/rocketshow
chown -R rocketshow:rocketshow /opt/rocketshow_factory.tar.gz
chown -R rocketshow:rocketshow /opt/rocketshow_reset.sh

chmod 755 /opt/rocketshow_factory.tar.gz
chmod 755 /opt/rocketshow_reset.sh

# Give the setup some time, because umount won't work afterwards if called too fast ("umount: device is busy")
echo "Wait 5 seconds..."
sleep 5s