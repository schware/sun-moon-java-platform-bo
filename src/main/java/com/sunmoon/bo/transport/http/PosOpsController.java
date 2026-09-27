package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.posops.PosOps;
import com.sunmoon.bo.domain.posops.PosOpsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * POS 운영 관제 — 개점 현황, Live Check, 배포, 공지 (업무 정의서 §3.4~3.6,
 * umbrella {@code docs/adr/0013}).
 *
 * <p>여기의 모든 값은 <b>POS-Server 가 갖고 있습니다</b>. BO는 읽어서
 * 그리고, 사람이 시작하는 세 가지(마스터 버전 올리기, 프로그램 배포
 * 등록, 공지 걸기)만 대신 보냅니다.
 *
 * <p>권한이 넷으로 갈려 있습니다. 한 덩어리로 두면 "공지 한 줄 올리는
 * 사람"에게 프로그램 배포까지 열어야 합니다 — 매장에 깔릴 파일을 바꾸는
 * 일과 안내문을 붙이는 일은 같은 무게가 아닙니다.
 */
@RestController
@RequestMapping("/api/pos-ops")
@Tag(name = "PosOps", description = "POS 운영 관제 — POS-Server를 읽습니다")
public class PosOpsController {

    private final PosOpsRepository posOps;

    public PosOpsController(PosOpsRepository posOps) {
        this.posOps = posOps;
    }

    // ---- 개점 현황 (§3.4) ---------------------------------------------

    @GetMapping("/business")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('POS_BUSINESS_VIEW')")
    @Operation(summary = "단말별 개점·마감 현재 상태",
            description = "Order의 매장 개점/마감과 다릅니다 — 저쪽은 매장이 주문을 받는가이고, "
                    + "이쪽은 그 단말이 매출을 올릴 수 있는가입니다.")
    @ApiResponse(responseCode = "502", description = "POS-Server에 연결하지 못했습니다")
    public PosOps.BusinessStates business(
            @RequestParam String storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return posOps.businessStates(storeId, businessDate);
    }

    @GetMapping("/business/history")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('POS_BUSINESS_VIEW')")
    @Operation(summary = "개점·마감 이력",
            description = "무시된 전문(IGNORED_STALE)까지 전부 나옵니다. "
                    + "\"이 POS는 왜 마감으로 안 바뀌었나\"에 답할 수 있는 유일한 자리입니다.")
    public PosOps.BusinessHistory businessHistory(
            @RequestParam String storeId,
            @RequestParam String posNo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return posOps.businessHistory(storeId, posNo, businessDate);
    }

    // ---- Live Check (§3.5) --------------------------------------------

    @GetMapping("/live")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('POS_LIVE_VIEW')")
    @Operation(summary = "POS Live Check 5종",
            description = "연결·프로그램·영업·장비·데이터를 따로 답합니다. "
                    + "연결 상태만으로 정상 영업 여부를 판단하지 않습니다.")
    public PosOps.LiveCheck live(
            @RequestParam String storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return posOps.liveCheck(storeId, businessDate);
    }

    // ---- 배포 (§3.5 프로그램, §3.6 마스터) ------------------------------

    @GetMapping("/masters")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('POS_DEPLOY_VIEW')")
    @Operation(summary = "장비 하나의 마스터 배포 계획",
            description = "읽기만 합니다 — 화면을 연 것이 전송으로 기록되면 "
                    + "한 번도 안 보낸 마스터가 방금 보낸 것처럼 보입니다.")
    public PosOps.MasterPlans masters(
            @RequestParam String storeId,
            @RequestParam String deviceType,
            @RequestParam String deviceNo) {
        return posOps.masterPlans(storeId, deviceType, deviceNo);
    }

    @GetMapping("/masters/pending")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('POS_DEPLOY_VIEW')")
    @Operation(summary = "마스터 재전송 대상", description = "아직 답이 없는 것과 실패한 것.")
    public PosOps.MasterPendings masterPending(@RequestParam String storeId) {
        return posOps.masterPending(storeId);
    }

    @PostMapping("/masters/version")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('POS_DEPLOY_SAVE')")
    @Operation(summary = "마스터 버전 올리기",
            description = "버전만 올립니다. 밀어붙이지 않는 이유는 꺼져 있는 장비가 있기 때문이고, "
                    + "그 장비는 켜질 때 알아서 뒤처진 것을 받습니다.")
    public PosOps.MasterPlan bump(@RequestParam String storeId, @RequestParam String masterType) {
        long version = posOps.bumpMasterVersion(storeId, masterType);
        return new PosOps.MasterPlan(masterType, masterType, version, null, "PUBLISHED", null);
    }

    @GetMapping("/programs")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('POS_DEPLOY_VIEW')")
    @Operation(summary = "장비 하나의 프로그램 배포 대상")
    public PosOps.ProgramPlans programs(
            @RequestParam String storeId,
            @RequestParam String deviceType,
            @RequestParam String deviceNo) {
        return posOps.programPlans(storeId, deviceType, deviceNo);
    }

    @GetMapping("/programs/status")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('POS_DEPLOY_VIEW')")
    @Operation(summary = "장비별 설치 현황과 배포 이력")
    public PosOps.ProgramStatus programStatus(@RequestParam String storeId) {
        return posOps.programStatus(storeId);
    }

