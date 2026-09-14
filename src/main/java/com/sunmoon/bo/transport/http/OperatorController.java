package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.operator.Action;
import com.sunmoon.bo.domain.operator.OperatorRequest;
import com.sunmoon.bo.domain.operator.OperatorService;
import com.sunmoon.bo.domain.operator.OperatorSummary;
import com.sunmoon.bo.domain.operator.Screen;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 운영자 관리.
 *
 * <p>Every method needs an {@code OPERATOR_*} permission, which only a
 * 전체 관리자 can grant — so the screen that hands out access is itself
 * behind the access it hands out. That is deliberate and it is why
 * {@link com.sunmoon.bo.config.AdminSeeder} exists.
 */
@RestController
@RequestMapping("/api/operators")
@Tag(name = "Operators", description = "운영자 계정과 화면별 권한")
public class OperatorController {

    private final OperatorService service;

    public OperatorController(OperatorService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OPERATOR_VIEW')")
    @Operation(summary = "조회", description = "비밀번호 해시는 내려가지 않습니다.")
    public List<OperatorSummary> list() {
        return service.list();
    }

    /**
     * The screen and action lists the permission matrix is drawn from.
     *
     * <p>Served rather than duplicated in the Frontend: {@link Screen} is a
     * code-defined enum that changes with deployments, and a hard-coded
     * copy in TypeScript would drift the first time a screen is added —
     * silently, since a missing column looks like a screen nobody has
     * permission on.
     */
    @GetMapping("/screens")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OPERATOR_VIEW')")
    @Operation(summary = "권한 매트릭스의 화면/행위 목록")
    public Map<String, List<String>> screens() {
        return Map.of(
                "screens", Arrays.stream(Screen.values()).map(Enum::name).toList(),
                "actions", Arrays.stream(Action.values()).map(Enum::name).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OPERATOR_CREATE')")
    @Operation(summary = "신규")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "비밀번호가 필요합니다")
    @ApiResponse(responseCode = "409", description = "이미 있는 아이디입니다")
    public ResponseEntity<?> create(@Valid @RequestBody OperatorRequest request) {
        OperatorService.Result result = service.create(request);
        return switch (result.outcome()) {
            case OK -> ResponseEntity.status(HttpStatus.CREATED).body(result.operator());
            case PASSWORD_REQUIRED -> bad("신규 등록에는 비밀번호가 필요합니다");
            case DUPLICATE_USERNAME -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("이미 있는 아이디입니다: " + request.username()));
            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("등록하지 못했습니다"));
        };
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OPERATOR_SAVE')")
    @Operation(summary = "저장",
            description = "비밀번호를 비워 두면 기존 비밀번호를 그대로 둡니다. 권한은 보낸 것으로 통째로 "
                    + "바뀝니다 — 화면이 매트릭스 전체를 보내기 때문입니다.")
    @ApiResponse(responseCode = "404", description = "없는 운영자입니다")
    @ApiResponse(responseCode = "409", description = "마지막 전체 관리자입니다")
    public ResponseEntity<?> save(@PathVariable long id, @Valid @RequestBody OperatorRequest request) {
        OperatorService.Result result = service.save(id, request);
        return switch (result.outcome()) {
            case OK -> ResponseEntity.ok(result.operator());
            case NOT_FOUND -> notFound(id);
            case LAST_SUPER_ADMIN -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("마지막 전체 관리자입니다 — 권한을 내리거나 사용 중지할 수 없습니다"));
            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("저장하지 못했습니다"));
        };
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OPERATOR_DELETE')")
    @Operation(summary = "삭제", description = "본인 계정과 마지막 전체 관리자는 삭제할 수 없습니다.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "없는 운영자입니다")
    @ApiResponse(responseCode = "409", description = "본인 계정이거나 마지막 전체 관리자입니다")
    public ResponseEntity<?> delete(@PathVariable long id, Authentication authentication) {
        return switch (service.delete(id, authentication.getName())) {
            case OK -> ResponseEntity.noContent().build();
            case NOT_FOUND -> notFound(id);
            case SELF -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("지금 로그인한 계정은 삭제할 수 없습니다"));
            case LAST_SUPER_ADMIN -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("마지막 전체 관리자는 삭제할 수 없습니다"));
            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("삭제하지 못했습니다"));
        };
    }

    /** An unknown screen name in the permission matrix — a 400, not a 500. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> onUnknownScreen(IllegalArgumentException e) {
        return bad(e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> bad(String message) {
        return ResponseEntity.badRequest().body(error(message));
    }

    private static ResponseEntity<Map<String, String>> notFound(long id) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("없는 운영자입니다: " + id));
    }

    private static Map<String, String> error(String message) {
        return Map.of("error", message);
    }
}
