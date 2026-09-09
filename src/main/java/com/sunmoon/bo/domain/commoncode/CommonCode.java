package com.sunmoon.bo.domain.commoncode;

/** One row of a shared reference table — e.g. (ORDER_STATUS, PENDING, "대기", 1). */
public record CommonCode(String groupCode, String code, String name, int sortOrder, boolean active) {
}
