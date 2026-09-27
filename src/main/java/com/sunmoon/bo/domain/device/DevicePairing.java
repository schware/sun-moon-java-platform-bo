package com.sunmoon.bo.domain.device;

import java.util.List;
import java.util.Map;

/**
 * 기기 페어링 — 코드는 BO 가 만들지 않고 Device Server 에 받아 옵니다
 * (umbrella docs/design/store-app.md §3.2). 기기 신원의 주인은 연결을 쥔 쪽입니다.
 *
 * <p>답은 Device Server 의 모양 그대로 화면에 넘깁니다 — BO 가 다시 이름을
 * 붙이면 두 곳에서 같은 것을 다르게 부르게 됩니다.
 */
public interface DevicePairing {

    /** {@code {code, expiresAt}} */
    Map<String, Object> issueCode(Device device, String issuedBy);

    /** {@code {revoked}} */
    Map<String, Object> revoke(Device device);

    /** 기기별 {@code state}(PAIRED | CODE_ISSUED), {@code pairedAt}, {@code lastSeen}, {@code codeExpiresAt}. */
    List<Map<String, Object>> devices(String storeId);
}
