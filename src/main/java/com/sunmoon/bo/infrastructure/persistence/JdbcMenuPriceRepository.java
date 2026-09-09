package com.sunmoon.bo.infrastructure.persistence;

import com.sunmoon.bo.domain.menu.MenuPrice;
import com.sunmoon.bo.domain.menu.MenuPriceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class JdbcMenuPriceRepository implements MenuPriceRepository {

    private static final RowMapper<MenuPrice> MAPPER = (rs, rowNum) -> new MenuPrice(
            rs.getLong("id"),
            rs.getString("menu_code"),
            rs.getBigDecimal("price"),
            rs.getDate("start_date").toLocalDate(),
            rs.getDate("end_date") == null ? null : rs.getDate("end_date").toLocalDate());

    private final JdbcTemplate jdbcTemplate;

    public JdbcMenuPriceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<MenuPrice> findByMenu(String menuCode) {
        return jdbcTemplate.query(
                "SELECT id, menu_code, price, start_date, end_date FROM menu_prices "
                        + "WHERE menu_code = ? ORDER BY start_date",
                MAPPER, menuCode);
    }

    @Override
    public void insert(MenuPrice price) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO menu_prices (menu_code, price, start_date, end_date) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, price.menuCode());
            ps.setBigDecimal(2, price.price());
            ps.setDate(3, Date.valueOf(price.startDate()));
            ps.setDate(4, price.endDate() == null ? null : Date.valueOf(price.endDate()));
            return ps;
        }, keyHolder);
    }

    @Override
    public boolean delete(String menuCode, long priceId) {
        return jdbcTemplate.update(
                "DELETE FROM menu_prices WHERE id = ? AND menu_code = ?", priceId, menuCode) > 0;
    }

    @Override
    public boolean deleteAllForMenu(String menuCode) {
        return jdbcTemplate.update("DELETE FROM menu_prices WHERE menu_code = ?", menuCode) > 0;
    }
}
