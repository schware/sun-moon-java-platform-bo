package com.sunmoon.bo.domain.device;

/**
 * Device master data only — no <em>live</em> connection state. Whether a
 * terminal is connected right now, and the handshake that gets it there,
 * belong to the Device Server, not to BO — {@link #ipAddress()} and
 * {@link #port()} here are addresses BO can dial to ask "is anything
 * answering", not a substitute for the Device Server's own registry.
 *
 * <p>{@code deviceType} is expected to name a Common Code in the
 * {@code DEVICE_TYPE} group, but isn't a foreign key — common codes are
 * editable at runtime, and a dangling type shouldn't make a device row
 * unreadable. {@code terminalType} is a separate, narrower column: it is
 * one of the Device Server's own {@code TerminalType} values (POS/KDS/DID)
 * for a device that connects there, and null for equipment that does not
 * (a printer, a router) — the deliberately open-ended {@code deviceType}
 * is not precise enough for the Device Server to validate a handshake
 * against.
 *
 * <p>{@code storeId} is which 매장 this terminal stands in. It was missing
 * until 2026-09-15, and its absence showed up the moment 단말 현황 tried to
 * line these rows up against the Device Server's own registry: a terminal
 * there is identified by <em>(매장, 단말기번호, 종류)</em>, because every
 * branch has a {@code pos-01}. A device master with no store could not
 * name a single one of them.
 *
 * <p>Nullable, and deliberately so — equipment that never connects to the
 * Device Server (a back-office printer) has no store to be in, and the
 * rows that existed before this column did have no store anyone can infer.
 */
public record Device(
        String deviceId,
        String name,
        String deviceType,
        String location,
        String storeId,
        String terminalType,
        String ipAddress,
        Integer port,
        boolean active) {
}
