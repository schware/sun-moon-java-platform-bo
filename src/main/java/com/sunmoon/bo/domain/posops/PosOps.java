package com.sunmoon.bo.domain.posops;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * POS-Server 가 답하는 모양들 (업무 정의서 §3.4~3.6, umbrella docs/adr/0013).
 *
 * <p>한 파일에 모아 둔 이유: BO는 이 값들을 <b>만들지 않습니다</b>.
 * 전부 POS-Server 가 갖고 있고 BO는 화면에 그리기만 합니다. 도메인마다
 * 패키지를 파면 "BO가 이 개념을 소유한다"고 읽히는데, 사실이 아닙니다.
 *
 * <p>필드가 POS-Server 의 답보다 적습니다. Jackson 이 모르는 속성을
 * 무시하는 덕에 저쪽이 늘려도 이 화면이 안 죽습니다 — 기본값에 기대고
 * 있다는 것을 알고 쓰는 편이 낫습니다.
 */
public final class PosOps {

    /** 단말 하나의 개점·마감 현재 상태 (§3.4). */
    public record BusinessState(String posNo, String status, String lastEvent,
                                int openCount, Instant eventAt, Instant receivedAt) {
    }

    public record BusinessStates(String storeId, String businessDate, List<BusinessState> terminals) {
    }

    /**
     * 개점·마감 이력 한 줄.
     *
     * @param result APPLIED / DUPLICATE / IGNORED_STALE. 무시된 전문도
     *               남아 있어서 "이 POS는 왜 마감으로 안 바뀌었나"에
     *               답할 수 있습니다.
     */
    public record BusinessHistoryEntry(int seq, String event, String result, String message,
                                       Instant eventAt, Instant receivedAt, String irtId) {
    }

    public record BusinessHistory(String storeId, String posNo, String businessDate,
                                  List<BusinessHistoryEntry> history) {
    }

    /**
     * Live Check 한 단말 (§3.5).
     *
     * @param axes       CONNECTION / APPLICATION / BUSINESS / DEVICE / DATA →
     *                   OK / WARN / DOWN / UNKNOWN. {@code Map} 인 이유는
     *                   축이 늘 때 BO를 함께 내리지 않기 위해서입니다.
     * @param mismatches 축끼리 아귀가 안 맞는 대목. 화면에서 제일 먼저
     *                   읽어야 할 줄입니다.
     */
    public record LiveRow(String posNo, Map<String, String> axes, String overall, boolean normal,
                          String programVersion, String dbVersion, Integer clockDriftSeconds,
                          Instant lastReportedAt, List<String> mismatches) {
    }

    public record LiveCheck(String storeId, String businessDate, List<LiveRow> terminals) {
    }

    /** 장비 하나 · 마스터 하나의 배포 계획 (§3.6). */
    public record MasterPlan(String masterType, String label, long currentVersion,
                             Long appliedVersion, String mode, String lastError) {
    }

    public record MasterPlans(String storeId, String deviceType, String deviceNo,
                              boolean issued, List<MasterPlan> masters) {
    }

    public record MasterPending(String deviceType, String deviceNo, String masterType,
                                Long appliedVersion, String status, int attempts,
                                String lastError, Instant sentAt) {
    }

    public record MasterPendings(String storeId, List<MasterPending> pending) {
    }

    /** 프로그램 배포 대상 (§3.5). */
    public record ProgramPlan(String program, String targetVersion, String installedVersion,
                              boolean upToDate, String packageUrl, String checksum,
                              boolean mandatory, String status, String lastError) {
    }

    public record ProgramPlans(String storeId, String deviceType, String deviceNo,
                               boolean issued, List<ProgramPlan> programs) {
    }

    public record ProgramInstall(String deviceType, String deviceNo, String program,
                                 String installedVersion, String targetVersion, String status,
                                 int attempts, String lastError,
                                 Instant offeredAt, Instant installedAt) {
    }

    public record ProgramRelease(Long id, String scope, String deviceType, String program,
                                 String version, boolean mandatory, String note,
                                 Instant releasedAt) {
    }

