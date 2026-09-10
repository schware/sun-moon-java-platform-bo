package com.sunmoon.bo.domain.store;

import jakarta.validation.constraints.NotBlank;

public record StoreRequest(
        @NotBlank String storeId,
        @NotBlank String name,
        String phone,
        String address,
        Boolean active) {

    public Store toDomain() {
        return new Store(storeId, name, phone, address, active == null || active);
    }
}
