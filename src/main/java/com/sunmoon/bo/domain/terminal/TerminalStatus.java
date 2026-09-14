package com.sunmoon.bo.domain.terminal;

import java.time.Instant;
import java.util.List;

/**
 * One row of 단말 현황: what BO has registered, next to what the Device
 * Server can actually see.
 *
 * <p>The two halves can disagree in both directions, and both disagreements
 * are the point of the screen:
 *
 * <ul>
 *   <li>{@code registered && !connected} — a terminal BO knows about that
 *       is not talking to us. A shop with no screen up takes no orders.</li>
 *   <li>{@code !registered && connected} — a screen connected as an id
 *       nobody entered in 장비. The Device Server checks only that the id
 *       is well-formed ({@code AcceptKnownFormatDirectory}), so today this
 *       is how that gap is visible at all.</li>
 * </ul>
 */
public record TerminalStatus(
        String deviceId,
        String terminalType,
        String storeId,
        List<String> storeIds,
        String name,
        String location,
        String ipAddress,
        Integer port,
        boolean registered,
        boolean active,
        boolean connected,
        Instant connectedAt) {
}
