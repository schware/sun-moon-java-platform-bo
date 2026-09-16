package com.sunmoon.bo.domain.soldout;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 기준 정보가 바뀌었다는 알림. 지금은 결품 두 가지뿐입니다.
 *
 * <p>내용을 싣지 않고 <b>바뀌었다는 사실만</b> 보냅니다. 받는 쪽(단말)은
 * 이걸 신호로 목록을 다시 읽습니다 — 이 계열이 주문 이벤트에서 이미 쓰는
 * 모양이고("소켓은 바뀌었다고만 말하고, 무엇이 참인지는 물어본다"), 그래야
 * 이벤트를 한 번 놓친 단말도 다음 신호에서 따라잡습니다. 델타를 싣기
 * 시작하면 놓친 단말이 영원히 어긋납니다.
 *
 * <p>{@code storeId}는 라우팅에 씁니다. Device Server가 이 값으로 어느
 * 매장의 단말에 밀지 결정합니다.
 */
public record MenuEvent(
        String type,
        String storeId,
        String menuCode,
        LocalDate businessDate,
        Instant at) {

    public static final String SOLD_OUT = "MENU_SOLD_OUT";
    public static final String RESTORED = "MENU_RESTORED";

    public static MenuEvent soldOut(String storeId, String menuCode, LocalDate businessDate) {
        return new MenuEvent(SOLD_OUT, storeId, menuCode, businessDate, Instant.now());
    }

    public static MenuEvent restored(String storeId, String menuCode, LocalDate businessDate) {
        return new MenuEvent(RESTORED, storeId, menuCode, businessDate, Instant.now());
    }
}
