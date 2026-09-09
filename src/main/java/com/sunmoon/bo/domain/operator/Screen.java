package com.sunmoon.bo.domain.operator;

/**
 * BO screen registry. A code-defined enum, not a database table — the
 * screen list changes with deployments, not by an operator's own action.
 */
public enum Screen {
    BOARD,
    COMMON_CODE,
    DEVICE,
    OPERATOR
}
