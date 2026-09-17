package com.sunmoon.bo.domain.master;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 공통 CRUD의 규칙 — 무엇을 받아들이고, 무엇을 남기는가.
 *
 * <p>화면이 보낸 것을 <b>그대로 쓰지 않습니다.</b> 값은 정의가 말하는
 * 칸만, 정의가 말하는 타입으로 걸러서 들어갑니다. 이 한 겹이 없으면
 * 공통 CRUD는 "아무 JSON이나 표에 붓는 창구"가 됩니다.
 */
@Service
public class MasterService {

    private static final Logger log = LoggerFactory.getLogger(MasterService.class);

    private final MasterCatalog catalog;
    private final MasterRepository repository;

    public MasterService(MasterCatalog catalog, MasterRepository repository) {
        this.catalog = catalog;
        this.repository = repository;
    }

    /** 무엇이 틀렸는지 화면이 그대로 보여줄 수 있게 문장으로 답합니다. */
    public static class InvalidMasterException extends RuntimeException {
        public InvalidMasterException(String message) {
            super(message);
        }
    }

    public List<MasterDefinition> catalog() {
        return catalog.all();
    }

    public Optional<MasterDefinition> definition(String code) {
        return catalog.find(code);
    }

    public List<Map<String, Object>> list(MasterDefinition definition, Map<String, String> filters) {
        return repository.list(definition, filters);
    }

    public Optional<Map<String, Object>> find(MasterDefinition definition, String id) {
        return repository.find(definition, id);
    }

    /**
     * 새 행.
     *
     * <p>저장과 감사로그가 <b>한 트랜잭션</b>입니다. 갈라 두면 "바뀌었는데
     * 기록이 없는" 행과 "기록은 있는데 안 바뀐" 행이 둘 다 생기고,
     * 둘 다 나중에 사람을 오래 붙잡습니다.
     */
    @Transactional
    public Map<String, Object> create(MasterDefinition definition, Map<String, Object> raw, String actor) {
        Map<String, Object> values = accept(definition, raw, true);
        String id = String.valueOf(values.get(definition.idColumn()));

        repository.insert(definition, values);
        repository.audit(definition.code(), id, "CREATE", null, values, actor);
        log.info("{} 등록: {} ({})", definition.label(), id, actor);
        return repository.find(definition, id).orElse(values);
    }

    @Transactional
    public Optional<Map<String, Object>> update(MasterDefinition definition, String id,
                                                Map<String, Object> raw, String actor) {
        Optional<Map<String, Object>> before = repository.find(definition, id);
        if (before.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> values = accept(definition, raw, false);
        repository.update(definition, id, values);

        Map<String, Object> after = repository.find(definition, id).orElse(values);
        repository.audit(definition.code(), id, "UPDATE", before.get(), after, actor);
        log.info("{} 수정: {} ({})", definition.label(), id, actor);
        return Optional.of(after);
    }

    /**
     * 지우기.
     *
     * <p>지운 <b>값</b>을 감사로그에 남깁니다. 행은 사라지므로, 남기지
     * 않으면 "무엇이 있었는지"를 아무도 되짚을 수 없습니다 — 기준정보는
     * 다른 데이터가 참조하고 있어서, 지운 뒤에 그 참조가 무엇을 가리켰는지
     * 물어볼 일이 반드시 생깁니다.
     */
    @Transactional
    public boolean delete(MasterDefinition definition, String id, String actor) {
        Optional<Map<String, Object>> before = repository.find(definition, id);
        if (before.isEmpty()) {
            return false;
        }
        repository.delete(definition, id);
        repository.audit(definition.code(), id, "DELETE", before.get(), null, actor);
        log.info("{} 삭제: {} ({})", definition.label(), id, actor);
        return true;
    }

    public List<Map<String, Object>> history(String code, String id, int limit) {
        return repository.auditOf(code, id, limit);
    }

    /**
     * 화면이 보낸 것에서 <b>쓸 것만</b> 골라 타입을 맞춥니다.
     *
     * <p>모르는 칸은 예외가 아니라 <b>버립니다</b>. 화면이 옛 칸을 들고
     * 있을 수 있고, 그때 저장 전체를 막으면 운영자는 이유도 모른 채
     * 아무것도 못 고칩니다. 반대로 <b>필수 칸이 비면 막습니다</b> —
     * 그건 화면의 실수가 아니라 사람의 실수이고, 말해 줘야 합니다.
     *
     * @param creating 새 행인가. 수정일 때는 키를 못 바꿉니다.
     */
    private Map<String, Object> accept(MasterDefinition definition, Map<String, Object> raw,
                                       boolean creating) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (MasterField field : definition.fields()) {
            boolean isKey = field.name().equals(definition.idColumn());
            if (!creating && !field.editable()) {
                continue;
            }
            if (!raw.containsKey(field.name())) {
                if (creating && field.required()) {
                    throw new InvalidMasterException(field.label() + "은(는) 필수입니다");
                }
                continue;
            }
            Object value = coerce(field, raw.get(field.name()));
            if (field.required() && isBlank(value)) {
                throw new InvalidMasterException(field.label() + "은(는) 필수입니다");
            }
            if (creating && isKey && isBlank(value)) {
                throw new InvalidMasterException(field.label() + "은(는) 필수입니다");
            }
            values.put(field.name(), value);
        }
        if (creating && !values.containsKey(definition.idColumn())) {
            throw new InvalidMasterException("키 값이 없습니다");
        }
        return values;
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    /**
     * 정의가 말하는 타입으로 바꿉니다.
     *
     * <p>JSON은 숫자와 글자를 헷갈리지 않지만 <b>사람이 만든 화면은
     * 헷갈립니다</b> — 입력칸에서 나온 값은 늘 글자입니다. 여기서 한 번
     * 맞춰 두지 않으면 {@code "2.3"} 이 숫자 칸에 글자로 들어가고,
     * 그 실패는 화면이 아니라 DB에서 납니다.
     */
    private static Object coerce(MasterField field, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return switch (field.type()) {
            case NUMBER -> text.isEmpty() ? null : parseNumber(field, text);
            case BOOLEAN -> value instanceof Boolean b ? b : Boolean.parseBoolean(text);
            case DATE -> text.isEmpty() ? null : parseDate(field, text);
            case TEXT, LONG_TEXT, CODE -> text.isEmpty() ? null : text;
        };
    }

    private static Object parseNumber(MasterField field, String text) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException notANumber) {
            throw new InvalidMasterException(field.label() + "은(는) 숫자여야 합니다: " + text);
        }
    }

    private static Object parseDate(MasterField field, String text) {
        try {
            return java.sql.Date.valueOf(LocalDate.parse(text));
        } catch (RuntimeException notADate) {
            throw new InvalidMasterException(field.label() + "은(는) 날짜여야 합니다: " + text);
        }
    }
}
