package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The wired network (a link, its speed and address) and the wireless access point, if it's enabled.
 */
@Service
class NetworkSelfTest implements SelfTestStep {

    static final String LAN = "lan";
    static final String ACCESS_POINT = "access-point";

    // The interface of the access point, see the hostapd.conf written by the settings
    private static final String ACCESS_POINT_INTERFACE = "wlan0";

    private final SettingsService settingsService;
    private final SelfTestSystem system;

    NetworkSelfTest(SettingsService settingsService, SelfTestSystem system) {
        this.settingsService = settingsService;
        this.system = system;
    }

    @Override
    public List<String> getCheckNames() {
        return List.of(LAN, ACCESS_POINT);
    }

    @Override
    public void run(SelfTestContext context) {
        checkLan(context);
        context.report(checkAccessPoint());
    }

    private void checkLan(SelfTestContext context) {
        // The physical interfaces, which aren't wireless
        List<String> networkInterfaces = system.list("/sys/class/net").stream()
                .filter(name -> system.exists("/sys/class/net/" + name + "/device") && !system.exists("/sys/class/net/" + name + "/wireless"))
                .toList();

        if (networkInterfaces.isEmpty()) {
            context.report(SelfTestCheck.skipped(LAN, null, "No wired network interface"));
            return;
        }

        for (String networkInterface : networkInterfaces) {
            String path = "/sys/class/net/" + networkInterface + "/";

            if (!"1".equals(system.read(path + "carrier").map(String::trim).orElse(""))) {
                context.report(SelfTestCheck.skipped(LAN, networkInterface, "No link, no cable?"));
                continue;
            }

            String speed = system.read(path + "speed").map(String::trim).filter(value -> !value.startsWith("-")).map(value -> " at " + value + " Mbit/s").orElse("");
            List<String> addresses = system.getAddresses(networkInterface);

            context.report(SelfTestCheck.passed(LAN, networkInterface, "Link" + speed + ", " + (addresses.isEmpty() ? "no address" : String.join(", ", addresses))));
        }
    }

    private SelfTestCheck checkAccessPoint() {
        Settings settings = settingsService.getSettings();

        if (!Boolean.TRUE.equals(settings.getWlanApEnable())) {
            return SelfTestCheck.skipped(ACCESS_POINT, null, "Disabled in the settings");
        }

        String target = settings.getWlanApSsid();

        if (!system.exists("/sys/class/net/" + ACCESS_POINT_INTERFACE)) {
            return SelfTestCheck.failed(ACCESS_POINT, target, "No wireless interface " + ACCESS_POINT_INTERFACE);
        }

        try {
            String state = system.run("systemctl", "is-active", "hostapd").output();
            if (state.equals("active")) {
                return SelfTestCheck.passed(ACCESS_POINT, target, "hostapd is active on " + ACCESS_POINT_INTERFACE);
            }
            return SelfTestCheck.failed(ACCESS_POINT, target, "hostapd is " + (state.isEmpty() ? "not running" : state));
        } catch (Exception e) {
            return SelfTestCheck.failed(ACCESS_POINT, target, "Could not ask systemd about hostapd: " + e.getMessage());
        }
    }

}
