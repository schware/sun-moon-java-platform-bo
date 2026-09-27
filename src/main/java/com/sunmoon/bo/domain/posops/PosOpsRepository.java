package com.sunmoon.bo.domain.posops;

import java.time.LocalDate;

/**
 * POS-Server 를 읽고, 거는 일만 대신 보냅니다.
 *
 * <p>BO가 이 값들을 <b>소유하지 않습니다</b>. 개점 상태도, 단말이 지금
 * 어떤지도, 어느 장비가 무엇을 깔았는지도 전부 POS-Server 가 압니다.
 * BO에 한 벌 복사해 두면 화면이 빨라지는 대신 <b>두 곳이 서로 다른
 * 말을 하는 날</b>이 오고, 그때 어느 쪽이 맞는지 아무도 모릅니다.
 *
 * <p>쓰는 것이 셋뿐인 것도 그래서입니다 — 마스터 버전 올리기, 프로그램
 * 배포 등록, 공지 걸기. 셋 다 <b>사람이 BO에서 시작하는 일</b>이고,
 * 나머지는 전부 장비가 만들어 POS-Server 에 쌓은 사실입니다.
 */
public interface PosOpsRepository {

    // 읽기 --------------------------------------------------------------

    PosOps.BusinessStates businessStates(String storeId, LocalDate businessDate);

    PosOps.BusinessHistory businessHistory(String storeId, String posNo, LocalDate businessDate);

    PosOps.LiveCheck liveCheck(String storeId, LocalDate businessDate);

    PosOps.MasterPlans masterPlans(String storeId, String deviceType, String deviceNo);

    PosOps.MasterPendings masterPending(String storeId);

    PosOps.ProgramPlans programPlans(String storeId, String deviceType, String deviceNo);

    PosOps.ProgramStatus programStatus(String storeId);

    PosOps.Notices notices(String storeId);

    // 쓰기 --------------------------------------------------------------

    /** 마스터가 바뀌었습니다. 장비는 다음에 물어볼 때 변경분 대상이 됩니다. */
    long bumpMasterVersion(String storeId, String masterType);

    PosOps.ReleasePublished publishRelease(PosOps.ReleaseRequest request);

    PosOps.NoticePublished publishNotice(PosOps.NoticeRequest request);

    /** 실패해 있는 것들. {@code storeId} 가 비면 전 매장입니다. */
    PosOps.Reprocess reprocess(String storeId);

    /** 한 건을 다시 하게 겁니다. */
    PosOps.RetryOutcome retryOne(String kind, String storeId, String id);

    /** 그 종류를 전부 다시 하게 겁니다. */
    PosOps.RetriedAll retryAll(String kind, String storeId);
}
