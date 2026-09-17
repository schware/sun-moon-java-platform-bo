package com.sunmoon.bo.domain.master;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공통 CRUD의 규칙 (umbrella docs/adr/0014).
 *
 * <p>여기서 지키는 것은 하나입니다: <b>화면이 보낸 것을 그대로 쓰지
 * 않는다.</b> 값은 정의가 말하는 칸만, 정의가 말하는 타입으로 걸러서
 * 들어갑니다. 이 한 겹이 없으면 공통 CRUD는 "아무 JSON이나 표에 붓는
 * 창구"가 됩니다.
 */
class MasterServiceTest {

    private static final MasterDefinition BRAND = new MasterDefinition(
            "BRAND", "브랜드", "brands", "brand_code",
            com.sunmoon.bo.domain.operator.Screen.MASTER,
            List.of(
                    MasterField.key("brand_code", "브랜드코드"),
                    MasterField.required("name", "브랜드명"),
                    MasterField.text("company_code", "회사코드").asSearchable(),
                    MasterField.text("fee_rate", "수수료율").of(FieldType.NUMBER),
                    new MasterField("active", "사용", FieldType.BOOLEAN, false, true, true, null)),
            "brand_code");

    private FakeMasters repository;
    private MasterService service;

    @BeforeEach
    void setUp() {
        repository = new FakeMasters();
        service = new MasterService(new MasterCatalog(), repository);
    }

