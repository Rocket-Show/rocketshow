package com.ascargon.rocketshow.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultLanService implements LanService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultLanService.class);

    @Override
    public LanInfo getLanInfo() {
        String ipAddress = "";
        String subnetMask = "";
        String gateway = "";
        String dns1 = "";
        String dns2 = "";

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "nmcli", "-t", "-f", "IP4.ADDRESS,IP4.GATEWAY,IP4.DNS,DHCP4.OPTION", "device", "show", "eth0");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Collect all assigned addresses so we can match by DHCP lease later
            List<String[]> addressList = new ArrayList<>(); // [ip, dotted-mask]
            List<String> dnsList = new ArrayList<>();
            String dhcpLeaseIp = null;
            String dhcpLeaseMask = null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("IP4.ADDRESS[")) {
                        String cidr = line.substring(line.indexOf(':') + 1);
                        int slash = cidr.indexOf('/');
                        if (slash > 0) {
                            String ip = cidr.substring(0, slash);
                            String mask = prefixToMask(Integer.parseInt(cidr.substring(slash + 1)));
                            addressList.add(new String[]{ip, mask});
                        }
                    } else if (line.startsWith("IP4.GATEWAY:")) {
                        String gw = line.substring("IP4.GATEWAY:".length());
                        if (!"--".equals(gw)) gateway = gw;
                    } else if (line.startsWith("IP4.DNS[")) {
                        String dns = line.substring(line.indexOf(':') + 1);
                        if (!"--".equals(dns)) dnsList.add(dns);
                    } else if (line.startsWith("DHCP4.OPTION[")) {
                        // Format: "DHCP4.OPTION[N]:key = value"
                        String opt = line.substring(line.indexOf(':') + 1);
                        if (opt.startsWith("ip_address = ")) {
                            dhcpLeaseIp = opt.substring("ip_address = ".length());
                        } else if (opt.startsWith("subnet_mask = ")) {
                            dhcpLeaseMask = opt.substring("subnet_mask = ".length());
                        }
                    }
                }
            }

            process.waitFor();

            // Prefer the DHCP-leased address – it is the one the router actually knows.
            // Fall back to the first address in the list (static or link-local).
            if (dhcpLeaseIp != null && !dhcpLeaseIp.isEmpty()) {
                ipAddress = dhcpLeaseIp;
                if (dhcpLeaseMask != null && !dhcpLeaseMask.isEmpty()) {
                    subnetMask = dhcpLeaseMask;
                } else {
                    for (String[] entry : addressList) {
                        if (dhcpLeaseIp.equals(entry[0])) {
                            subnetMask = entry[1];
                            break;
                        }
                    }
                }
            } else if (!addressList.isEmpty()) {
                ipAddress = addressList.get(0)[0];
                subnetMask = addressList.get(0)[1];
            }

            if (!dnsList.isEmpty()) dns1 = dnsList.get(0);
            if (dnsList.size() > 1) dns2 = dnsList.get(1);

        } catch (Exception e) {
            logger.warn("Could not read LAN info via nmcli, falling back to NetworkInterface", e);
            try {
                NetworkInterface eth0 = NetworkInterface.getByName("eth0");
                if (eth0 != null) {
                    for (InterfaceAddress addr : eth0.getInterfaceAddresses()) {
                        if (addr.getAddress() instanceof Inet4Address) {
                            ipAddress = addr.getAddress().getHostAddress();
                            subnetMask = prefixToMask(addr.getNetworkPrefixLength());
                            break;
                        }
                    }
                }
            } catch (SocketException se) {
                logger.warn("Could not determine eth0 address via NetworkInterface", se);
            }
        }

        LanInfo lanInfo = new LanInfo();
        lanInfo.setIpAddress(ipAddress);
        lanInfo.setSubnetMask(subnetMask);
        lanInfo.setGateway(gateway);
        lanInfo.setDns1(dns1);
        lanInfo.setDns2(dns2);
        return lanInfo;
    }

    static String prefixToMask(int prefix) {
        if (prefix <= 0) return "0.0.0.0";
        if (prefix >= 32) return "255.255.255.255";
        int mask = ~(0xffffffff >>> prefix);
        return String.format("%d.%d.%d.%d",
                (mask >>> 24) & 0xff, (mask >>> 16) & 0xff,
                (mask >>> 8) & 0xff, mask & 0xff);
    }

}
