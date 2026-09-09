package com.sunmoon.bo.domain.menu;

import jakarta.validation.constraints.NotBlank;

public record MenuRequest(
        @NotBlank String menuCode,
        @NotBlank String name,
        String imageUrl,
        String description,
        Boolean active) {

    public Menu toDomain() {
        return new Menu(menuCode, name, imageUrl, description, active == null || active);
    }
}
