package com.sunmoon.bo.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunmoon.bo.domain.master.FieldType;
import com.sunmoon.bo.domain.master.MasterDefinition;
import com.sunmoon.bo.domain.master.MasterField;
import com.sunmoon.bo.domain.master.MasterRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 정의 하나로 아무 기준정보 표나 읽고 씁니다.
 *
 * <p><b>SQL에 들어가는 이름은 전부 {@link MasterDefinition} 에서
 * 옵니다.</b> 표 이름, 칸 이름, 정렬 기준 — 하나도 요청에서 오지
 * 않습니다. 이것이 이 클래스의 유일한 안전장치이고, 이 규칙이 깨지는
 * 순간 BO는 아무 표나 고칠 수 있는 창구가 됩니다.
 *
 * <p>값은 반대로 전부 물음표입니다. 이름은 정의에서, 값은 파라미터에서 —
 * 둘을 섞지 않는 것이 요령 전부입니다.
 */
@Repository
public class JdbcMasterRepository implements MasterRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcMasterRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> list(MasterDefinition definition, Map<String, String> filters) {
        List<Object> args = new ArrayList<>();
        String where = whereOf(definition, filters, args);
        String sql = "SELECT " + columns(definition) + " FROM " + definition.table()
                + where + " ORDER BY " + definition.orderBy() + " LIMIT 500";
        return jdbc.query(sql, this::row, args.toArray());
    }

    @Override
    public Optional<Map<String, Object>> find(MasterDefinition definition, String id) {
        String sql = "SELECT " + columns(definition) + " FROM " + definition.table()
                + " WHERE " + definition.idColumn() + " = ?";
        return jdbc.query(sql, this::row, id).stream().findFirst();
    }

    @Override
    public void insert(MasterDefinition definition, Map<String, Object> values) {
        List<String> names = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (MasterField field : definition.fields()) {
            if (values.containsKey(field.name())) {
                names.add(field.name());
                args.add(values.get(field.name()));
            }
        }
        String sql = "INSERT INTO " + definition.table() + " (" + String.join(", ", names)
                + ") VALUES (" + names.stream().map(n -> "?").reduce((a, b) -> a + ", " + b).orElse("")
                + ")";
        jdbc.update(sql, args.toArray());
    }

    @Override
    public void update(MasterDefinition definition, String id, Map<String, Object> values) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        // 키는 여기 못 들어옵니다 — editable() 이 빼 줍니다. 키를 바꾸는
        // 것은 수정이 아니라 다른 행을 만드는 일입니다.
        for (MasterField field : definition.editable()) {
            if (values.containsKey(field.name())) {
                sets.add(field.name() + " = ?");
                args.add(values.get(field.name()));
            }
        }
        if (sets.isEmpty()) {
            return;
        }
        args.add(id);
        jdbc.update("UPDATE " + definition.table() + " SET " + String.join(", ", sets)
                + " WHERE " + definition.idColumn() + " = ?", args.toArray());
    }

    @Override
    public int delete(MasterDefinition definition, String id) {
        return jdbc.update("DELETE FROM " + definition.table()
                + " WHERE " + definition.idColumn() + " = ?", id);
    }

    /**
     * 누가 무엇을 어떻게 바꿨나 (§6.2 "접속과 변경 로그").
     *
     * <p>공통 CRUD의 <b>가장 큰 값어치</b>가 여기 있습니다. 화면을 손으로
     * 마흔 벌 만들면 감사로그도 마흔 벌 붙여야 하고, 그중 몇 개는
     * 반드시 빠집니다. 한 곳을 지나가면 한 번만 쓰면 됩니다.
     *
     * <p>바꾸기 <b>전</b> 값을 함께 적습니다. 바뀐 값만 남기면 "무엇이
     * 어떻게 달라졌나"에 답할 수 없고, 되돌릴 근거도 없습니다.
     */
    @Override
    public void audit(String code, String id, String action,
                      Map<String, Object> before, Map<String, Object> after, String actor) {
        jdbc.update("INSERT INTO master_audit (master_code, row_id, action, before_value, "
                        + "after_value, actor) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?)",
                code, id, action, json(before), json(after), actor);
    }

    @Override
    public List<Map<String, Object>> auditOf(String code, String id, int limit) {
        String sql = "SELECT id, master_code, row_id, action, before_value::text AS before_value, "
                + "after_value::text AS after_value, actor, changed_at FROM master_audit "
                + "WHERE master_code = ?" + (id == null ? "" : " AND row_id = ?")
                + " ORDER BY id DESC LIMIT ?";
        Object[] args = id == null ? new Object[] { code, limit } : new Object[] { code, id, limit };
        return jdbc.query(sql, this::plainRow, args);
    }

    private String json(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // 감사로그를 못 적는 것이 저장을 막지는 않습니다. 대신
            // 무엇이 안 적혔는지는 남습니다.
            return "{\"error\":\"직렬화 실패\"}";
        }
    }

    private static String columns(MasterDefinition definition) {
        return definition.fields().stream().map(MasterField::name)
                .reduce((a, b) -> a + ", " + b).orElse("*");
    }

    /**
     * 조회 조건.
     *
     * <p>{@code searchable} 이 아닌 칸은 <b>조용히 무시됩니다</b>.
     * 400을 내지 않는 이유: 화면이 옛 조건을 들고 있을 수 있고, 그때
     * 목록을 못 보는 것보다 조건 하나가 안 걸리는 편이 낫습니다.
     *
     * <p>글자는 앞부분 일치입니다. 가운데 일치({@code %값%})로 두면 색인을
     * 못 쓰고, 행이 쌓일수록 느려지는 이유가 화면에 안 보입니다.
     */
    private static String whereOf(MasterDefinition definition, Map<String, String> filters,
                                  List<Object> args) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        List<String> conditions = new ArrayList<>();
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            String value = filter.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            definition.field(filter.getKey())
                    .filter(MasterField::searchable)
                    .ifPresent(field -> {
                        if (field.type() == FieldType.TEXT || field.type() == FieldType.CODE) {
                            conditions.add(field.name() + " ILIKE ?");
                            args.add(value.trim() + "%");
                        } else if (field.type() == FieldType.BOOLEAN) {
                            conditions.add(field.name() + " = ?");
                            args.add(Boolean.parseBoolean(value));
                        } else {
                            conditions.add(field.name() + " = ?");
                            args.add(value.trim());
                        }
                    });
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private Map<String, Object> row(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        var meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            Object value = rs.getObject(i);
            // BigDecimal 을 그대로 두면 JSON 에 "2.30" 처럼 나갑니다.
            // 화면은 숫자로 받아야 오른쪽 정렬과 계산을 할 수 있습니다.
            row.put(meta.getColumnLabel(i), value instanceof BigDecimal d ? d.doubleValue() : value);
        }
        return row;
    }

    private Map<String, Object> plainRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return row(rs, rowNum);
    }
}
