package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.device.Device;
import com.sunmoon.bo.domain.device.DeviceRepository;
import com.sunmoon.bo.domain.device.KioskAdminPins;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/** 장비 화면 KIOSK 행의 [관리자 PIN] (umbrella docs/design/store-app.md §5.3). */
@RestController
@RequestMapping("/api/devices")
@Tag(name = "KIOSK admin PIN", description = "KIOSK 잠금을 푸는 관리자 PIN")
public class KioskPinController {

    private final DeviceRepository devices;
    private final KioskAdminPins pins;

    public KioskPinController(DeviceRepository devices, KioskAdminPins pins) {
        this.devices = devices;
        this.pins = pins;
    }

    /** PIN 본문 — 로그에 남지 않게 따로 받습니다. */
    public record KioskPinRequest(String pin) {
    }

    @PostMapping("/{deviceId}/kiosk-pin")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('DEVICE_SAVE')")
    @Operation(summary = "KIOSK 관리자 PIN",
            description = "화면 구석 5번 + 이 PIN 으로 KIOSK 잠금을 풉니다. 숫자 4~8자리. BO 는 저장하지 않고 "
                    + "POS-SERVER 로 넘기며, 거기에는 해시만 남습니다.")
    public ResponseEntity<?> setKioskPin(@PathVariable String deviceId,
                                         @RequestBody KioskPinRequest request,
                                         Authentication who) {
        Optional<Device> device = devices.find(deviceId);
        if (device.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such device: " + deviceId));
        }
        if (!"KIOSK".equals(device.get().terminalType()) || device.get().storeId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "매장이 정해진 KIOSK 장비에만 PIN 을 둡니다"));
        }
        String pin = request == null ? null : request.pin();
        if (pin == null || !pin.matches("[0-9]{4,8}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "PIN 은 숫자 4~8자리입니다"));
        }
        return ResponseEntity.ok(pins.set(device.get(), pin, who == null ? "bo" : who.getName()));
    }
}
