package com.sunmoon.bo.domain.soldout;

import java.time.LocalDate;
import java.util.List;

public interface SoldOutRepository {

    List<SoldOut> findByStoreAndDate(String storeId, LocalDate businessDate);

    /** 이미 결품이면 아무 일도 하지 않습니다 — 두 번 눌러도 선언 시각이 안 밀립니다. */
    boolean declare(SoldOut soldOut);

    boolean release(String storeId, String menuCode, LocalDate businessDate);
}
