package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.store.Store;
import com.sunmoon.bo.domain.store.StoreRequest;
import com.sunmoon.bo.domain.store.StoreService;
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
import java.util.Map;

/**
 * 매장 기준 정보. 개점/마감 상태는 여기 없습니다 — Order가 갖고 있고, BO는
 * 나중에 조회만 합니다.
 */
@RestController
@RequestMapping("/api/stores")
@Tag(name = "Stores", description = "매장 기준 정보 관리")
public class StoreController {

    private final StoreService service;

    public StoreController(StoreService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('STORE_VIEW')")
    @Operation(summary = "조회")
    public List<Store> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('STORE_CREATE')")
    @Operation(summary = "신규")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "409", description = "That storeId already exists")
    public ResponseEntity<?> create(@Valid @RequestBody StoreRequest request) {
        Store store = request.toDomain();
        return service.create(store)
                ? ResponseEntity.status(HttpStatus.CREATED).body(store)
                : ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Store already exists: " + request.storeId()));
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('STORE_SAVE')")
    @Operation(summary = "저장", description = "Updates an existing store. Never inserts.")
    @ApiResponse(responseCode = "404", description = "No such storeId")
    public ResponseEntity<?> save(@Valid @RequestBody StoreRequest request) {
        Store store = request.toDomain();
        return service.save(store)
                ? ResponseEntity.ok(store)
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(request.storeId()));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('STORE_DELETE')")
    @Operation(summary = "삭제")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such storeId")
    public ResponseEntity<?> delete(@RequestParam String storeId) {
        return service.delete(storeId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(storeId));
    }

    private static Map<String, String> notFound(String storeId) {
        return Map.of("error", "No such store: " + storeId);
    }
}
