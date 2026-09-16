package com.sunmoon.bo.infrastructure.persistence;

import com.sunmoon.bo.domain.table.StoreTable;
import com.sunmoon.bo.domain.table.StoreTableRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcStoreTableRepository implements StoreTableRepository {

    private static final String COLUMNS =
            "id, store_id, floor, table_no, display_name, pos_x, pos_y, qr_code, active";

    private static final RowMapper<StoreTable> MAPPER = (rs, rowNum) -> new StoreTable(
            rs.getLong("id"),
            rs.getString("store_id"),
            rs.getInt("floor"),
            rs.getString("table_no"),
            rs.getString("display_name"),
            (Integer) rs.getObject("pos_x"),
            (Integer) rs.getObject("pos_y"),
            rs.getString("qr_code"),
            rs.getBoolean("active"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcStoreTableRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StoreTable> findByStore(String storeId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM store_tables WHERE store_id = ? ORDER BY floor, table_no",
                MAPPER, storeId);
    }

    @Override
    public Optional<StoreTable> find(long id) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM store_tables WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public Optional<StoreTable> findByQrCode(String qrCode) {
        return jdbcTemplate.query(
                        "SELECT " + COLUMNS + " FROM store_tables WHERE qr_code = ?", MAPPER, qrCode)
                .stream().findFirst();
    }

    @Override
    public boolean existsQrCode(String qrCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store_tables WHERE qr_code = ?", Integer.class, qrCode);
        return count != null && count > 0;
    }

    @Override
    public StoreTable insert(StoreTable table) {
        Long id = jdbcTemplate.queryForObject("SELECT nextval('store_tables_id_seq')", Long.class);
        jdbcTemplate.update(
                "INSERT INTO store_tables (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, table.storeId(), table.floor(), table.tableNo(), table.displayName(),
                table.posX(), table.posY(), table.qrCode(), table.active());
        return new StoreTable(id, table.storeId(), table.floor(), table.tableNo(),
                table.displayName(), table.posX(), table.posY(), table.qrCode(), table.active());
    }

    @Override
    public boolean update(StoreTable table) {
        // qr_code는 일부러 빠져 있습니다 — 한 번 인쇄된 값은 바뀌면 안 됩니다.
        return jdbcTemplate.update(
                "UPDATE store_tables SET store_id = ?, floor = ?, table_no = ?, display_name = ?, "
                        + "pos_x = ?, pos_y = ?, active = ? WHERE id = ?",
                table.storeId(), table.floor(), table.tableNo(), table.displayName(),
                table.posX(), table.posY(), table.active(), table.id()) > 0;
    }

    @Override
    public boolean delete(long id) {
        return jdbcTemplate.update("DELETE FROM store_tables WHERE id = ?", id) > 0;
    }
}
