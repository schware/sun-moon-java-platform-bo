package com.sunmoon.bo.domain.device;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {

    private final DeviceRepository repository;
    private final ConnectionChecker connectionChecker;

    public DeviceService(DeviceRepository repository, ConnectionChecker connectionChecker) {
        this.repository = repository;
        this.connectionChecker = connectionChecker;
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

    /**
     * One-shot, on demand — there is deliberately no scheduler polling
     * every device's address. Periodic health-checking is Batch/APM
     * territory, not this screen's.
     */
    public Optional<ConnectionCheckResult> checkConnection(String deviceId) {
        return repository.find(deviceId).map(device -> {
            if (device.ipAddress() == null || device.ipAddress().isBlank() || device.port() == null) {
                return ConnectionCheckResult.NO_ADDRESS;
            }
            return connectionChecker.isReachable(device.ipAddress(), device.port())
                    ? ConnectionCheckResult.REACHABLE
                    : ConnectionCheckResult.UNREACHABLE;
        });
    }
}
