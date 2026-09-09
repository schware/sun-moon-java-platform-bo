package com.sunmoon.bo.domain.commoncode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record CommonCodeRequest(
        @NotBlank String groupCode,
        @NotBlank String code,
        @NotBlank String name,
        @PositiveOrZero int sortOrder,
        Boolean active) {

    /** Defaults to active: a code created through the 신규 screen is meant to be usable. */
    public CommonCode toDomain() {
        return new CommonCode(groupCode, code, name, sortOrder, active == null || active);
    }
}
