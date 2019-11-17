#!/bin/bash
# 
# Install Rocket Show on a DietPi Stretch.
#

# Install all required packages (libnss-mdns installs the Bonjour service, if not already installed)
apt-get update
apt-get upgrade -y

# Install step-by-step because it does not work alltogether (timeouts to raspbian.org, maybe due to connection limits).
apt-get -y install openjdk-11-jdk fbi ola libnss-mdns wiringpi iptables alsa-base libasound2 alsa-utils openssh-sftp-server libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-libav gstreamer1.0-tools gstreamer1.0-x gstreamer1.0-alsa gstreamer1.0-gl gstreamer1.0-gtk3

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

# Lock the user DietPi
passwd --lock dietpi

# Disable login over SSH with root
sed -i 's/DROPBEAR_EXTRA_ARGS=/DROPBEAR_EXTRA_ARGS=-g/g' /etc/default/dropbear

# Set the required config files to writeable for the rocketshow user
# chmod 777 /DietPi/config.txt
#chmod 777 /etc/wpa_supplicant/wpa_supplicant.conf
#chmod 777 /etc/dhcpcd.conf

# Download the initial directory structure including samples
cd /opt
wget https://rocketshow.net/install/directory.tar.gz
tar xvzf ./directory.tar.gz
rm directory.tar.gz
cd rocketshow

# Add execution permissions on the update script
chmod +x update.sh

# Install an USB interface reset according to
# https://raspberrypi.stackexchange.com/questions/9264/how-do-i-reset-a-usb-device-using-a-script
chmod +x ./bin/raspberry-usbreset

# Overclock the raspberry to sustain streams without underruns
# - Set more memory for the GPU to play larger video files with omx
# - Enable turbo-mode by default (boot_delay avoids sdcard corruption)
# - Overclock the sdcard a little bit to prevent bufferunderruns with ALSA
# - Hide warnings (e.g. temperature icon)
configfile=/DietPi/config.txt
sed -i '1i# ROCKETSHOWSTART\nboot_delay=1\ndtparam=sd_overclock=100\navoid_warnings=1\ndtoverlay=audioinjector-addons\n# ROCKETSHOWEND\n' $configfile
sed -i 's/gpu_mem_256=16/gpu_mem_256=256/g' $configfile
sed -i 's/gpu_mem_512=16/gpu_mem_512=256/g' $configfile
sed -i 's/gpu_mem_1024=16/gpu_mem_1024=256/g' $configfile

# Set rocketshows nice priority to 10
sed -i '1irocketshow soft priority 10' /etc/security/limits.conf

# Add realtime permissions to the audio group
sed -i '1i@audio   -  rtprio     99\n@audio   -  memlock    unlimited' /etc/security/limits.d/audio.conf

# Download current JAR and version info
wget https://www.rocketshow.net/update/rocketshow.jar
wget https://www.rocketshow.net/update/currentversion2.xml

# Set the user rocketshow as owner and add execution permissions
# on the update script
chown -R rocketshow:rocketshow /opt/rocketshow

# Install the wireless access point feature
# https://www.raspberrypi.org/documentation/configuration/wireless/access-point.md
systemctl stop hostapd

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

printf "\n# ROCKETSHOWSTART\nDAEMON_CONF=\"/etc/hostapd/hostapd.conf\"\n# ROCKETSHOWEND\n" | tee -a /etc/default/hostapd

# Add a sleep to the start, because a race condition can cause the hotspot to fail on boot
# See: https://unix.stackexchange.com/questions/119209/hostapd-will-not-start-via-service-but-will-start-directly
# Starting with iface wlan0 inet static is too unstable
sudo sed -i '/start)/a \\tsleep 10' /etc/init.d/hostapd

systemctl start hostapd

printf "\n# ROCKETSHOWSTART\nnet.ipv4.ip_forward=1\n# ROCKETSHOWEND\n" | tee -a /etc/sysctl.conf

# Install pi4j
curl -s get.pi4j.com | bash

# Add execution permissions on the start script
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