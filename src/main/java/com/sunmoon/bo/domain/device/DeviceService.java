package com.sunmoon.bo.domain.device;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceService {

    private final DeviceRepository repository;

    public DeviceService(DeviceRepository repository) {
        this.repository = repository;
    }

    public List<Device> list(String deviceType) {
        return deviceType == null || deviceType.isBlank()
                ? repository.findAll()
                : repository.findByType(deviceType);
    }

    public boolean create(Device device) {
        if (repository.find(device.deviceId()).isPresent()) {
            return false;
        }
        repository.insert(device);
        return true;
    }

    public boolean save(Device device) {
        return repository.update(device);
    }

    public boolean delete(String deviceId) {
        return repository.delete(deviceId);
    }
}
