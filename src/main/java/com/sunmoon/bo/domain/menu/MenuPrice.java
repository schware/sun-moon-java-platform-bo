package com.sunmoon.bo.domain.menu;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One effective-dated price row. {@code endDate} null means open-ended —
 * still the current price, until a later row amends it.
 */
public record MenuPrice(Long id, String menuCode, BigDecimal price, LocalDate startDate, LocalDate endDate) {

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }

    public boolean overlaps(MenuPrice other) {
        boolean thisEndsBeforeOtherStarts = endDate != null && endDate.isBefore(other.startDate);
        boolean otherEndsBeforeThisStarts = other.endDate != null && other.endDate.isBefore(startDate);
        return !thisEndsBeforeOtherStarts && !otherEndsBeforeThisStarts;
    }
}
