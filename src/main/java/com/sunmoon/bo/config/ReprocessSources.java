package com.sunmoon.bo.config;

import com.sunmoon.bo.domain.reprocess.ReprocessBoard;
import com.sunmoon.bo.infrastructure.reprocess.HttpReprocessSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 재처리 화면이 읽는 세 서비스. 순서가 화면의 순서입니다.
 *
 * <p>POS-SERVER 는 관리 토큰이 붙은 클라이언트로 갑니다(Device Server 의 ApiGuard). Order·APP-SERVER 는 LAN 전용이라
 * 토큰이 없습니다 — BO 의 다른 호출과 같습니다.
 */
@Configuration
public class ReprocessSources {

    @Bean
    public ReprocessBoard reprocessBoard(@Qualifier("deviceServerClient") RestClient deviceServer,
                                         @Qualifier("orderClient") RestClient order,
                                         @Qualifier("appServerClient") RestClient appServer) {
        return new ReprocessBoard(List.of(
                new HttpReprocessSource(ReprocessBoard.DEFAULT_SERVICE, deviceServer),
                new HttpReprocessSource("ORDER", order),
                new HttpReprocessSource("APP-SERVER", appServer)));
    }
}
