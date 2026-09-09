package com.sunmoon.platform.domain.device;

/**
 * Device master data only — no connection state. Whether a device is
 * currently connected, and the handshake that gets it there, belong to the
 * (not yet built) Device Server, not to BO.
 *
 * <p>{@code deviceType} is expected to name a Common Code in the
 * {@code DEVICE_TYPE} group, but isn't a foreign key — common codes are
 * editable at runtime, and a dangling type shouldn't make a device row
 * unreadable.
 */
public record Device(String deviceId, String name, String deviceType, String location, boolean active) {
}