    private static Map<String, Object> body(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    /**
     * <b>모르는 칸은 버립니다.</b>
     *
     * <p>이 한 줄이 공통 CRUD의 안전장치입니다. 화면이 보낸 키를 그대로
     * SQL에 넣으면 그 순간 어떤 표든 고칠 수 있는 창구가 됩니다.
     */
    @Test
    void anUnknownColumnIsDroppedNotWritten() {
        service.create(BRAND, body(
                "brand_code", "B1", "name", "브랜드1",
                // 정의에 없는 칸. 표에는 있을 수도 있습니다.
                "is_admin", true, "password", "x"), "tester");

        assertThat(repository.inserted).containsOnlyKeys("brand_code", "name");
    }

    /** 400을 내지 않는 이유: 화면이 옛 칸을 들고 있을 수 있고, 그때 저장 전체가 막히면 안 됩니다. */
    @Test
    void anUnknownColumnDoesNotFailTheWholeSave() {
        assertThat(service.create(BRAND, body("brand_code", "B1", "name", "브랜드1", "nope", 1),
                "tester")).isNotNull();
    }

    /**
     * 필수 칸이 비면 <b>막습니다</b>.
     *
     * <p>모르는 칸과 반대인 이유: 저건 화면의 실수이고 이건 사람의
     * 실수입니다. 사람의 실수는 말해 줘야 고칩니다.
     */
    @Test
    void aMissingRequiredFieldIsRefusedWithItsLabel() {
        assertThatThrownBy(() -> service.create(BRAND, body("brand_code", "B1"), "tester"))
                .isInstanceOf(MasterService.InvalidMasterException.class)
                // 칸 이름이 아니라 **사람이 보는 이름**으로 말합니다.
                .hasMessageContaining("브랜드명");
    }

    @Test
    void aBlankRequiredFieldIsAlsoRefused() {
        assertThatThrownBy(() -> service.create(BRAND, body("brand_code", "B1", "name", "  "), "tester"))
                .isInstanceOf(MasterService.InvalidMasterException.class);
    }

    /**
     * 입력칸에서 나온 값은 늘 <b>글자</b>입니다.
     *
     * <p>여기서 한 번 맞춰 두지 않으면 {@code "2.3"} 이 숫자 칸에 글자로
     * 들어가고, 그 실패는 화면이 아니라 DB에서 납니다 — 운영자는 무엇이
     * 틀렸는지 알 길이 없습니다.
     */
    @Test
    void textFromAnInputBecomesTheTypeTheDefinitionSays() {
        service.create(BRAND, body("brand_code", "B1", "name", "브랜드1",
                "fee_rate", "2.3", "active", "true"), "tester");

        assertThat(repository.inserted.get("fee_rate")).isEqualTo(new BigDecimal("2.3"));
        assertThat(repository.inserted.get("active")).isEqualTo(true);
    }

    @Test
    void somethingThatIsNotANumberIsRefusedWithItsLabel() {
        assertThatThrownBy(() -> service.create(BRAND, body(
                "brand_code", "B1", "name", "브랜드1", "fee_rate", "2.3%"), "tester"))
                .isInstanceOf(MasterService.InvalidMasterException.class)
                .hasMessageContaining("수수료율");
    }

    /**
     * <b>키는 고칠 수 없습니다.</b>
     *
     * <p>키를 바꾸는 것은 수정이 아니라 다른 행을 만드는 일입니다.
     * 통과시키면 그 행을 가리키던 모든 데이터가 허공을 가리킵니다.
     */
    @Test
    void theKeyCannotBeChangedByAnUpdate() {
        repository.rows.put("B1", body("brand_code", "B1", "name", "브랜드1"));

        service.update(BRAND, "B1", body("brand_code", "B2", "name", "고친이름"), "tester");

        assertThat(repository.updated).doesNotContainKey("brand_code");
        assertThat(repository.updated).containsEntry("name", "고친이름");
    }

    /**
     * 바꾸기 <b>전</b> 값이 이력에 남습니다.
     *
     * <p>바뀐 값만 남기면 "무엇이 어떻게 달라졌나"에 답할 수 없습니다 —
     * 지금 값은 행 자체가 이미 알려 줍니다.
     */
    @Test
    void anUpdateRecordsWhatItWasBefore() {
        repository.rows.put("B1", body("brand_code", "B1", "name", "옛이름"));

        service.update(BRAND, "B1", body("name", "새이름"), "tester");

        FakeMasters.Audit audit = repository.audits.get(0);
        assertThat(audit.action()).isEqualTo("UPDATE");
        assertThat(audit.before()).containsEntry("name", "옛이름");
        assertThat(audit.actor()).isEqualTo("tester");
    }

    /**
     * 지운 <b>값</b>이 남습니다.
     *
     * <p>행은 사라지므로, 남기지 않으면 무엇이 있었는지 아무도 모릅니다.
     * 기준정보는 다른 데이터가 참조하고 있어서 그 질문이 반드시 나옵니다.
     */
    @Test
    void aDeleteKeepsWhatWasThere() {
        repository.rows.put("B1", body("brand_code", "B1", "name", "지울것"));

        assertThat(service.delete(BRAND, "B1", "tester")).isTrue();

        FakeMasters.Audit audit = repository.audits.get(0);
        assertThat(audit.action()).isEqualTo("DELETE");
        assertThat(audit.before()).containsEntry("name", "지울것");
        assertThat(audit.after()).isNull();
    }

    @Test
    void updatingSomethingThatIsNotThereIsEmptyNotAnError() {
        assertThat(service.update(BRAND, "없음", body("name", "x"), "tester")).isEmpty();
        assertThat(service.delete(BRAND, "없음", "tester")).isFalse();
        // 없는 행에 이력이 남으면 안 됩니다 — 아무 일도 안 일어났으니까요.
        assertThat(repository.audits).isEmpty();
    }

    /** 카탈로그에 실제로 넷이 있고, 모르는 코드는 빈 값입니다. */
    @Test
    void theCatalogAnswersOnlyForWhatItKnows() {
        assertThat(service.definition("BRAND")).isPresent();
        // 주소창에 아무 글자나 넣어도 404 입니다.
        assertThat(service.definition("DROP_TABLE")).isEmpty();
        assertThat(service.catalog()).extracting(MasterDefinition::code)
                .contains("COMPANY", "BRAND", "VENDOR", "CARD_ISSUER");
    }

    /** 정의는 소문자 코드도 알아봅니다 — 주소창에 손으로 치는 값입니다. */
    @Test
    void theCodeIsNotCaseSensitive() {
        assertThat(service.definition("brand")).isPresent();
    }

    private static final class FakeMasters implements MasterRepository {

        record Audit(String code, String id, String action,
                     Map<String, Object> before, Map<String, Object> after, String actor) {
        }

        private final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        private final List<Audit> audits = new ArrayList<>();
        private Map<String, Object> inserted = Map.of();
        private Map<String, Object> updated = Map.of();

        @Override
        public List<Map<String, Object>> list(MasterDefinition definition, Map<String, String> filters) {
            return List.copyOf(rows.values());
        }

        @Override
        public Optional<Map<String, Object>> find(MasterDefinition definition, String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void insert(MasterDefinition definition, Map<String, Object> values) {
            inserted = values;
            rows.put(String.valueOf(values.get(definition.idColumn())), values);
        }

        @Override
        public void update(MasterDefinition definition, String id, Map<String, Object> values) {
            updated = values;
            Map<String, Object> row = new LinkedHashMap<>(rows.getOrDefault(id, Map.of()));
            row.putAll(values);
            rows.put(id, row);
        }

        @Override
        public int delete(MasterDefinition definition, String id) {
            return rows.remove(id) == null ? 0 : 1;
        }

        @Override
        public void audit(String code, String id, String action,
                          Map<String, Object> before, Map<String, Object> after, String actor) {
            audits.add(new Audit(code, id, action, before, after, actor));
        }

        @Override
        public List<Map<String, Object>> auditOf(String code, String id, int limit) {
            return List.of();
        }
    }
}
