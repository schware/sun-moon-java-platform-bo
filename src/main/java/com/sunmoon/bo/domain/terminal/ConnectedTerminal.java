package com.sunmoon.bo.domain.terminal;

import java.time.Instant;
import java.util.List;

/**
 * A terminal the Device Server currently has a WebSocket open to.
 *
 * <p>This is a live fact, not a stored one — it is read fresh on every
 * page load and never written down. BO keeping its own copy would mean a
 * screen that confidently shows a terminal as connected minutes after it
 * unplugged, which is exactly the answer 단말 현황 exists to avoid.
 *
 * <p>{@code storeId} is null for a DID watching several stores at once;
 * {@code storeIds} always carries the full list.
 */
public record ConnectedTerminal(
        String deviceId,
        String storeId,
        List<String> storeIds,
        String type,
        String channelId,
        Instant connectedAt) {
}
