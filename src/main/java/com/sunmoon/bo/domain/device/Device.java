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
 */
public record Device(
        String deviceId,
        String name,
        String deviceType,
        String location,
        String terminalType,
        String ipAddress,
        Integer port,
        boolean active) {
}
