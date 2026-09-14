package com.sunmoon.bo.domain.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One order inside a day's 매출, as BO shows it.
 *
 * <p>Deliberately not Order's own record: this is the read side of another
 * service's model, and copying it field for field would make every change
 * there a compile error here. Unknown fields are ignored on the way in
 * (see {@code HttpSalesRepository}), so Order can grow 거래번호 and
 * 회원번호 without BO being redeployed to keep working.
 */
public record SalesOrder(
        Long id,
        String storeId,
        String customerId,
        String menuName,
        BigDecimal amount,
        String status,
        String acceptedBy,
        LocalDate businessDate,
        Instant placedAt,
        Instant updatedAt) {
}
