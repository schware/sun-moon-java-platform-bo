package com.sunmoon.bo.domain.table;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record StoreTableRequest(
        Long id,
        @NotBlank String storeId,
        @Min(1) @Max(200) Integer floor,
        @NotBlank String tableNo,
        String displayName,
        @Min(0) @Max(999) Integer posX,
        @Min(0) @Max(999) Integer posY,
        Boolean active) {

    /**
     * {@code qrCode}는 요청에서 받지 않습니다. 발급은 서버가 하고, 한 번
     * 인쇄된 뒤에는 바뀌면 안 되는 값이라 화면에서 고칠 수 있게 두면
     * 안 됩니다.
     */
    public StoreTable toDomain(String qrCode) {
        return new StoreTable(id, storeId, floor == null ? 1 : floor, tableNo,
                blankToNull(displayName), posX, posY, qrCode, active == null || active);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
