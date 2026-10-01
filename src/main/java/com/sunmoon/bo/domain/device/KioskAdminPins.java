package com.sunmoon.bo.domain.device;

import java.util.Map;

/**
 * KIOSK 관리자 PIN (umbrella docs/design/store-app.md §5.3). BO 는 PIN 을 저장하지 않고
 * Device Server 에 넘깁니다 — 거기서 해시만 남습니다.
 */
public interface KioskAdminPins {

    /** {@code {set}} */
    Map<String, Object> set(Device device, String pin, String setBy);
}
