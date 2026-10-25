package com.ascargon.rocketshow.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The passphrase of the wireless access point. hostapd only protects the access point with a passphrase of 8 to 63
 * characters: a shorter one leaves it open, a longer one doesn't start it at all.
 */
public class WlanApPassphrase {

    private WlanApPassphrase() {
    }

    // Only checked for an enabled access point: a passphrase left from earlier (e.g. by a release taking shorter
    // ones) must not stop the other settings from being saved. An empty passphrase opens the access point on purpose.
    public static void check(Boolean enabled, String passphrase) {
        if (Boolean.TRUE.equals(enabled) && passphrase != null && !passphrase.isEmpty() && (passphrase.length() < 8 || passphrase.length() > 63)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The passphrase of the wireless access point needs 8 to 63 characters");
        }
    }

}
