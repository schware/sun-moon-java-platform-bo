package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.device.Device;
import com.sunmoon.bo.domain.device.DevicePairing;
import com.sunmoon.bo.domain.device.DeviceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 장비 화면의 [페어링 코드] · [해제] (umbrella docs/design/store-app.md §3).
 *
 * <p>단말은 더 이상 자기 매장·번호·역할을 고르지 않습니다. 여기서 발급한
 * 6자리 코드를 단말에 넣으면, 이 장비 행에 적힌 매장·번호·종류·역할이 그
 * 기기의 신원이 됩니다.
 */
@RestController
@RequestMapping("/api/devices")
@Tag(name = "Device pairing", description = "단말 페어링 코드 발급과 해제")
public class DevicePairingController {

    private final DeviceRepository devices;
    private final DevicePairing pairing;

    public DevicePairingController(DeviceRepository devices, DevicePairing pairing) {
        this.devices = devices;
        this.pairing = pairing;
    }

    @GetMapping("/pairing")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('DEVICE_VIEW')")
    @Operation(summary = "페어링 상태", description = "Device Server 가 아는 기기별 상태. 코드 자체는 다시 안 보입니다.")
    public List<Map<String, Object>> status(@RequestParam(required = false) String storeId) {
        return pairing.devices(storeId);
    }

    @PostMapping("/{deviceId}/pairing-code")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('DEVICE_SAVE')")
    @Operation(summary = "페어링 코드 발급",
            description = "6자리, 10분, 한 번만. 같은 장비에 다시 발급하면 앞의 코드는 끝납니다.")
    public ResponseEntity<?> issue(@PathVariable String deviceId, Authentication who) {
        Optional<Device> device = devices.find(deviceId);
        if (device.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such device: " + deviceId));
        }
        String problem = cannotPair(device.get());
        if (problem != null) {
            return ResponseEntity.badRequest().body(Map.of("error", problem));
        }
        return ResponseEntity.ok(pairing.issueCode(device.get(), who == null ? "bo" : who.getName()));
    }

    @PostMapping("/{deviceId}/unpair")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('DEVICE_SAVE')")
    @Operation(summary = "페어링 해제",
            description = "그 기기의 토큰을 끝냅니다. 다음 연결부터 거절되고, 다시 코드를 넣어야 합니다.")
    public ResponseEntity<?> unpair(@PathVariable String deviceId) {
        Optional<Device> device = devices.find(deviceId);
        if (device.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such device: " + deviceId));
        }
        if (device.get().storeId() == null || device.get().terminalType() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "매장이나 단말 종류가 없는 장비입니다"));
        }
        return ResponseEntity.ok(pairing.revoke(device.get()));
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
                                         @org.springframework.web.bind.annotation.RequestBody KioskPinRequest request,
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
        return ResponseEntity.ok(pairing.setKioskPin(device.get(), pin, who == null ? "bo" : who.getName()));
    }

    /** 페어링은 "이 매장의 이 번호의 이 종류" 를 묶는 일이라, 하나라도 없으면 묶을 것이 없습니다. */
    static String cannotPair(Device device) {
        if (device.storeId() == null || device.storeId().isBlank()) {
            return "매장이 정해지지 않은 장비는 페어링할 수 없습니다";
        }
        if (device.terminalType() == null || device.terminalType().isBlank()) {
            return "단말 종류(POS/KDS/DID)가 없는 장비는 페어링하지 않습니다 (프린터 등)";
        }
        if (!device.active()) {
            return "사용 안 함으로 된 장비입니다. 먼저 사용으로 바꾸십시오";
        }
        return null;
    }
}
