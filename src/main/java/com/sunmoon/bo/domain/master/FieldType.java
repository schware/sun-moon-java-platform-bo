package com.sunmoon.bo.domain.master;

/**
 * 기준정보 한 칸의 종류 (공통 CRUD).
 *
 * <p>일부러 <b>적습니다</b>. 자바 타입이나 DB 타입에서 뽑아낼 수도
 * 있지만, 그러면 화면이 "이 칸을 어떻게 그릴까"를 스키마에서 추측하게
 * 됩니다 — {@code VARCHAR(1)} 이 체크박스인지 코드인지는 스키마가
 * 모릅니다.
 */
public enum FieldType {
    TEXT,
    /** 여러 줄. 화면이 textarea 로 그립니다. */
    LONG_TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    /**
     * 공통코드에서 고릅니다. {@link MasterField#codeGroup()} 이 어느
     * 그룹인지 말합니다 — 값은 그 그룹의 코드입니다.
     *
     * <p>외래키로 묶지 않는 이유는 {@code devices.device_type} 과 같습니다:
     * 공통코드는 운영 중에 고쳐지고, 끊어진 코드 하나가 행 전체를 못 읽게
     * 만들면 안 됩니다.
     */
    CODE
}
