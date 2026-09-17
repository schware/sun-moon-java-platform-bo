package com.sunmoon.bo.domain.apporder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * APP-SERVER 원장에서 읽어 온 주문 한 건 (umbrella {@code docs/adr/0012}).
 *
 * <p>{@code channel} 이 이 화면의 요점입니다. 전에는 BO가 "테이블 번호가
 * 있는가"로 QR 주문을 짐작했는데, 그건 <b>도착지</b>이지 유입 경로가
 * 아닙니다 — 테이블 없는 QR이 생기는 날 그 짐작이 틀립니다.
 *
 * <p>{@code status} 도 OMS의 상태가 아닙니다. 손님의 요청이 어떻게
 * 됐는가이고, 그래서 <b>OMS가 거절한 주문도 여기 나옵니다</b> — 매출
 * 조회에는 아예 없는 것들입니다.
 *
 * <p>Order의 {@link com.sunmoon.bo.domain.sales.SalesOrder} 와 나란히
 * 두되 합치지 않습니다: 같은 주문을 서로 다른 쪽에서 본 기록이고,
 * 한 record 로 뭉치면 어느 쪽이 사실인지 알 수 없게 됩니다.
 */
public record AppOrderRow(
        Long id,
        String orderNo,
        String storeId,
        LocalDate businessDate,
        String channel,
        String type,
        String status,
        Integer floor,
        String tableNo,
        BigDecimal totalAmount,
        String pickupNo,
        List<Line> lines,
        Instant placedAt) {

    public record Line(int detailSeq, String menuCode, String menuName,
                       int quantity, BigDecimal unitPrice, BigDecimal detailAmount) {
    }

    /** 첫 줄의 메뉴 이름. 장바구니가 없어 지금은 한 줄뿐입니다. */
    public String firstMenuName() {
        return lines == null || lines.isEmpty() ? "" : lines.get(0).menuName();
    }
}
