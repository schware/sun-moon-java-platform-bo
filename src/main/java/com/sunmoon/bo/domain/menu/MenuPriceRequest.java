package com.sunmoon.bo.domain.menu;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MenuPriceRequest(@NotNull BigDecimal price, @NotNull LocalDate startDate, LocalDate endDate) {

    public MenuPrice toDomain(String menuCode) {
        return new MenuPrice(null, menuCode, price, startDate, endDate);
    }
}
