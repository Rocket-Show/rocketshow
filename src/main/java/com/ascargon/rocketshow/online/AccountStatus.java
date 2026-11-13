package com.ascargon.rocketshow.online;

/**
 * Whether the device is linked to an account of the online designer, and the name of that account.
 */
public record AccountStatus(boolean linked, String username) {
}
