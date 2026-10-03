package com.sunmoon.bo.infrastructure.reprocess;

import com.sunmoon.bo.domain.UpstreamUnavailableException;
import com.sunmoon.bo.domain.posops.PosOps;
import com.sunmoon.bo.domain.reprocess.ReprocessSource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.function.Function;

/**
 * {@code /reprocess} 를 가진 서비스 하나를 HTTP 로 — 셋 다 이 클래스입니다.
 *
 * <p>재처리는 <b>본문이 아니라 쿼리</b>로 받습니다 — 저쪽 주소 모양을 그대로 따릅니다. 여기서 본문으로 바꿔 보내면
 * 중계가 말을 옮기는 것이 아니라 번역하게 되고, 번역은 틀립니다.
 */
public class HttpReprocessSource implements ReprocessSource {

    private final String service;
    private final RestClient client;

    public HttpReprocessSource(String service, RestClient client) {
        this.service = service;
        this.client = client;
    }

    @Override
    public String service() {
        return service;
    }

    @Override
    public PosOps.Reprocess list(String storeId) {
        return call(() -> client.get().uri(uri -> withStore(uri.path("/reprocess"), storeId))
                .retrieve().body(PosOps.Reprocess.class));
    }

    @Override
    public PosOps.RetryOutcome retryOne(String kind, String storeId, String id) {
        return post(uri -> withStore(uri.path("/reprocess/one").queryParam("kind", kind).queryParam("id", id), storeId),
                PosOps.RetryOutcome.class);
    }

    @Override
    public PosOps.RetriedAll retryAll(String kind, String storeId) {
        return post(uri -> withStore(uri.path("/reprocess/all").queryParam("kind", kind), storeId),
                PosOps.RetriedAll.class);
    }

    /** 비어 있으면 보내지 않습니다 — 저쪽이 "전 매장" 으로 읽습니다. */
    private static URI withStore(UriBuilder uri, String storeId) {
        if (storeId != null && !storeId.isBlank()) {
            uri.queryParam("storeId", storeId);
        }
        return uri.build();
    }

    private <T> T post(Function<UriBuilder, URI> uri, Class<T> type) {
        return call(() -> client.post().uri(uri).retrieve().body(type));
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException(service, e);
        }
    }
}
