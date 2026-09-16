package com.sunmoon.bo.domain.soldout;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 오늘 이 매장에서 이 메뉴가 <b>없다</b>는 선언.
 *
 * <p>메뉴를 내리는 것(= {@code menus.active = false})과 다릅니다. 저쪽은
 * "이 매장 계열에서 파는 물건이 아니다"이고, 이쪽은 "오늘 재료가 떨어졌다"
 * 입니다. 그래서 키에 판매일자가 들어갑니다 — 내일이면 저절로 풀립니다.
 *
 * <p>행이 있으면 결품입니다. 플래그를 들고 있지 않은 이유는, {@code false}
 * 행을 남기는 순간 "안 정했다"와 "판매한다"가 같은 모양이 되고 그 구분을
 * 쓰는 곳이 없기 때문입니다. 풀면 지웁니다.
 */
public record SoldOut(
        String storeId,
        String menuCode,
        LocalDate businessDate,
        Instant declaredAt,
        String declaredBy) {
}
