package com.sunmoon.platform.infrastructure.persistence;

import com.sunmoon.platform.domain.commoncode.CommonCode;
import com.sunmoon.platform.domain.commoncode.CommonCodeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCommonCodeRepository implements CommonCodeRepository {

    private static final String COLUMNS = "group_code, code, name, sort_order, active";
    private static final RowMapper<CommonCode> MAPPER = (rs, rowNum) -> new CommonCode(
            rs.getString("group_code"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getInt("sort_order"),
            rs.getBoolean("active"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcCommonCodeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CommonCode> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM common_codes ORDER BY group_code, sort_order, code", MAPPER);
    }

    @Override
    public List<CommonCode> findByGroup(String groupCode) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM common_codes WHERE group_code = ? ORDER BY sort_order, code",
                MAPPER, groupCode);
    }

    @Override
    public Optional<CommonCode> find(String groupCode, String code) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM common_codes WHERE group_code = ? AND code = ?",
                MAPPER, groupCode, code).stream().findFirst();
    }

    @Override
    public void insert(CommonCode commonCode) {
        jdbcTemplate.update(
                "INSERT INTO common_codes (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?)",
                commonCode.groupCode(), commonCode.code(), commonCode.name(),
                commonCode.sortOrder(), commonCode.active());
    }

    @Override
    public boolean update(CommonCode commonCode) {
        return jdbcTemplate.update(
                "UPDATE common_codes SET name = ?, sort_order = ?, active = ? WHERE group_code = ? AND code = ?",
                commonCode.name(), commonCode.sortOrder(), commonCode.active(),
                commonCode.groupCode(), commonCode.code()) > 0;
    }

    @Override
    public boolean delete(String groupCode, String code) {
        return jdbcTemplate.update(
                "DELETE FROM common_codes WHERE group_code = ? AND code = ?", groupCode, code) > 0;
    }
}
