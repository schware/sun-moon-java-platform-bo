package com.sunmoon.bo.domain.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One 영업일자 × 매장's takings, as the Order service counted them.
 *
 * <p>{@code storeName} is the only field BO adds — Order knows the
 * {@code storeId} an order carried and nothing else about the shop, and a
 * screen that only showed {@code store-07} would make an operator go look
 * it up. Null when BO has no 매장 row for that id, which is itself worth
 * seeing: it means orders are arriving for a store nobody registered.
 */
public record SalesSummary(
        LocalDate businessDate,
        String storeId,
        String storeName,
        long orderCount,
        long acceptedCount,
        long rejectedCount,
        long expiredCount,
        BigDecimal salesAmount) {

    public SalesSummary withStoreName(String name) {
        return new SalesSummary(businessDate, storeId, name,
                orderCount, acceptedCount, rejectedCount, expiredCount, salesAmount);
    }
}
