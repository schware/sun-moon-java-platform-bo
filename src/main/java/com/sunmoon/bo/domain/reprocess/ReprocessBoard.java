package com.sunmoon.bo.domain.reprocess;

import com.sunmoon.bo.domain.posops.PosOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 재처리 화면 하나에 세 서비스를 (umbrella docs/design/reprocess-library.md §6).
 *
 * <p><b>한 곳이 안 닿아도 나머지는 보입니다.</b> 안 닿은 서비스는 {@link Board#unreachable} 에 이름으로 남습니다 —
 * 빈 목록으로 두면 "실패 0건" 과 구분이 안 되고, 화면이 "손볼 것 없음" 을 띄우게 됩니다(BO 매출 조회와 같은 원칙).
 */
public class ReprocessBoard {

    private static final Logger log = LoggerFactory.getLogger(ReprocessBoard.class);

    /** 서비스를 고르지 않은 "다시" 는 여기로 — 서비스 칸이 생기기 전 화면이 보내던 곳입니다. */
    public static final String DEFAULT_SERVICE = "POS-SERVER";

    /**
     * @param unreachable 읽지 못한 서비스. 비어 있어야 "손볼 것 없음" 이 참입니다.
     */
    public record Board(String storeId, List<Kind> kinds, List<String> unreachable) {
    }

    /** 서비스 칸이 붙은 종류 — 종류 이름(kind)은 서비스 안에서만 유일합니다. */
    public record Kind(String service, String kind, String label, boolean retryable, int count,
                       List<PosOps.FailedEntry> items) {
    }

    private final Map<String, ReprocessSource> sources = new LinkedHashMap<>();

    /** 순서가 화면의 순서입니다. */
    public ReprocessBoard(List<ReprocessSource> sources) {
        sources.forEach(source -> this.sources.put(source.service(), source));
    }

    public Board list(String storeId) {
        List<Kind> kinds = new ArrayList<>();
        List<String> unreachable = new ArrayList<>();
        for (ReprocessSource source : sources.values()) {
            try {
                PosOps.Reprocess answer = source.list(storeId);
                if (answer == null || answer.kinds() == null) {
                    continue;
                }
                for (PosOps.ReprocessKind kind : answer.kinds()) {
                    kinds.add(new Kind(source.service(), kind.kind(), kind.label(),
                            kind.retryable() == null || kind.retryable(), kind.count(),
                            kind.items() == null ? List.of() : kind.items()));
                }
            } catch (RuntimeException e) {
                // 닿지 않은 것만이 아니라 모양이 틀린 답도 여기로 — 한 서비스 때문에 화면 전체가 비면 안 됩니다.
                log.warn("재처리 목록을 {} 에서 읽지 못했습니다", source.service(), e);
                unreachable.add(source.service());
            }
        }
        return new Board(storeId == null ? "" : storeId, kinds, unreachable);
    }

    public PosOps.RetryOutcome retryOne(String service, String kind, String storeId, String id) {
        return source(service).retryOne(kind, storeId, id);
    }

    public PosOps.RetriedAll retryAll(String service, String kind, String storeId) {
        return source(service).retryAll(kind, storeId);
    }

    private ReprocessSource source(String service) {
        String name = service == null || service.isBlank() ? DEFAULT_SERVICE : service;
        ReprocessSource source = sources.get(name);
        if (source == null) {
            throw new IllegalArgumentException("그런 서비스가 없습니다: " + name);
        }
        return source;
    }
}