    public record ProgramStatus(String storeId, List<ProgramInstall> installs,
                                List<ProgramRelease> releases) {
    }

    /**
     * 공지 또는 긴급 메시지 (§3.5).
     *
     * @param acknowledged 긴급일 때만 채워집니다. 일반 공지는 아무도
     *                     확인하지 않고, 0이라는 숫자가 화면에 붙으면
     *                     문제처럼 보입니다.
     */
    public record Notice(Long id, String scope, String deviceType, String title, String body,
                         boolean urgent, Instant startsAt, Instant endsAt, String createdBy,
                         Boolean active, Integer acknowledged, List<String> acknowledgedBy) {
    }

    public record Notices(List<Notice> notices) {
    }

    /**
     * 공지를 걸 때 BO가 보내는 것.
     *
     * @param createdBy 화면이 보낸 값은 <b>버려집니다</b>. 컨트롤러가
     *                  로그인한 운영자의 이름으로 덮어씁니다 — 긴급
     *                  메시지는 나중에 "누가 이걸 보냈나"를 반드시 묻게
     *                  되는 종류의 글입니다.
     */
    public record NoticeRequest(String storeId, String deviceType, String title, String body,
                                boolean urgent, Instant startsAt, Instant endsAt, String createdBy) {
    }

    /** @param pushed 실제로 밀어 넣은 단말 수. 0이라고 실패가 아닙니다 — 켜지면 조회로 봅니다. */
    public record NoticePublished(long id, boolean urgent, int pushed) {
    }

    /** 프로그램 배포를 낼 때 BO가 보내는 것. */
    public record ReleaseRequest(String storeId, String deviceType, String program, String version,
                                 String packageUrl, String checksum, boolean mandatory, String note) {
    }

    public record ReleasePublished(long id, String program, String version, String scope) {
    }

    // ---- 재처리 (§5.8, §10) -------------------------------------------

    /**
     * 지금 손봐야 할 것 전부. 종류마다 한 칸입니다.
     *
     * <p><b>실패가 없는 종류도 나옵니다</b>(count 0). 화면이 "전부 정상"
     * 을 그릴 수 있어야 하고, 칸이 사라지면 "없다" 와 "안 불러왔다" 가
     * 같은 그림이 됩니다.
     */
    public record Reprocess(String storeId, List<ReprocessKind> kinds) {
    }

    /**
     * @param kind  {@code SALE} · {@code ETC} · {@code MASTER} · {@code PROGRAM} (POS-SERVER),
     *              {@code ORDER_EVENT} (Order) …. 각 서비스가 정하고 BO 는 그대로 씁니다 —
     *              여기서 목록을 고정하면 저쪽에 종류가 늘 때 BO 를 다시 배포해야 합니다.
     *              Order·APP-SERVER 도 같은 모양으로 답해서 이 타입으로 읽습니다
     *              (domain/reprocess, 2026-10-03).
     */
    public record ReprocessKind(String kind, String label, int count, List<FailedEntry> items) {
    }

    /**
     * @param summary 그 건을 가리키는 말 — "2026-09-24 pos-01 / 000503".
     *                내부 id 는 화면이 되돌릴 때만 씁니다.
     * @param error   실패 사유. <b>그대로</b> 보여줍니다 — 요약하면 다시
     *                돌려도 되는지 판단할 근거가 사라집니다.
     */
    public record FailedEntry(String id, String storeId, String summary, String error,
                              int attempts, Instant failedAt) {
    }

    /**
     * @param accepted 그 건이 있었고 다시 할 대상이 됐는가.
     * @param done     POS-Server 가 <b>지금</b> 다시 했는가. 거짓이면 걸어
     *                 뒀을 뿐이고 실제 처리는 나중입니다 — 매출은 다음
     *                 배치에서, 마스터·프로그램은 장비가 물어볼 때.
     */
    public record RetryOutcome(String kind, String id, boolean accepted, boolean done, String message) {
    }

    public record RetriedAll(String kind, int queued, int failed) {
    }

    private PosOps() {
    }
}
