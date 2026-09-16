package com.sunmoon.bo.infrastructure.persistence;

import com.sunmoon.bo.domain.soldout.SoldOut;
import com.sunmoon.bo.domain.soldout.SoldOutRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcSoldOutRepository implements SoldOutRepository {

    private static final RowMapper<SoldOut> MAPPER = (rs, rowNum) -> new SoldOut(
            rs.getString("store_id"),
            rs.getString("menu_code"),
            rs.getObject("business_date", LocalDate.class),
            rs.getTimestamp("declared_at").toInstant(),
            rs.getString("declared_by"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcSoldOutRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SoldOut> findByStoreAndDate(String storeId, LocalDate businessDate) {
        return jdbcTemplate.query("""
                SELECT store_id, menu_code, business_date, declared_at, declared_by
                FROM menu_sold_out
                WHERE store_id = ? AND business_date = ?
                ORDER BY menu_code
                """, MAPPER, storeId, businessDate);
    }

    /**
     * {@code ON CONFLICT DO NOTHING} 이라 두 번 눌러도 선언 시각이 뒤로
     * 밀리지 않습니다. 돌아오는 값은 "이번에 바뀌었나"이고, 이벤트를
     * 쏠지 말지를 그걸로 정합니다.
     */
    @Override
    public boolean declare(SoldOut soldOut) {
        return jdbcTemplate.update("""
                INSERT INTO menu_sold_out (store_id, menu_code, business_date, declared_by)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (store_id, menu_code, business_date) DO NOTHING
                """, soldOut.storeId(), soldOut.menuCode(), soldOut.businessDate(),
                soldOut.declaredBy()) > 0;
    }

    @Override
    public boolean release(String storeId, String menuCode, LocalDate businessDate) {
        return jdbcTemplate.update(
                "DELETE FROM menu_sold_out WHERE store_id = ? AND menu_code = ? AND business_date = ?",
                storeId, menuCode, businessDate) > 0;
    }
}
