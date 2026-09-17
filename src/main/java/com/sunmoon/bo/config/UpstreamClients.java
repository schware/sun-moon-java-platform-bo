package com.sunmoon.bo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * BO's read-only upstreams.
 *
 * <p>BO owns 기준 정보 — 매장, 메뉴, 장비, 운영자. It does not own 매출 and
 * it does not own who is connected: those are facts that settle in Order
 * and in the Device Server, and BO asks for them rather than keeping its
 * own copy. A screen showing a stale duplicate of either would be worse
 * than one that says the upstream is unreachable.
 *
 * <p>Both clients have short timeouts on purpose. An operator screen that
 * hangs is indistinguishable from one that is broken, and every call these
 * make is a page render — nothing here is on a write path.
 */
@Configuration
public class UpstreamClients {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    /** The Order service — 매출 and 영업일자 live there (umbrella docs/adr/0006). */
    @Bean
    public RestClient orderClient(@Value("${bo.order.base-url}") String baseUrl) {
        return build(baseUrl);
    }

    /** The Device Server — the only thing that knows which terminals are actually connected. */
    @Bean
    public RestClient deviceServerClient(@Value("${bo.device-server.base-url}") String baseUrl) {
        return build(baseUrl);
    }

    /**
     * APP-SERVER — 손님 주문의 원장이 있는 곳 (umbrella docs/adr/0012).
     *
     * <p>매출과 마찬가지로 BO가 소유하지 않는 사실입니다. APP 주문이
     * 어느 경로로 들어왔고 어느 자리로 갔는지는 그 원장만 압니다 —
     * OMS는 매장이 받은 일감만 알고, 거절된 주문은 아예 모릅니다.
     */
    @Bean
    public RestClient appServerClient(@Value("${bo.app-server.base-url}") String baseUrl) {
        return build(baseUrl);
    }

    private static RestClient build(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
