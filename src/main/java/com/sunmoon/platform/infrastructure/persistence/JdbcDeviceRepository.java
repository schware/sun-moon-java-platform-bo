package com.sunmoon.platform.infrastructure.persistence;

import com.sunmoon.platform.domain.device.Device;
import com.sunmoon.platform.domain.device.DeviceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcDeviceRepository implements DeviceRepository {

    private static final String COLUMNS = "device_id, name, device_type, location, active";
    private static final RowMapper<Device> MAPPER = (rs, rowNum) -> new Device(
            rs.getString("device_id"),
            rs.getString("name"),
            rs.getString("device_type"),
            rs.getString("location"),
            rs.getBoolean("active"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcDeviceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Device> findAll() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM devices ORDER BY device_id", MAPPER);
    }

    @Override
    public List<Device> findByType(String deviceType) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM devices WHERE device_type = ? ORDER BY device_id",
                MAPPER, deviceType);
    }

    @Override
    public Optional<Device> find(String deviceId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM devices WHERE device_id = ?", MAPPER, deviceId)
                .stream().findFirst();
    }

    @Override
    public void insert(Device device) {
        jdbcTemplate.update(
                "INSERT INTO devices (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?)",
                device.deviceId(), device.name(), device.deviceType(), device.location(), device.active());
    }

    @Override
    public boolean update(Device device) {
        return jdbcTemplate.update(
                "UPDATE devices SET name = ?, device_type = ?, location = ?, active = ? WHERE device_id = ?",
                device.name(), device.deviceType(), device.location(), device.active(), device.deviceId()) > 0;
    }

    @Override
    public boolean delete(String deviceId) {
        return jdbcTemplate.update("DELETE FROM devices WHERE device_id = ?", deviceId) > 0;
    }
}
