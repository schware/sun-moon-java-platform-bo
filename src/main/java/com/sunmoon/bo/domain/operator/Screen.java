package com.sunmoon.bo.domain.operator;

/**
 * BO screen registry. A code-defined enum, not a database table — the
 * screen list changes with deployments, not by an operator's own action.
 */
public enum Screen {
    BOARD,
    COMMON_CODE,
    DEVICE,
    MENU,
    /**
     * 결품 — 오늘 이 매장에 없는 메뉴. MENU와 따로 둡니다: 저쪽은 기준
     * 정보를 고치는 드문 일이고 이쪽은 장사 중에 몇 번씩 누르는 일이라,
     * 홀 담당에게 이것만 열어줄 수 있어야 합니다.
     */
    SOLD_OUT,
    STORE,
    /** 매장 배치도 — 테이블 기준 정보와 QR. */
    TABLE,
    /** 매출 조회. VIEW only — BO has no endpoint that writes 매출, by design. */
    SALES,
    /**
     * APP 매출 조회 — 테이블 QR과 손님 앱으로 들어온 주문을 <b>어디서
     * 와서 어디로 갔는가</b>로 보는 화면. SALES와 따로인 이유는 묻는
     * 질문이 다르기 때문입니다: 저쪽은 하루에 얼마를 팔았나이고, 이쪽은
     * 그 주문이 몇 번 자리로 갔고 수령번호가 무엇이었나입니다.
     * VIEW only — 여기도 쓰는 endpoint가 없습니다.
     */
    APP_SALES,
    /** 단말 현황. VIEW only — the connections belong to the Device Server. */
    TERMINAL,
    /**
     * 개점 현황 — 단말 단위 개점·마감 (업무 정의서 §3.4).
     *
     * <p>Order 의 매장 개점/마감과 다릅니다. 저쪽은 매장이 주문을
     * 받는가이고, 이쪽은 <b>그 단말이 매출을 올릴 수 있는가</b>입니다.
     * POS 셋 중 하나만 마감한 매장은 여전히 영업 중입니다.
     * VIEW only — 개점과 마감은 POS가 누르는 것이고 BO는 결과를 봅니다.
     */
    POS_BUSINESS,
    /**
     * Live Check — 연결·프로그램·영업·장비·데이터 5종 (§3.5).
     * VIEW only — 전부 장비가 만든 사실입니다.
     */
    POS_LIVE,
    /**
     * 배포 — 마스터(§3.6)와 프로그램(§3.5).
     *
     * <p>VIEW 는 현황, SAVE 는 마스터 버전 올리기, CREATE 는 프로그램
     * 배포 등록입니다. 뒤의 둘을 가른 이유: 앞은 이미 있는 것을 다시
     * 내리는 일이고, 뒤는 <b>매장 장비에 깔릴 파일을 새로 지정하는</b>
     * 일입니다. DELETE 는 없습니다 — 배포는 지우는 것이 아니라 새로
     * 내는 것입니다.
     */
    POS_DEPLOY,
    /**
     * 단말 공지·긴급 메시지 (§3.5).
     *
     * <p>{@link #BOARD} 와 다릅니다. 저쪽은 BO 안에서 운영자끼리 보는
     * 게시판이고, 이쪽은 <b>매장 단말 화면에 뜨는 글</b>입니다 — 긴급이면
     * 손님 앞에서 일하는 사람의 화면을 가로챕니다.
     */
    POS_NOTICE,
    OPERATOR
}
