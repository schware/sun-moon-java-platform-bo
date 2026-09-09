package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.device.Device;
import com.sunmoon.bo.domain.device.DeviceRequest;
import com.sunmoon.bo.domain.device.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Device screen — master data only. Connection state and the handshake
 * belong to the Device Server, which does not exist yet.
 */
@RestController
@RequestMapping("/devices")
@Tag(name = "Devices", description = "장비 기준 정보 관리")
public class DeviceController {

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('DEVICE_VIEW')")
    @Operation(summary = "조회", description = "Lists devices, optionally filtered by type.")
    public List<Device> list(@RequestParam(required = false) String type) {
        return service.list(type);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('DEVICE_CREATE')")
    @Operation(summary = "신규")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "409", description = "That deviceId already exists")
    public ResponseEntity<?> create(@Valid @RequestBody DeviceRequest request) {
        Device device = request.toDomain();
        return service.create(device)
                ? ResponseEntity.status(HttpStatus.CREATED).body(device)
                : ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(java.util.Map.of("error", "Device already exists: " + request.deviceId()));
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('DEVICE_SAVE')")
    @Operation(summary = "저장", description = "Updates an existing device. Never inserts.")
    @ApiResponse(responseCode = "404", description = "No such deviceId")
    public ResponseEntity<?> save(@Valid @RequestBody DeviceRequest request) {
        Device device = request.toDomain();
        return service.save(device)
                ? ResponseEntity.ok(device)
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(request.deviceId()));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('DEVICE_DELETE')")
    @Operation(summary = "삭제")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such deviceId")
    public ResponseEntity<?> delete(@RequestParam String deviceId) {
        return service.delete(deviceId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(deviceId));
    }

    private static java.util.Map<String, String> notFound(String deviceId) {
        return java.util.Map.of("error", "No such device: " + deviceId);
    }
}
