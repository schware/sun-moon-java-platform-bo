package com.sunmoon.bo.domain.table;

/**
 * 매장의 테이블 한 자리 — **배송지**입니다 (umbrella {@code docs/adr/0007}).
 *
 * <p>신원도 세션도 계산서도 아닙니다. "이 메뉴를 저 자리로 가져다 주세요"의
 * 저 자리이고, 그래서 번호만이 아니라 위치를 들고 있습니다. 언젠가 로봇이
 * 갈 곳입니다.
 *
 * <p>{@code id}가 대리 키인 이유: 자연 키를 (매장, 번호)로 잡으면 번호를
 * 바꾸는 일이 delete+insert가 되고 그때 {@code qrCode}가 같이 날아갑니다.
 * <b>QR은 인쇄돼서 테이블에 붙어 있어 다시 못 만듭니다.</b>
 *
 * <p>{@code tableNo}는 사람이 부르는 이름이고 {@code posX}/{@code posY}는
 * 기계가 가는 곳입니다. 둘을 따로 두어야 자리를 옮겨도 번호를 유지할 수
 * 있습니다.
 */
public record StoreTable(
        Long id,
        String storeId,
        int floor,
        String tableNo,
        String displayName,
        Integer posX,
        Integer posY,
        String qrCode,
        boolean active) {

    /** 화면에 보일 이름. 따로 안 정했으면 번호가 곧 이름입니다. */
    public String label() {
        return displayName == null || displayName.isBlank() ? tableNo : displayName;
    }

    /** 배치도에 그릴 수 있는가 — 좌표가 둘 다 있어야 합니다. */
    public boolean placed() {
        return posX != null && posY != null;
    }
}
