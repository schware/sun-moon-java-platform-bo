package com.sunmoon.bo.domain.operator;

/**
 * BO screen registry. A code-defined enum, not a database table — the
 * screen list changes with deployments, not by an operator's own action.
 */
public enum Screen {
    BOARD,
    COMMON_CODE,
    DEVICE,
    MENU,
    /**
     * 결품 — 오늘 이 매장에 없는 메뉴. MENU와 따로 둡니다: 저쪽은 기준
     * 정보를 고치는 드문 일이고 이쪽은 장사 중에 몇 번씩 누르는 일이라,
     * 홀 담당에게 이것만 열어줄 수 있어야 합니다.
     */
    SOLD_OUT,
    STORE,
    /** 매장 배치도 — 테이블 기준 정보와 QR. */
    TABLE,
    /** 매출 조회. VIEW only — BO has no endpoint that writes 매출, by design. */
    SALES,
    /** 단말 현황. VIEW only — the connections belong to the Device Server. */
    TERMINAL,
    OPERATOR
}
