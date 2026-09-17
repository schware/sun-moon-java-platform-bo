package com.sunmoon.bo.domain.apporder;

import java.time.LocalDate;
import java.util.List;

/**
 * APP-SERVER의 주문 원장을 읽습니다. <b>읽기만 합니다</b> — BO에는 손님
 * 주문을 고치는 화면이 없고, 앞으로도 없어야 합니다.
 */
public interface AppOrderRepository {

    /** 그 판매일자의 주문들. {@code storeId}·{@code channel} 이 비면 전부입니다. */
    List<AppOrderRow> findByDay(LocalDate businessDate, String storeId, String channel);
}
