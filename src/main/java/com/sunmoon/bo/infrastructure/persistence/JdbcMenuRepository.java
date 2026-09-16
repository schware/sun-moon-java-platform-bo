package com.sunmoon.bo.infrastructure.persistence;

import com.sunmoon.bo.domain.menu.Menu;
import com.sunmoon.bo.domain.menu.MenuRepository;
import com.sunmoon.bo.domain.menu.MenuSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMenuRepository implements MenuRepository {

    private static final String COLUMNS =
            "menu_code, name, image_url, description, kds_device_id, active, cook_minutes";
    private static final RowMapper<Menu> MAPPER = (rs, rowNum) -> new Menu(
            rs.getString("menu_code"),
            rs.getString("name"),
            rs.getString("image_url"),
            rs.getString("description"),
            rs.getString("kds_device_id"),
            rs.getBoolean("active"),
            nullableInt(rs, "cook_minutes"));

    /** null과 0을 구분해야 합니다 — getInt는 null을 0으로 돌려줍니다. */
    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static final RowMapper<MenuSummary> SUMMARY_MAPPER = (rs, rowNum) -> new MenuSummary(
            rs.getString("menu_code"),
            rs.getString("name"),
            rs.getString("image_url"),
            rs.getString("description"),
            rs.getString("kds_device_id"),
            rs.getBoolean("active"),
            nullableInt(rs, "cook_minutes"),
            rs.getBigDecimal("current_price"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcMenuRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<MenuSummary> findAllWithCurrentPrice() {
        return jdbcTemplate.query("""
                SELECT m.menu_code, m.name, m.image_url, m.description, m.kds_device_id, m.active,
                       m.cook_minutes,
                       p.price AS current_price
                FROM menus m
                LEFT JOIN menu_prices p
                       ON p.menu_code = m.menu_code
                      AND p.start_date <= CURRENT_DATE
                      AND (p.end_date IS NULL OR p.end_date >= CURRENT_DATE)
                ORDER BY m.menu_code
                """, SUMMARY_MAPPER);
    }

    @Override
    public Optional<Menu> find(String menuCode) {
        return jdbcTemplate.query(
                        "SELECT " + COLUMNS + " FROM menus WHERE menu_code = ?", MAPPER, menuCode)
                .stream().findFirst();
    }

    @Override
    public void insert(Menu menu) {
        jdbcTemplate.update(
                "INSERT INTO menus (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                menu.menuCode(), menu.name(), menu.imageUrl(), menu.description(), menu.kdsDeviceId(),
                menu.active(), menu.cookMinutes());
    }

    @Override
    public boolean update(Menu menu) {
        return jdbcTemplate.update(
                "UPDATE menus SET name = ?, image_url = ?, description = ?, kds_device_id = ?, active = ?, "
                        + "cook_minutes = ? WHERE menu_code = ?",
                menu.name(), menu.imageUrl(), menu.description(), menu.kdsDeviceId(), menu.active(),
                menu.cookMinutes(), menu.menuCode()) > 0;
    }

    @Override
    public boolean delete(String menuCode) {
        return jdbcTemplate.update("DELETE FROM menus WHERE menu_code = ?", menuCode) > 0;
    }
}
