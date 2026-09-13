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
        @Pattern(regexp = "POS|KDS|DID", message = "terminalType must be POS, KDS or DID") String terminalType,
        String ipAddress,
        @Min(1) @Max(65535) Integer port,
        Boolean active) {

    public Device toDomain() {
        return new Device(
                deviceId, name, deviceType, location, terminalType, ipAddress, port, active == null || active);
    }
}