    /**
     * 프로그램 배포 등록.
     *
     * <p>{@code CREATE} 권한입니다. 마스터 버전 올리기({@code SAVE})와
     * 가른 이유: 저쪽은 이미 있는 것을 다시 내리는 일이고, 이쪽은
     * <b>매장 장비에 깔릴 파일을 새로 지정하는</b> 일입니다.
     */
    @PostMapping("/programs/release")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('POS_DEPLOY_CREATE')")
    @Operation(summary = "프로그램 배포 등록",
            description = "storeId 를 비우면 전 매장입니다. 매장을 지정하면 그 매장이 "
                    + "전 매장 배포를 이깁니다 — 한 곳만 먼저 올려 보는 것이 정상 모양입니다.")
    public PosOps.ReleasePublished release(@RequestBody PosOps.ReleaseRequest request) {
        return posOps.publishRelease(request);
    }

    // ---- 공지·긴급 메시지 (§3.5) ---------------------------------------

    @GetMapping("/notices")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('POS_NOTICE_VIEW')")
    @Operation(summary = "단말 공지·긴급 메시지",
            description = "지난 것까지 전부. 긴급은 몇 대가 확인했는지도 함께 나옵니다.")
    public PosOps.Notices notices(@RequestParam(required = false) String storeId) {
        return posOps.notices(storeId);
    }

    /**
     * 공지 또는 긴급 메시지를 겁니다.
     *
     * <p>누가 걸었는지를 <b>여기서</b> 채웁니다. 화면이 보내는 값을
     * 믿으면 아무 이름이나 적을 수 있고, 긴급 메시지는 나중에 "누가
     * 이걸 보냈나"를 반드시 묻게 되는 종류의 글입니다.
     */
    @PostMapping("/notices")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('POS_NOTICE_CREATE')")
    @Operation(summary = "공지·긴급 메시지 등록",
            description = "urgent 면 지금 붙어 있는 단말에 바로 밀어 넣고, 답의 pushed 가 그 대수입니다. "
                    + "0이라고 실패가 아닙니다 — 꺼져 있던 장비는 켜질 때 조회로 봅니다.")
    public PosOps.NoticePublished publish(@RequestBody PosOps.NoticeRequest request,
                                          Authentication who) {
        return posOps.publishNotice(new PosOps.NoticeRequest(
                request.storeId(), request.deviceType(), request.title(), request.body(),
                request.urgent(), request.startsAt(), request.endsAt(),
                who == null ? "bo" : who.getName()));
    }

    // ---- 재처리 (§5.8, §10) -------------------------------------------

    @GetMapping("/reprocess")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('REPROCESS_VIEW')")
    @Operation(summary = "지금 손봐야 할 것",
            description = "매출 분해 · 마스터 적용 · 프로그램 설치의 실패가 종류별로 나옵니다. "
                    + "실패가 없는 종류도 count 0 으로 나옵니다 — 화면이 \"전부 정상\" 을 그릴 수 "
                    + "있어야 합니다. storeId 를 비우면 전 매장입니다.")
    @ApiResponse(responseCode = "502", description = "POS-Server에 연결하지 못했습니다")
    public PosOps.Reprocess reprocess(@RequestParam(required = false) String storeId) {
        return posOps.reprocess(storeId);
    }

    /**
     * 한 건을 다시 하게 겁니다.
     *
     * <p><b>누가 눌렀는지를 여기서 남깁니다.</b> POS-Server 의 재처리는
     * 누가 눌렀는지를 받지 않습니다 — 저쪽은 장비와 매출의 사실만 갖고,
     * 사람은 BO 가 압니다. 같은 건을 세 번째 되돌리는 사람이 누구인지는
     * 나중에 반드시 묻게 됩니다.
     */
    @PostMapping("/reprocess/one")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('REPROCESS_SAVE')")
    @Operation(summary = "한 건 재처리",
            description = "done 이 거짓이면 걸어 둔 것이지 다시 한 것이 아닙니다 — 매출은 다음 "
                    + "배치(30초 이내)에, 마스터·프로그램은 장비가 다음에 물어볼 때 처리됩니다.")
    public PosOps.RetryOutcome retryOne(@RequestParam String kind,
                                        @RequestParam String id,
                                        @RequestParam(required = false) String storeId,
                                        Authentication who) {
        PosOps.RetryOutcome outcome = posOps.retryOne(kind, storeId, id);
        log.info("재처리 {} #{} — {} (by {})", kind, id, outcome.message(),
                who == null ? "?" : who.getName());
        return outcome;
    }

    @PostMapping("/reprocess/all")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('REPROCESS_SAVE')")
    @Operation(summary = "그 종류 전부 재처리",
            description = "그 종류의 실패를 전부 다시 하게 겁니다. 규칙을 고친 뒤 한꺼번에 "
                    + "다시 푸는 경우가 이것입니다.")
    public PosOps.RetriedAll retryAll(@RequestParam String kind,
                                      @RequestParam(required = false) String storeId,
                                      Authentication who) {
        PosOps.RetriedAll retried = posOps.retryAll(kind, storeId);
        log.info("재처리 전부 {} 매장={} — 건 {} / 실패 {} (by {})", kind,
                storeId == null ? "전체" : storeId, retried.queued(), retried.failed(),
                who == null ? "?" : who.getName());
        return retried;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PosOpsController.class);
}
