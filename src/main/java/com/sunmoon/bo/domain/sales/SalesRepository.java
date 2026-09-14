package com.sunmoon.bo.domain.sales;

import java.time.LocalDate;
import java.util.List;

/**
 * 매출 as BO can read it. The implementation is an HTTP call to Order, not
 * a query — BO has no orders table and should never grow one.
 */
public interface SalesRepository {

    List<SalesSummary> summarize(LocalDate from, LocalDate to, String storeId);

    List<SalesOrder> detail(LocalDate businessDate, String storeId);
}
