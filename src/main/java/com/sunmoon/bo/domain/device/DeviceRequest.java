package com.sunmoon.bo.domain.device;

import jakarta.validation.constraints.NotBlank;

public record DeviceRequest(
        @NotBlank String deviceId,
        @NotBlank String name,
        @NotBlank String deviceType,
        String location,
        Boolean active) {

    public Device toDomain() {
        return new Device(deviceId, name, deviceType, location, active == null || active);
    }
}
