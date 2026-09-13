package com.sunmoon.bo.domain.menu;

import java.math.BigDecimal;

/**
 * A menu row plus the price effective today — what the list screen shows.
 * {@code currentPrice} is null when no price row covers today, which the
 * screen should read as "가격 미등록", not zero.
 */
public record MenuSummary(
        String menuCode, String name, String imageUrl, String description, String kdsDeviceId, boolean active,
        BigDecimal currentPrice) {
}
