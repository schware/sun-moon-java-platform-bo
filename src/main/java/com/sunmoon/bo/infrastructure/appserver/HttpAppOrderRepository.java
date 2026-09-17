package com.sunmoon.bo.infrastructure.appserver;

import com.sunmoon.bo.domain.UpstreamUnavailableException;
import com.sunmoon.bo.domain.apporder.AppOrderRepository;
import com.sunmoon.bo.domain.apporder.AppOrderRow;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.time.LocalDate;
import java.util.List;

/**
 * APP 주문 원장을 APP-SERVER에서 HTTP로 읽습니다.
 *
 * <p>fdw 뷰가 아니라 HTTP인 이유: 이건 <b>기준 정보가 아니라 거래
 * 데이터</b>입니다. 메뉴나 매장은 사람이 가끔 고치는 것이라 뷰로 읽지만,
 * 주문은 계속 쌓이고 원장의 모양이 앞으로 바뀝니다 — 그 모양에 BO의
 * 뷰가 물려 있으면 APP-SERVER가 컬럼 하나 고칠 때마다 BO도 같이
 * 내려야 합니다.
 *
 * <p>APP-SERVER가 {@link AppOrderRow} 보다 많은 필드를 돌려줍니다.
 * Jackson 기본값이 모르는 속성을 무시하는 덕에 APP-SERVER가 모델을
 * 늘려도 이 화면이 안 죽습니다 — 기본값에 기대고 있다는 것을 알고
 * 있는 편이 낫습니다.
 */
@Repository
public class HttpAppOrderRepository implements AppOrderRepository {

    private static final ParameterizedTypeReference<List<AppOrderRow>> ROWS =
            new ParameterizedTypeReference<>() {};

    private final RestClient client;

    public HttpAppOrderRepository(@Qualifier("appServerClient") RestClient client) {
        this.client = client;
    }

    @Override
    public List<AppOrderRow> findByDay(LocalDate businessDate, String storeId, String channel) {
        try {
            List<AppOrderRow> rows = client.get()
                    .uri(uri -> {
                        UriBuilder builder = uri.path("/orders")
                                .queryParam("businessDate", businessDate.toString());
                        if (storeId != null && !storeId.isBlank()) {
                            builder.queryParam("storeId", storeId);
                        }
                        if (channel != null && !channel.isBlank()) {
                            builder.queryParam("channel", channel);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(ROWS);
            return rows == null ? List.of() : rows;
        } catch (RestClientException e) {
            // 빈 목록으로 뭉개지 않습니다 — "그 날 주문이 없다"와
            // "APP-SERVER가 안 보인다"는 운영자가 할 일이 다릅니다.
            throw new UpstreamUnavailableException("APP-SERVER", e);
        }
    }
}
