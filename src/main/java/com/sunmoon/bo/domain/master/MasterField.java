package com.sunmoon.bo.domain.master;

/**
 * 기준정보 표의 한 칸.
 *
 * @param name       컬럼 이름. <b>요청이 아니라 정의에서만</b> 옵니다 —
 *                   이 한 줄이 공통 CRUD 의 안전장치 전부입니다. 화면이
 *                   보낸 이름으로 SQL을 짜면 그 순간 어떤 표든 고칠 수
 *                   있는 창구가 됩니다.
 * @param searchable 조회 조건으로 쓸 수 있는가. 전부 열어 두지 않는
 *                   이유: 색인 없는 칸으로 거르면 행이 쌓일수록 느려지고,
 *                   느려지는 이유가 화면에 안 보입니다.
 * @param editable   고칠 수 있는가. 키는 보통 false 입니다 — 키를 바꾸는
 *                   것은 수정이 아니라 다른 행을 만드는 일입니다.
 * @param codeGroup  {@link FieldType#CODE} 일 때 어느 공통코드 그룹인가.
 */
public record MasterField(
        String name,
        String label,
        FieldType type,
        boolean required,
        boolean searchable,
        boolean editable,
        String codeGroup) {

    public static MasterField text(String name, String label) {
        return new MasterField(name, label, FieldType.TEXT, false, false, true, null);
    }

    public static MasterField required(String name, String label) {
        return new MasterField(name, label, FieldType.TEXT, true, true, true, null);
    }

    /** 키 칸 — 필수이고, 조회되고, 고칠 수 없습니다. */
    public static MasterField key(String name, String label) {
        return new MasterField(name, label, FieldType.TEXT, true, true, false, null);
    }

    /**
     * 조회 조건으로 쓸 수 있게.
     *
     * <p>이름이 {@code searchable()} 이 아닌 이유: record 는 그 이름을
     * 접근자로 이미 쓰고 있습니다. 같은 이름의 빌더를 얹으려다 컴파일이
     * 막혔고, 막힌 것이 맞습니다 — 읽는 것과 만드는 것이 같은 이름이면
     * 읽는 쪽에서 헷갈립니다.
     */
    public MasterField asSearchable() {
        return new MasterField(name, label, type, required, true, editable, codeGroup);
    }

    public MasterField of(FieldType newType) {
        return new MasterField(name, label, newType, required, searchable, editable, codeGroup);
    }

    public MasterField code(String group) {
        return new MasterField(name, label, FieldType.CODE, required, searchable, editable, group);
    }
}
