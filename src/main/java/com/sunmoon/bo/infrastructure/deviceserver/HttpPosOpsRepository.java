package com.sunmoon.bo.infrastructure.deviceserver;

import com.sunmoon.bo.domain.UpstreamUnavailableException;
import com.sunmoon.bo.domain.posops.PosOps;
import com.sunmoon.bo.domain.posops.PosOpsRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * POS 운영 화면들이 읽는 곳 — Device Server(=POS-Server) 8087.
 *
 * <p>모든 메서드가 같은 모양입니다: 부르고, 못 닿으면
 * {@link UpstreamUnavailableException}. <b>빈 목록으로 뭉개지 않습니다</b> —
 * "오늘 개점한 단말이 없다"와 "POS-Server 가 안 보인다"는 운영자가 할
 * 일이 완전히 다릅니다. 앞은 전화를 걸 곳이 매장이고 뒤는 서버실입니다.
 *
 * <p>쓰기 셋도 여기 있습니다. BO가 그 값을 소유해서가 아니라, 사람이
 * BO 화면에서 시작하는 일이기 때문입니다 — 그래도 쌓이는 곳은 저쪽입니다.
 */
@Repository
public class HttpPosOpsRepository implements PosOpsRepository {

    private final RestClient client;

    public HttpPosOpsRepository(@Qualifier("deviceServerClient") RestClient client) {
        this.client = client;
    }

    @Override
    public PosOps.BusinessStates businessStates(String storeId, LocalDate businessDate) {
        return get(uri -> uri.path("/pos/business")
                        .queryParam("storeId", storeId)
                        .queryParam("businessDate", businessDate.toString()).build(),
                PosOps.BusinessStates.class);
    }

    @Override
    public PosOps.BusinessHistory businessHistory(String storeId, String posNo, LocalDate businessDate) {
        return get(uri -> uri.path("/pos/business/history")
                        .queryParam("storeId", storeId)
                        .queryParam("posNo", posNo)
                        .queryParam("businessDate", businessDate.toString()).build(),
                PosOps.BusinessHistory.class);
    }

    @Override
    public PosOps.LiveCheck liveCheck(String storeId, LocalDate businessDate) {
        return get(uri -> uri.path("/pos/live")
                        .queryParam("storeId", storeId)
                        .queryParam("businessDate", businessDate.toString()).build(),
                PosOps.LiveCheck.class);
    }

    /**
     * {@code issue} 를 붙이지 않습니다.
     *
     * <p>BO는 <b>보는 화면</b>입니다. 관제가 열릴 때마다 전송 기록이
     * 찍히면 한 번도 안 보낸 마스터가 방금 보낸 것처럼 보이고, 재시도
     * 횟수도 사람이 화면을 연 횟수가 됩니다. 실제 전송은 장비가 물어볼
     * 때 일어납니다.
     */
    @Override
    public PosOps.MasterPlans masterPlans(String storeId, String deviceType, String deviceNo) {
        return get(uri -> uri.path("/pos/masters")
                        .queryParam("storeId", storeId)
                        .queryParam("deviceType", deviceType)
                        .queryParam("deviceNo", deviceNo).build(),
                PosOps.MasterPlans.class);
    }

    @Override
    public PosOps.MasterPendings masterPending(String storeId) {
        return get(uri -> uri.path("/pos/masters/pending").queryParam("storeId", storeId).build(),
                PosOps.MasterPendings.class);
    }

    @Override
    public PosOps.ProgramPlans programPlans(String storeId, String deviceType, String deviceNo) {
        return get(uri -> uri.path("/pos/programs")
                        .queryParam("storeId", storeId)
                        .queryParam("deviceType", deviceType)
                        .queryParam("deviceNo", deviceNo).build(),
                PosOps.ProgramPlans.class);
    }

    @Override
    public PosOps.ProgramStatus programStatus(String storeId) {
        return get(uri -> uri.path("/pos/programs/status").queryParam("storeId", storeId).build(),
                PosOps.ProgramStatus.class);
    }

    @Override
    public PosOps.Notices notices(String storeId) {
        return get(uri -> {
            UriBuilder builder = uri.path("/pos/notices/all");
            if (storeId != null && !storeId.isBlank()) {
                builder.queryParam("storeId", storeId);
            }
            return builder.build();
        }, PosOps.Notices.class);
    }

    @Override
    public long bumpMasterVersion(String storeId, String masterType) {
        Map<?, ?> answer = post("/pos/masters/version",
                Map.of("storeId", storeId, "masterType", masterType), Map.class);
        Object version = answer == null ? null : answer.get("version");
        return version instanceof Number number ? number.longValue() : 0L;
    }

    @Override
    public PosOps.ReleasePublished publishRelease(PosOps.ReleaseRequest request) {
        return post("/pos/programs/release", request, PosOps.ReleasePublished.class);
    }

    @Override
    public PosOps.NoticePublished publishNotice(PosOps.NoticeRequest request) {
        return post("/pos/notices", request, PosOps.NoticePublished.class);
    }

    private <T> T get(Function<UriBuilder, java.net.URI> uri, Class<T> type) {
        try {
            return client.get().uri(uri).retrieve().body(type);
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("POS-SERVER", e);
        }
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return client.post().uri(path).body(body).retrieve().body(type);
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("POS-SERVER", e);
        }
    }

    /** 화면이 빈 목록을 그대로 그릴 수 있게 — null 은 화면에서 한 번 더 다뤄야 합니다. */
    static <T> List<T> orEmpty(List<T> rows) {
        return rows == null ? List.of() : rows;
    }
}
