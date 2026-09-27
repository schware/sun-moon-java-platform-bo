package com.sunmoon.bo.domain.master;

import com.sunmoon.bo.domain.operator.Screen;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 공통 CRUD가 다루는 기준정보 목록 (업무 정의서 §6.2 기준정보).
 *
 * <p>여기 한 줄을 더하는 것이 <b>화면 하나를 더하는 것</b>입니다.
 * 서버에 controller 도 repository 도 안 만들고, 화면에 page 도 안
 * 만듭니다.
 *
 * <p>지금 넷만 있는 것은 <b>표가 실제로 있는 것만</b> 넣었기 때문입니다.
 * 문서의 기준정보는 훨씬 많지만, 없는 표를 미리 정의해 두면 화면이
 * 열리자마자 500으로 죽습니다 — 목록이 길어 보이는 것보다 나쁩니다.
 *
 * <p>여기에 <b>넣지 않은 것</b>도 설계입니다. 매장·메뉴·장비·테이블·
 * 운영자는 이미 손으로 만든 화면이 있고, 그 화면들은 표가 아니라 업무를
 * 그립니다 — 메뉴에는 가격 이력이, 장비에는 접속 확인이, 테이블에는
 * QR이 붙어 있습니다. 공통 CRUD로 갈아 끼우면 그 전부를 잃습니다.
 * 결품은 더 분명합니다: 그건 격자가 아니라 눌러서 걸고 푸는 타일입니다.
 */
@Component
public class MasterCatalog {

    private final Map<String, MasterDefinition> definitions = new LinkedHashMap<>();

    public MasterCatalog() {
        add(new MasterDefinition("COMPANY", "회사", "companies", "company_code", Screen.MASTER,
                List.of(
                        MasterField.key("company_code", "회사코드"),
                        MasterField.required("name", "회사명"),
                        MasterField.text("biz_no", "사업자번호"),
                        MasterField.text("ceo_name", "대표자"),
                        MasterField.text("phone", "연락처"),
                        MasterField.text("address", "주소"),
                        new MasterField("active", "사용", FieldType.BOOLEAN, false, true, true, null)),
                "company_code"));

        add(new MasterDefinition("BRAND", "브랜드", "brands", "brand_code", Screen.MASTER,
                List.of(
                        MasterField.key("brand_code", "브랜드코드"),
                        MasterField.required("name", "브랜드명"),
                        // 회사 코드를 공통코드가 아니라 글자로 둡니다.
                        // 회사는 위의 표이지 코드표가 아니고, 화면에서
                        // 고를 수 있게 하는 것은 그다음 일입니다.
                        MasterField.text("company_code", "회사코드").asSearchable(),
                        MasterField.text("note", "메모").of(FieldType.LONG_TEXT),
                        new MasterField("active", "사용", FieldType.BOOLEAN, false, true, true, null)),
                "brand_code"));

        add(new MasterDefinition("VENDOR", "거래처", "vendors", "vendor_code", Screen.MASTER,
                List.of(
                        MasterField.key("vendor_code", "거래처코드"),
                        MasterField.required("name", "거래처명"),
                        // 거래처 구분은 공통코드에서 고릅니다 —
                        // 매입처/물류/외주처는 운영 중에 늘어납니다.
                        MasterField.text("vendor_type", "구분").code("VENDOR_TYPE").asSearchable(),
                        MasterField.text("biz_no", "사업자번호"),
                        MasterField.text("phone", "연락처"),
                        MasterField.text("address", "주소"),
                        new MasterField("active", "사용", FieldType.BOOLEAN, false, true, true, null)),
                "vendor_code"));

        add(new MasterDefinition("CARD_ISSUER", "카드사", "card_issuers", "issuer_code", Screen.MASTER,
                List.of(
                        MasterField.key("issuer_code", "카드사코드"),
                        MasterField.required("name", "카드사명"),
                        // 수수료율은 숫자입니다. 글자로 두면 화면이
                        // 오른쪽 정렬을 못 하고, 무엇보다 "2.3%"처럼
                        // 단위가 섞여 들어옵니다.
                        MasterField.text("fee_rate", "수수료율(%)").of(FieldType.NUMBER),
                        MasterField.text("settle_cycle_days", "정산주기(일)").of(FieldType.NUMBER),
                        new MasterField("active", "사용", FieldType.BOOLEAN, false, true, true, null)),
                "issuer_code"));

        // 결제수단 — 이 브랜드가 **받기로 한** 것 (2026-09-27).
        //
        // APP-SERVER 의 PaymentMethod enum 은 서버가 **처리할 줄 아는**
        // 것입니다. 둘은 다른 질문이고, 코드가 같아야 이어집니다 —
        // 그래서 코드 칸에 enum 이름(SUNMOON_PAY)을 그대로 적습니다.
        //
        // 공통코드가 아니라 자기 표인 이유는 schema.sql 에 적혀 있습니다:
        // 공통 CRUD 가 복합키를 못 다루고, 환불 가능 여부 같은 이 수단만의
        // 성질이 공통코드 칸에 안 들어갑니다.
        add(new MasterDefinition("PAY_METHOD", "결제수단", "payment_methods", "pay_method_code",
                Screen.MASTER,
                List.of(
                        MasterField.key("pay_method_code", "결제수단코드"),
                        MasterField.required("name", "결제수단명"),
                        new MasterField("refundable", "환불 가능", FieldType.BOOLEAN, false, false, true, null),
                        MasterField.text("sort_order", "표시순서").of(FieldType.NUMBER),
                        new MasterField("active", "사용", FieldType.BOOLEAN, false, true, true, null)),
                "sort_order, pay_method_code"));
    }

    private void add(MasterDefinition definition) {
        definitions.put(definition.code(), definition);
    }

    public List<MasterDefinition> all() {
        return List.copyOf(definitions.values());
    }

    /** 모르는 코드는 빈 값입니다 — 주소창에 아무 글자나 넣어도 404 입니다. */
    public Optional<MasterDefinition> find(String code) {
        return Optional.ofNullable(definitions.get(code == null ? null : code.toUpperCase()));
    }
}
