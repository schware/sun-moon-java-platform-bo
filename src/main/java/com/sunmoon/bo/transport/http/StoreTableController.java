package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.table.StoreTable;
import com.sunmoon.bo.domain.table.StoreTableRequest;
import com.sunmoon.bo.domain.table.StoreTableService;
import com.sunmoon.bo.infrastructure.qr.QrImages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 매장 배치도 — 테이블 기준 정보와 QR.
 *
 * <p>테이블은 배송지입니다(umbrella {@code docs/adr/0007}). 여기서 관리하는
 * 것은 <b>어디에 무엇이 있는가</b>이고, 그 자리에 주문이 도착했는지는
 * Order가 압니다.
 */
@RestController
@RequestMapping("/api/tables")
@Tag(name = "Tables", description = "매장 배치도와 테이블 QR")
public class StoreTableController {

    private final StoreTableService service;
    private final QrImages qrImages;
    private final String qrBaseUrl;

    public StoreTableController(StoreTableService service, QrImages qrImages,
            @Value("${bo.qr.table-base-url}") String qrBaseUrl) {
        this.service = service;
        this.qrImages = qrImages;
        this.qrBaseUrl = qrBaseUrl;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TABLE_VIEW')")
    @Operation(summary = "조회", description = "한 매장의 테이블을 층·번호 순으로.")
    public List<StoreTable> list(@RequestParam String storeId) {
        return service.byStore(storeId);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TABLE_CREATE')")
    @Operation(summary = "신규", description = "QR 코드는 여기서 발급됩니다 — 요청에 담지 않습니다.")
    @ApiResponse(responseCode = "201", description = "Created")
    public ResponseEntity<?> create(@Valid @RequestBody StoreTableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TABLE_SAVE')")
    @Operation(summary = "저장",
            description = "QR 코드는 바뀌지 않습니다. 이미 인쇄해서 붙인 스티커가 "
                    + "계속 이 테이블을 가리켜야 하기 때문입니다.")
    @ApiResponse(responseCode = "404", description = "없는 테이블입니다")
    public ResponseEntity<?> save(@PathVariable long id, @Valid @RequestBody StoreTableRequest request) {
        return service.save(id, request)
                ? ResponseEntity.ok(service.read(id).orElseThrow())
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TABLE_DELETE')")
    @Operation(summary = "삭제",
            description = "붙여둔 QR이 있다면 그것도 떼야 합니다 — 지운 뒤에는 그 코드가 "
                    + "아무것도 가리키지 않습니다.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<?> delete(@PathVariable long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(id));
    }

    /**
     * 인쇄용 QR 이미지. 저장하지 않고 매번 그립니다 — 코드가 정해지면
     * 그림도 정해지므로 보관할 것이 없습니다.
     */
    @GetMapping(value = "/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TABLE_VIEW')")
    @Operation(summary = "QR 이미지 (PNG)")
    public ResponseEntity<byte[]> qr(@PathVariable long id) {
        return service.read(id)
                .map(table -> ResponseEntity.ok(qrImages.pngOf(qrBaseUrl + table.qrCode())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * QR이 실제로 담는 주소. 인쇄 전에 눈으로 확인할 자리가 필요합니다 —
     * 이 값이 LAN 주소면 손님 휴대폰에서 안 열리고, 스티커는 회수가
     * 안 됩니다.
     */
    @GetMapping("/{id}/qr-target")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TABLE_VIEW')")
    @Operation(summary = "QR이 담는 주소")
    public ResponseEntity<?> qrTarget(@PathVariable long id) {
        return service.read(id)
                .<ResponseEntity<?>>map(table -> ResponseEntity.ok(
                        Map.of("qrCode", table.qrCode(), "url", qrBaseUrl + table.qrCode())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(id)));
    }

    private static Map<String, String> notFound(long id) {
        return Map.of("error", "없는 테이블입니다: " + id);
    }
}
