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
    STORE,
    /** 매장 배치도 — 테이블 기준 정보와 QR. */
    TABLE,
    /** 매출 조회. VIEW only — BO has no endpoint that writes 매출, by design. */
    SALES,
    /** 단말 현황. VIEW only — the connections belong to the Device Server. */
    TERMINAL,
    OPERATOR
}
