package com.sunmoon.bo.domain.device;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeviceRequest(
        @NotBlank String deviceId,
        @NotBlank String name,
        @NotBlank String deviceType,
        String location,
        String storeId,
        @Pattern(regexp = "POS|KDS|DID|KIOSK", message = "terminalType must be POS, KDS, DID or KIOSK") String terminalType,
        String ipAddress,
        @Min(1) @Max(65535) Integer port,
        Boolean active,
        @Pattern(regexp = "MAIN|SUB", message = "kdsRole must be MAIN or SUB") String kdsRole) {

    public Device toDomain() {
        return new Device(deviceId, name, deviceType, location, blankToNull(storeId), terminalType,
                ipAddress, port, active == null || active,
                "KDS".equals(terminalType) ? (kdsRole == null ? "SUB" : kdsRole) : null);
    }

    /**
     * An empty {@code <select>} posts {@code ""}, not null. Stored as-is it
     * would be a store id that matches no 매장 and is not null either — a
     * third state the 단말 현황 join would have to know about.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
