#!/bin/bash
# 
# Update the currently installed Rocket Show system before installing the new WAR.
# 

### PREPARE ###
# Get the currently installed version
CURR_VERSION=$(cat /opt/rocketshow/currentversion.xml | grep -oPm1 "(?<=<version>)[^<]+")

# Get the new version
NEW_VERSION=$(cat /opt/rocketshow/update/currentversion.xml | grep -oPm1 "(?<=<version>)[^<]+")

### Install the wireless access point feature ###
# https://www.raspberrypi.org/documentation/configuration/wireless/access-point.md
UPD_VERSION="1.2.0"
if [ $UPD_VERSION = $CURR_VERSION ] || [ $UPD_VERSION != $(printf "$UPD_VERSION\n$CURR_VERSION\n" | sort -V | head -n1) ] ; then
    echo "Installing the wireless access point feature..."
    sudo apt-get -y install dnsmasq hostapd
    
    sudo systemctl stop dnsmasq
	sudo systemctl stop hostapd
	
	printf "\n# ROCKETSHOWSTART\ninterface wlan0\n    static ip_address=192.168.4.1/24\n# ROCKETSHOWEND\n" | sudo tee -a /etc/dhcpcd.conf
	
	sudo service dhcpcd restart
	
	printf "\n# ROCKETSHOWSTART\ninterface=wlan0\n  dhcp-range=192.168.4.2,192.168.4.20,255.255.255.0,24h\naddress=/#/192.168.4.1\n# ROCKETSHOWEND\n" | sudo tee -a /etc/dnsmasq.conf

	sudo chmod 777 /etc/hostapd/hostapd.conf

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

	printf "\n# ROCKETSHOWSTART\nDAEMON_CONF=\"/etc/hostapd/hostapd.conf\"\n# ROCKETSHOWEND\n" | sudo tee -a /etc/default/hostapd
	
	sudo systemctl start hostapd
	sudo systemctl start dnsmasq
	
	printf "\n# ROCKETSHOWSTART\nnet.ipv4.ip_forward=1\n# ROCKETSHOWEND\n" | sudo tee -a /etc/sysctl.conf
	
	sudo iptables -t nat -A  POSTROUTING -o eth0 -j MASQUERADE
	
	sudo sh -c "iptables-save > /etc/iptables.ipv4.nat"
	
	sudo sed -i '/exit 0/i# ROCKETSHOWSTART\niptables-restore < /etc/iptables.ipv4.nat\n# ROCKETSHOWEND\n' /etc/rc.local
fi