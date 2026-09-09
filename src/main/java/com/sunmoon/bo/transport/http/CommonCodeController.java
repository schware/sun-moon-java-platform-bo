package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.commoncode.CommonCode;
import com.sunmoon.bo.domain.commoncode.CommonCodeRequest;
import com.sunmoon.bo.domain.commoncode.CommonCodeService;
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
 * 공통코드 screen. Each method states the tier-3 permission it needs
 * inline, so a method that is <em>not</em> permission-checked is visible as
 * a missing annotation rather than as an absence somewhere else.
 */
@RestController
@RequestMapping("/api/common-code")
@Tag(name = "Common Code", description = "공통코드 관리")
public class CommonCodeController {

    private final CommonCodeService service;

    public CommonCodeController(CommonCodeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('COMMON_CODE_VIEW')")
    @Operation(summary = "조회", description = "Lists common codes, optionally filtered to one group.")
    public List<CommonCode> list(@RequestParam(required = false) String group) {
        return service.list(group);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('COMMON_CODE_CREATE')")
    @Operation(summary = "신규", description = "Inserts a new code. Deliberately not an upsert — 신규 and 저장 are separate permissions.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "409", description = "That (groupCode, code) already exists")
    public ResponseEntity<?> create(@Valid @RequestBody CommonCodeRequest request) {
        CommonCode commonCode = request.toDomain();
        return service.create(commonCode)
                ? ResponseEntity.status(HttpStatus.CREATED).body(commonCode)
                : ResponseEntity.status(HttpStatus.CONFLICT).body(conflict(request));
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('COMMON_CODE_SAVE')")
    @Operation(summary = "저장", description = "Updates an existing code. Never inserts.")
    @ApiResponse(responseCode = "404", description = "No such (groupCode, code)")
    public ResponseEntity<?> save(@Valid @RequestBody CommonCodeRequest request) {
        CommonCode commonCode = request.toDomain();
        return service.save(commonCode)
                ? ResponseEntity.ok(commonCode)
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(request.groupCode(), request.code()));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('COMMON_CODE_DELETE')")
    @Operation(summary = "삭제", description = "Deletes by key, which travels in the query string.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such (groupCode, code)")
    public ResponseEntity<?> delete(@RequestParam String group, @RequestParam String code) {
        return service.delete(group, code)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(group, code));
    }

    private static java.util.Map<String, String> conflict(CommonCodeRequest request) {
        return java.util.Map.of("error",
                "Common code already exists: " + request.groupCode() + "/" + request.code());
    }

    private static java.util.Map<String, String> notFound(String groupCode, String code) {
        return java.util.Map.of("error", "No such common code: " + groupCode + "/" + code);
    }
}
