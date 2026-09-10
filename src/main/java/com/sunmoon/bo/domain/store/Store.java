package com.sunmoon.bo.domain.store;

/**
 * 매장 기준 정보. A store's 영업 상태 — 개점/마감 and the 영업일자 it is on —
 * is deliberately not here: that is operational state owned by the Order
 * service, which stamps a 매출일자 on every order and mints the 거래번호 at
 * acceptance. BO reads it back for display rather than owning it.
 */
public record Store(String storeId, String name, String phone, String address, boolean active) {
}
