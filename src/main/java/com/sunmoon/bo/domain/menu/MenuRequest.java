package com.sunmoon.bo.domain.menu;

import jakarta.validation.constraints.NotBlank;

public record MenuRequest(
        @NotBlank String menuCode,
        @NotBlank String name,
        String imageUrl,
        String description,
        String kdsDeviceId,
        Boolean active,
        @jakarta.validation.constraints.Positive(message = "기준 조리시간은 1분 이상이어야 합니다")
        Integer cookMinutes) {

    public Menu toDomain() {
        return new Menu(menuCode, name, imageUrl, description, kdsDeviceId, active == null || active,
                cookMinutes);
    }
}
