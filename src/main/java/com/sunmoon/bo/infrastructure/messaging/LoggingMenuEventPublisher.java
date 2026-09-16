package com.sunmoon.bo.infrastructure.messaging;

import com.sunmoon.bo.domain.soldout.MenuEvent;
import com.sunmoon.bo.domain.soldout.MenuEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Redis가 없는 기기(개발 PC, 테스트)에서 BO가 그냥 뜨게 하는 쪽. 형제
 * service들이 쓰는 것과 같은 모양입니다 — 빠진 bean을 보고 고르는 대신
 * 같은 property의 반대쪽을 봅니다. 그래야 둘 중 정확히 하나만 뜹니다.
 */
@Component
@ConditionalOnProperty(name = "bo.events.redis", havingValue = "false", matchIfMissing = true)
public class LoggingMenuEventPublisher implements MenuEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingMenuEventPublisher.class);

    @Override
    public void publish(MenuEvent event) {
        log.info("menu event (not published — Redis off): {} {}/{} {}",
                event.type(), event.storeId(), event.menuCode(), event.businessDate());
    }
}
