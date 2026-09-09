package com.sunmoon.bo.domain.device;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository {

    List<Device> findAll();

    List<Device> findByType(String deviceType);

    Optional<Device> find(String deviceId);

    void insert(Device device);

    boolean update(Device device);

    boolean delete(String deviceId);
}
