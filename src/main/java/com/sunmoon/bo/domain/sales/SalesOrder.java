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
 * (see {@code HttpSalesRepository}), so Order can grow 회원번호 without BO
 * being redeployed to keep working.
 *
 * <p>도착지·수령번호·QR번호·거래번호가 여기 있는 것은 APP 매출 조회
 * 때문입니다 — 그 화면이 묻는 것은 "얼마를 팔았나"가 아니라 "그 주문이
 * 몇 번 자리로 갔나"입니다. 값 자체는 Order가 이미 주고 있었고, 이
 * record가 받아 적지 않아 버리고 있었을 뿐입니다.
 */
public record SalesOrder(
        Long id,
        String storeId,
        String customerId,
        String menuName,
        BigDecimal amount,
        String status,
        String acceptedBy,
        /**
         * 도착지 — "이 메뉴를 저 자리로". 테이블 QR로 들어온 주문에만
         * 있고, 매장 선택 화면이나 포장·배달은 null 입니다. null이
         * "모른다"가 아니라 "테이블이 아니다"라는 뜻입니다.
         */
        Integer floor,
        String tableNo,
        /** 부르는 번호. DID에 뜨고 손님이 보고 온 값입니다. */
        String pickupNo,
        /** 이 주문이 들어온 테이블 QR의 코드. 자리 번호를 바꿔도 그대로입니다. */
        String qrCode,
        /** 거래번호 — 매출 키의 마지막 자리. 수락 전에는 null 입니다. */
        String tranNo,
        LocalDate businessDate,
        Instant placedAt,
        Instant updatedAt) {

    /** 테이블 QR로 들어온 주문인가. 아니면 매장 선택 화면에서 온 것입니다. */
    public boolean fromTable() {
        return tableNo != null;
    }
}
