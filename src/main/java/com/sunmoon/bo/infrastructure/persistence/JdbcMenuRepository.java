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

    private static final String COLUMNS = "menu_code, name, image_url, description, active";
    private static final RowMapper<Menu> MAPPER = (rs, rowNum) -> new Menu(
            rs.getString("menu_code"),
            rs.getString("name"),
            rs.getString("image_url"),
            rs.getString("description"),
            rs.getBoolean("active"));

    private static final RowMapper<MenuSummary> SUMMARY_MAPPER = (rs, rowNum) -> new MenuSummary(
            rs.getString("menu_code"),
            rs.getString("name"),
            rs.getString("image_url"),
            rs.getString("description"),
            rs.getBoolean("active"),
            rs.getBigDecimal("current_price"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcMenuRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<MenuSummary> findAllWithCurrentPrice() {
        return jdbcTemplate.query("""
                SELECT m.menu_code, m.name, m.image_url, m.description, m.active, p.price AS current_price
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
                "INSERT INTO menus (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?)",
                menu.menuCode(), menu.name(), menu.imageUrl(), menu.description(), menu.active());
    }

    @Override
    public boolean update(Menu menu) {
        return jdbcTemplate.update(
                "UPDATE menus SET name = ?, image_url = ?, description = ?, active = ? WHERE menu_code = ?",
                menu.name(), menu.imageUrl(), menu.description(), menu.active(), menu.menuCode()) > 0;
    }

    @Override
    public boolean delete(String menuCode) {
        return jdbcTemplate.update("DELETE FROM menus WHERE menu_code = ?", menuCode) > 0;
    }
}
