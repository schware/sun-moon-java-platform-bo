package com.sunmoon.bo.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunmoon.bo.domain.soldout.MenuEvent;
import com.sunmoon.bo.domain.soldout.MenuEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 기준 정보 변경을 {@code menu-events} Redis 채널로 흘립니다. Device
 * Server가 받아서 매장 단말에 밀어 줍니다.
 *
 * <p>BO가 단말을 직접 부르지 않는 이유는 Order가 그러지 않는 이유와
 * 같습니다 — 누가 접속해 있는지는 Device Server만 압니다. BO는 "바뀌었다"를
 * 말할 뿐이고, 그걸 누가 봐야 하는지는 단말을 맡은 쪽이 정합니다.
 *
 * <p>Pub/Sub이라 꺼져 있던 단말은 이 이벤트를 못 받습니다. 그래도 맞습니다:
 * 단말은 켜질 때 목록을 다시 읽고, 결품은 그 목록 안에 들어 있습니다.
 * 이벤트는 <b>빨리</b> 알리는 수단이지 유일한 경로가 아닙니다.
 */
@Component
@ConditionalOnProperty(name = "bo.events.redis", havingValue = "true")
public class RedisMenuEventPublisher implements MenuEventPublisher {

    public static final String CHANNEL = "menu-events";

    private static final Logger log = LoggerFactory.getLogger(RedisMenuEventPublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisMenuEventPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(MenuEvent event) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
            log.debug("published to {}: {} {}/{}", CHANNEL, event.type(), event.storeId(), event.menuCode());
        } catch (Exception e) {
            // 삼킵니다. 결품은 이미 DB에 들어갔고, 그게 진실입니다.
            // 알림이 못 갔다고 해서 방금 한 선언을 되돌리는 것은 더 나쁩니다
            // — 단말은 다음 조회에서 어차피 따라잡습니다.
            log.warn("결품 이벤트를 보내지 못했습니다 ({} {}/{}) — 단말은 다음 조회에서 따라잡습니다",
                    event.type(), event.storeId(), event.menuCode(), e);
        }
    }
}
