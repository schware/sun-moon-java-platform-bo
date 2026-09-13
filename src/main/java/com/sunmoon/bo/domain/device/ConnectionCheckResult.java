package com.sunmoon.bo.domain.device;

/**
 * {@code NO_ADDRESS} is distinct from {@code UNREACHABLE} — a device with
 * no ip/port recorded has never been asked, and saying so is more honest
 * than reporting it down.
 */
public enum ConnectionCheckResult {
    REACHABLE, UNREACHABLE, NO_ADDRESS
}
