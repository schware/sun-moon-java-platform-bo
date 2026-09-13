package com.sunmoon.bo.domain.device;

/**
 * Dials an address and reports whether anything answered. This is a
 * network fact, not a domain one — the interface lives in {@code domain}
 * only so {@link DeviceService} depends on an abstraction it can mock,
 * matching how the repository split works here.
 */
public interface ConnectionChecker {

    boolean isReachable(String ipAddress, int port);
}
