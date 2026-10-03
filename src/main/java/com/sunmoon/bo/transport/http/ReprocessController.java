package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.posops.PosOps;
import com.sunmoon.bo.domain.reprocess.ReprocessBoard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 재처리 (업무 정의서 §5.8, §10) — POS-SERVER · Order · APP-SERVER 의 실패를 한 화면에.
 *
 * <p>2026-10-03 전에는 {@code /api/pos-ops/reprocess} 에서 POS-SERVER 하나만 읽었습니다. 세 서비스가 같은 모양으로
 * 답하게 되면서(sun-moon-java-common) 이 주소로 옮겼습니다 (umbrella docs/design/reprocess-library.md §6).
 */
@RestController
@RequestMapping("/api/reprocess")
@Tag(name = "Reprocess", description = "실패 재처리 — POS-SERVER · Order · APP-SERVER 를 읽습니다")
public class ReprocessController {

    private static final Logger log = LoggerFactory.getLogger(ReprocessController.class);

    private final ReprocessBoard board;

    public ReprocessController(ReprocessBoard board) {
        this.board = board;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('REPROCESS_VIEW')")
    @Operation(summary = "지금 손봐야 할 것",
            description = "세 서비스의 실패가 종류별로, 서비스 이름과 함께 나옵니다. 실패가 없는 종류도 count 0 으로 "
                    + "나옵니다. 읽지 못한 서비스는 unreachable 에 이름으로 남습니다 — 그 서비스의 실패가 0건이라는 "
                    + "뜻이 아닙니다. storeId 를 비우면 전 매장입니다.")
    public ReprocessBoard.Board list(@RequestParam(required = false) String storeId) {
        return board.list(storeId);
    }

    /**
     * 한 건을 다시 하게 겁니다.
     *
     * <p><b>누가 눌렀는지를 여기서 남깁니다.</b> 서비스들의 재처리는 누가 눌렀는지를 받지 않습니다 — 저쪽은 장비와
     * 주문의 사실만 갖고, 사람은 BO 가 압니다. 같은 건을 세 번째 되돌리는 사람이 누구인지는 나중에 반드시 묻게 됩니다.
     */
    @PostMapping("/one")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('REPROCESS_SAVE')")
    @Operation(summary = "한 건 재처리",
            description = "service 를 비우면 POS-SERVER 입니다. done 이 거짓이면 걸어 둔 것이지 다시 한 것이 아닙니다 — "
                    + "매출은 다음 배치(30초 이내)에, 마스터·프로그램은 장비가 다음에 물어볼 때 처리됩니다.")
    @ApiResponse(responseCode = "502", description = "그 서비스에 연결하지 못했습니다")
    public PosOps.RetryOutcome retryOne(@RequestParam(required = false) String service,
                                        @RequestParam String kind,
                                        @RequestParam String id,
                                        @RequestParam(required = false) String storeId,
                                        Authentication who) {
        PosOps.RetryOutcome outcome = board.retryOne(service, kind, storeId, id);
        log.info("재처리 {}/{} #{} — {} (by {})", service, kind, id, outcome == null ? "?" : outcome.message(),
                who == null ? "?" : who.getName());
        return outcome;
    }

    @PostMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('REPROCESS_SAVE')")
    @Operation(summary = "그 종류 전부 재처리",
            description = "그 종류의 실패를 전부 다시 하게 겁니다. 규칙을 고친 뒤 한꺼번에 다시 푸는 경우가 이것입니다.")
    @ApiResponse(responseCode = "502", description = "그 서비스에 연결하지 못했습니다")
    public PosOps.RetriedAll retryAll(@RequestParam(required = false) String service,
                                      @RequestParam String kind,
                                      @RequestParam(required = false) String storeId,
                                      Authentication who) {
        PosOps.RetriedAll retried = board.retryAll(service, kind, storeId);
        log.info("재처리 전부 {}/{} 매장={} — 건 {} / 실패 {} (by {})", service, kind,
                storeId == null ? "전체" : storeId,
                retried == null ? 0 : retried.queued(), retried == null ? 0 : retried.failed(),
                who == null ? "?" : who.getName());
        return retried;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> onUnknownService(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
