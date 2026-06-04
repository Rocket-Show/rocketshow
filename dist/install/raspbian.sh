#!/bin/bash
# 
# Install Rocket Show on a Raspberry Pi Lite OS.
# This script needs to be executed as root.
#

# ---- INSTALL PACKAGES ----
# - libnss-mdns installs the Bonjour service, if not already installed
# - dhcpcd is used to set a static IP address for the wifi hotspot feature
# - dnsmasq is a DHCP server
# - hostapd installs the wifi host features

echo "Install packages"
apt-get update
apt-get upgrade -y

apt-get -y install unzip openjdk-21-jdk dhcpcd dnsmasq hostapd fbi ola libnss-mdns iptables libasound2 alsa-utils openssh-sftp-server libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-libav gstreamer1.0-tools gstreamer1.0-alsa gstreamer1.0-gl

# ---- USER MANAGEMENT ----
# Add the rocketshow user

echo "User management"
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
usermod -a -G render rocketshow
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
chmod 777 /etc/dhcpcd.conf

# Disable initial Raspberry Pi userconfig-service to setup an initial user (don't needed, because we already did it automatically)
systemctl disable userconfig.service || true
systemctl mask userconfig.service || true

# ---- INSTALL ROCKET SHOW ----

echo "Install Rocket Show"

# Prepare running directory
cd /opt
mkdir -p rocketshow
cd rocketshow

mkdir sessions
mkdir upload-tmp

# Download current JAR and version info
wget https://www.rocketshow.net/update/rocketshow.jar

# Create default config files
cat <<'EOF' >/home/rocketshow/.asoundrc
pcm.!default {
  type plug
  slave {
    pcm "hw:0,0"
  }
}
EOF

# Download a black image to be displayed as default
wget https://www.rocketshow.net/install/black.jpg

# Download the designer template
wget https://www.rocketshow.net/install/designer_template.json

