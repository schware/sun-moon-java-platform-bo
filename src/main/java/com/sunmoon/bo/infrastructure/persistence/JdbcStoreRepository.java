package com.sunmoon.bo.infrastructure.persistence;

import com.sunmoon.bo.domain.store.Store;
import com.sunmoon.bo.domain.store.StoreRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcStoreRepository implements StoreRepository {

    private static final String COLUMNS = "store_id, name, phone, address, active";
    private static final RowMapper<Store> MAPPER = (rs, rowNum) -> new Store(
            rs.getString("store_id"),
            rs.getString("name"),
            rs.getString("phone"),
            rs.getString("address"),
            rs.getBoolean("active"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcStoreRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Store> findAll() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM stores ORDER BY store_id", MAPPER);
    }

    @Override
    public Optional<Store> find(String storeId) {
        return jdbcTemplate.query(
                        "SELECT " + COLUMNS + " FROM stores WHERE store_id = ?", MAPPER, storeId)
                .stream().findFirst();
    }

    @Override
    public void insert(Store store) {
        jdbcTemplate.update(
                "INSERT INTO stores (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?)",
                store.storeId(), store.name(), store.phone(), store.address(), store.active());
    }

    @Override
    public boolean update(Store store) {
        return jdbcTemplate.update(
                "UPDATE stores SET name = ?, phone = ?, address = ?, active = ? WHERE store_id = ?",
                store.name(), store.phone(), store.address(), store.active(), store.storeId()) > 0;
    }

    @Override
    public boolean delete(String storeId) {
        return jdbcTemplate.update("DELETE FROM stores WHERE store_id = ?", storeId) > 0;
    }
}
