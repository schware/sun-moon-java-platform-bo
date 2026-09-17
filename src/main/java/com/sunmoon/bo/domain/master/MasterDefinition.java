package com.sunmoon.bo.domain.master;

import com.sunmoon.bo.domain.operator.Screen;

import java.util.List;
import java.util.Optional;

/**
 * 기준정보 표 하나의 정의 — <b>화면이 코드가 아니라 값이 됩니다</b>.
 *
 * <p>업무 정의서 §6.2 의 기준정보만 세어도 회사·조직·브랜드·거래처·
 * 품목·레시피·메뉴분류·카드사·결제수단·지역코드·달력코드… 입니다.
 * 화면마다 controller + repository + page 를 손으로 만들면 같은 코드를
 * 마흔 벌 쓰게 되고, 마흔 벌은 마흔 군데가 조금씩 다르다는 뜻입니다 —
 * 어떤 화면은 감사로그를 남기고 어떤 화면은 안 남깁니다.
 *
 * <p><b>무엇에 쓰고 무엇에 안 쓰는가</b>가 이 구조에서 제일 중요합니다:
 *
 * <ul>
 *   <li><b>씁니다</b> — 사람이 값을 적어 두는 표. 회사, 브랜드, 거래처,
 *       카드사, 코드류. 화면이 하는 일이 "목록·검색·입력·저장"뿐인 것.
 *   <li><b>안 씁니다</b> — <b>무엇이 일어나는</b> 화면. 결품(타일을 눌러
 *       걸고 푸는 것), 개점 현황, Live Check, 매출, 정산, 프로모션 적용.
 *       이런 화면은 표가 아니라 업무이고, 억지로 넣으면 "모든 것이 행
 *       하나"인 시스템이 됩니다. 결품 화면이 격자가 아니라 타일인 데는
 *       이유가 있었습니다.
 * </ul>
 *
 * <p>정의가 <b>DB가 아니라 코드</b>에 있는 이유는 {@link Screen} 과
 * 같습니다: 화면 목록은 배포로 바뀌지 운영자가 바꾸는 것이 아닙니다.
 * DB에 두면 누군가 행 하나를 지워서 화면이 사라지고, 그것을 되돌릴
 * 기록이 없습니다.
 *
 * @param code    화면 코드. 주소와 권한에 쓰입니다 — {@code /master/BRAND}.
 * @param table   실제 표 이름. <b>요청에서 오지 않습니다.</b>
 * @param idColumn 단건을 가리키는 칸. 복합키는 아직 못 다룹니다 —
 *                 {@code common_codes} 가 그런 표이고, 그래서 그 화면은
 *                 지금도 손으로 만든 것입니다.
 * @param screen  이 표가 속한 권한 화면. 대부분 {@link Screen#MASTER}
 *                이고, 따로 떼야 할 표만 자기 화면을 가리킵니다.
 */
public record MasterDefinition(
        String code,
        String label,
        String table,
        String idColumn,
        Screen screen,
        List<MasterField> fields,
        String orderBy) {

    public MasterDefinition {
        fields = List.copyOf(fields);
    }

    /** 고칠 수 있는 칸들 — 키는 빠집니다. */
    public List<MasterField> editable() {
        return fields.stream().filter(MasterField::editable).toList();
    }

    public List<MasterField> searchable() {
        return fields.stream().filter(MasterField::searchable).toList();
    }

    /**
     * 그 이름의 칸.
     *
     * <p>돌려주는 것이 {@link Optional} 인 것이 요점입니다 — 모르는
     * 이름은 <b>조용히 버려집니다</b>. 화면이 보낸 키를 그대로 SQL에
     * 넣으면 그 순간 어떤 표든 고칠 수 있는 창구가 됩니다.
     */
    public Optional<MasterField> field(String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst();
    }
}