# Download the defaults including some sample files
wget https://rocketshow.net/install/defaults.tar.gz
tar xvzf ./defaults.tar.gz
rm defaults.tar.gz
mv defaults/* .
rm -rf defaults

# Download the current set of fixtures
wget https://rocketshow.net/designer/downloads/fixtures.zip
unzip fixtures.zip -d fixtures
rm fixtures.zip

# Install wireless access point, but only enable from the java app (after setting the country)
# https://www.raspberrypi.org/documentation/configuration/wireless/access-point.md
echo "Install wireless access point"
systemctl unmask hostapd
systemctl stop dnsmasq
systemctl stop hostapd

# Configure the wlan0 interface to have a static ip address
cat >> /etc/dhcpcd.conf <<'EOF'
interface wlan0
static ip_address=192.168.4.1/24
nohook wpa_supplicant
EOF

# Configure dnsmasq (DHCP server)
cat <<'EOF' >> /etc/dnsmasq.conf
address=/rocketshow.local/192.168.4.1
interface=wlan0
listen-address=192.168.4.1
dhcp-range=192.168.4.2,192.168.4.20,255.255.255.0,24h
EOF

cat <<'EOF' >hostapd.conf
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
chmod 777 /etc/hostapd/hostapd.conf

cat <<'EOF' >/opt/rocketshow/set-wifi-ap-country.sh
#!/usr/bin/env bash

set -euo pipefail

TARGET_COUNTRY="${1:-}"

if [[ -z "$TARGET_COUNTRY" ]]; then
  echo "Usage: $0 <COUNTRY_CODE>" >&2
  exit 1
fi

get_persisted_country() {
  grep -o 'cfg80211.ieee80211_regdom=[A-Z][A-Z]' /boot/firmware/cmdline.txt 2>/dev/null \
    | head -n1 \
    | cut -d= -f2 \
    || true
}

PERSISTED_COUNTRY="$(get_persisted_country)"

echo "Persisted country: ${PERSISTED_COUNTRY:-<not set>}"
echo "Target country:    $TARGET_COUNTRY"

if [[ "$PERSISTED_COUNTRY" == "$TARGET_COUNTRY" ]]; then
  echo "WiFi AP country already persisted. Nothing to do."
  exit 0
fi

BOOT_REMOUNTED_RW=0

cleanup() {
  sync || true

  if [[ "$BOOT_REMOUNTED_RW" -eq 1 && -d /boot/firmware && -f /provision/device-information.conf ]]; then
    sudo mount -o remount,ro /boot/firmware || true
  fi
}
trap cleanup EXIT

if [[ -d /boot/firmware && -f /provision/device-information.conf ]]; then
  sudo mount -o remount,rw /boot/firmware
  BOOT_REMOUNTED_RW=1
fi

sudo raspi-config nonint do_wifi_country "$TARGET_COUNTRY"

UPDATED_COUNTRY="$(get_persisted_country)"

if [[ "$UPDATED_COUNTRY" != "$TARGET_COUNTRY" ]]; then
  echo "Failed to persist WiFi country. Expected '$TARGET_COUNTRY', got '${UPDATED_COUNTRY:-<not set>}'." >&2
  exit 1
fi

echo "WiFi AP country persisted successfully."
EOF
chmod +x /opt/rocketshow/set-wifi-ap-country.sh

# Enable forwarding in kernel
echo "net.ipv4.ip_forward=1" > /etc/sysctl.d/99-ipforward.conf

# Set the country code (required in order for wlan0 and hostapd to work)
raspi-config nonint do_wifi_country US

# Delay hostapd startup to make sure it waits for the interfaces to be ready and its config file mount
install -d /etc/systemd/system/hostapd.service.d
cat > /etc/systemd/system/hostapd.service.d/override.conf <<'EOF'
[Unit]
RequiresMountsFor=/etc/hostapd/hostapd.conf
After=sys-subsystem-net-devices-wlan0.device
Wants=sys-subsystem-net-devices-wlan0.device
BindsTo=sys-subsystem-net-devices-wlan0.device

[Service]
ExecStartPre=/bin/sh -ec '\
  for i in $(seq 1 50); do \
    [ -d /sys/class/net/wlan0 ] && exit 0; \
    sleep 0.1; \
  done; \
  echo "wlan0 did not appear in time" >&2; \
  exit 1'
Restart=on-failure
RestartSec=2
EOF

# Set the hostname
sed -i '/127.0.1.1/d' /etc/hosts
sed -i "\$a127.0.1.1\tRocketShow" /etc/hosts
sed -i 's/raspberrypi/RocketShow/g' /etc/hostname

# Enable SSH
systemctl enable ssh

# Disable Bluetooth
sudo systemctl disable bluetooth.service
sudo systemctl mask hciuart.service 2>/dev/null || true

# ---- SERVICES ----

echo "Install services"

# iptables rules
cat <<'EOF' >/opt/rocketshow/iptables.sh
#!/bin/bash
set -euo pipefail
if ! iptables -t nat -C PREROUTING -p tcp --dport 80 -j REDIRECT --to-ports 8080 2>/dev/null; then
  iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-ports 8080
fi
EOF
sudo chmod +x /opt/rocketshow/iptables.sh

# LAN static IP configuration script (uses NetworkManager/nmcli, available on Trixie)
cat <<'EOF' >/opt/rocketshow/set-lan-ip.sh
#!/bin/bash
# Configure eth0 IP assignment via NetworkManager.
# Usage: set-lan-ip.sh dhcp
#        set-lan-ip.sh static <ip> <subnet_mask> <gateway> [dns1] [dns2]

MODE="$1"
IP_ADDRESS="$2"
SUBNET_MASK="${3:-255.255.255.0}"
GATEWAY="$4"
DNS1="$5"
DNS2="$6"

IFACE="eth0"

mask_to_prefix() {
    local mask=$1 prefix=0 n
    local IFS=.
    read -ra octets <<< "$mask"
    for octet in "${octets[@]}"; do
        n=$octet
        while [ "$n" -gt 0 ]; do
            prefix=$(( prefix + (n & 1) ))
            n=$(( n >> 1 ))
        done
    done
    echo $prefix
}

# Prefer the currently active connection; fall back to any configured connection.
# Use sed instead of cut so connection names containing ':' are handled correctly.
CON=$(nmcli -t -f NAME,DEVICE con show --active | grep ":${IFACE}$" | sed 's/:[^:]*$//' | head -1)
if [ -z "$CON" ]; then
    CON=$(nmcli -t -f NAME,DEVICE con show | grep ":${IFACE}$" | sed 's/:[^:]*$//' | head -1)
fi

if [ -z "$CON" ]; then
    CON="rocketshow-eth0"
    nmcli con add type ethernet ifname "$IFACE" con-name "$CON" connection.autoconnect yes
fi

if [ "$MODE" = "static" ]; then
    PREFIX=$(mask_to_prefix "$SUBNET_MASK")
    nmcli con mod "$CON" \
        ipv4.method manual \
        ipv4.addresses "${IP_ADDRESS}/${PREFIX}" \
        ipv4.gateway "$GATEWAY"

    DNS_SERVERS=""
    [ -n "$DNS1" ] && DNS_SERVERS="$DNS1"
    [ -n "$DNS1" ] && [ -n "$DNS2" ] && DNS_SERVERS="$DNS1 $DNS2"
    nmcli con mod "$CON" ipv4.dns "$DNS_SERVERS"
else
    nmcli con mod "$CON" \
        ipv4.method auto \
        ipv4.addresses "" \
        ipv4.gateway "" \
        ipv4.dns ""
fi

# Reactivate the connection so changes take effect immediately without a reboot.
nmcli con up "$CON"
EOF
sudo chmod +x /opt/rocketshow/set-lan-ip.sh

cat <<'EOF' >/etc/systemd/system/rocketshow-iptables.service
[Unit]
Description=RocketShow iptables
After=network-pre.target
Before=network.target
DefaultDependencies=no

[Service]
Type=oneshot
ExecStart=/opt/rocketshow/iptables.sh
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable rocketshow-iptables.service

# Rocket Show app
cat <<'EOF' >/etc/systemd/system/rocketshow.service
[Unit]
Description=RocketShow
After=olad.service network-online.target
Wants=olad.service network-online.target

[Service]
# Run the main process as rocketshow
User=rocketshow
Group=rocketshow
WorkingDirectory=/opt/rocketshow

# Main process: KEEP IN FOREGROUND (no &)
# Wait for port 9010 from OLA to be ready before starting the app
[Service]
ExecStartPre=/bin/sh -c 'for i in $(seq 1 120); do nc -z 127.0.0.1 9010 && exit 0; echo "Waiting for olad on :9010 ($i/120)"; sleep 1; done; echo "olad not ready"; exit 1'
ExecStart=/usr/bin/java -Xmx512m -jar /opt/rocketshow/rocketshow.jar

Restart=on-failure
RestartSec=2s

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable rocketshow.service

# ---- FINISH ----

echo "Finish"

# Set owner of directory
chown -R rocketshow:rocketshow /opt/rocketshow

# Give the setup some time, because umount won't work afterwards if called too fast ("umount: device is busy")
echo "Wait 5 seconds..."
sleep 5s