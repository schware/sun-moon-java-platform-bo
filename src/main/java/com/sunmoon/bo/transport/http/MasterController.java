package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.master.MasterDefinition;
import com.sunmoon.bo.domain.master.MasterField;
import com.sunmoon.bo.domain.master.MasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기준정보 공통 CRUD — <b>화면 하나가 아니라 화면 전부</b>.
 *
 * <p>{@code /api/master/BRAND}, {@code /api/master/VENDOR} … 코드만
 * 다르고 나머지는 같습니다. 새 기준정보를 붙이는 일은 이 파일을 고치는
 * 것이 아니라 {@code MasterCatalog} 에 정의 한 줄을 더하는 것입니다.
 *
 * <p>업무 정의서 §6.2 의 기준정보만 세어도 회사·조직·브랜드·거래처·
 * 품목·레시피·메뉴분류·카드사·결제수단·지역코드·달력코드… 입니다.
 * 손으로 만들면 같은 코드를 마흔 벌 쓰게 되고, 마흔 벌은 마흔 군데가
 * 조금씩 다르다는 뜻입니다.
 *
 * <p><b>권한이 한 겹뿐인 것은 알고 두는 한계입니다.</b> 지금은 모든
 * 기준정보가 {@code MASTER} 화면 하나를 공유하므로, 브랜드를 고칠 수
 * 있는 사람은 거래처도 고칠 수 있습니다. 표마다 권한을 떼려면 정의가
 * 자기 {@code Screen} 을 가리키게 하면 되고 그 자리는 이미 열려
 * 있지만, 권한 화면이 마흔 개가 되는 것이 정말 필요한지는 실제로
 * 나눠 달라는 요구가 나온 뒤에 정하는 편이 낫습니다.
 */
@RestController
@RequestMapping("/api/master")
@Tag(name = "Master", description = "기준정보 공통 CRUD — 정의 한 줄이 화면 하나")
public class MasterController {

    private final MasterService masters;

    public MasterController(MasterService masters) {
        this.masters = masters;
    }

    /**
     * 이 BO가 다루는 기준정보 목록과 <b>각 표의 칸 생김새</b>.
     *
     * <p>화면은 이걸 읽어서 자기를 그립니다 — 열 이름도, 입력칸 종류도,
     * 검색 조건도 여기서 옵니다. 그래서 새 기준정보가 붙어도 화면
     * 코드는 한 줄도 안 바뀝니다.
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MASTER_VIEW')")
    @Operation(summary = "기준정보 목록과 정의")
    public List<Map<String, Object>> catalog() {
        return masters.catalog().stream().map(MasterController::describe).toList();
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MASTER_VIEW')")
    @Operation(summary = "그 기준정보의 행들",
            description = "검색 조건은 쿼리 파라미터로 그대로 받습니다. 정의가 searchable 이라고 "
                    + "말한 칸만 걸리고, 나머지는 조용히 무시됩니다 — 옛 화면이 보낸 조건 하나 "
                    + "때문에 목록을 통째로 못 보는 것보다 낫습니다.")
    @ApiResponse(responseCode = "404", description = "그런 기준정보가 없습니다")
    public ResponseEntity<?> list(@PathVariable String code,
                                  @RequestParam Map<String, String> filters) {
        return masters.definition(code)
                .<ResponseEntity<?>>map(definition -> ResponseEntity.ok(Map.of(
                        "definition", describe(definition),
                        "rows", masters.list(definition, filters))))
                .orElseGet(() -> notFound(code));
    }

    @GetMapping("/{code}/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MASTER_VIEW')")
    @Operation(summary = "한 행")
    public ResponseEntity<?> find(@PathVariable String code, @PathVariable String id) {
        return masters.definition(code)
                .<ResponseEntity<?>>map(definition -> masters.find(definition, id)
                        .<ResponseEntity<?>>map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(error("그런 행이 없습니다: " + id))))
                .orElseGet(() -> notFound(code));
    }

    @PostMapping("/{code}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MASTER_CREATE')")
    @Operation(summary = "새 행")
    @ApiResponse(responseCode = "400", description = "필수 칸이 비었거나 타입이 맞지 않습니다")
    @ApiResponse(responseCode = "409", description = "같은 키가 이미 있습니다")
    public ResponseEntity<?> create(@PathVariable String code,
                                    @RequestBody Map<String, Object> body,
                                    Authentication who) {
        return masters.definition(code)
                .<ResponseEntity<?>>map(definition -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(masters.create(definition, body, actor(who))))
                .orElseGet(() -> notFound(code));
    }

    @PutMapping("/{code}/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MASTER_SAVE')")
    @Operation(summary = "고치기", description = "키 칸은 안 바뀝니다 — 키를 바꾸는 것은 다른 행을 만드는 일입니다.")
    public ResponseEntity<?> update(@PathVariable String code, @PathVariable String id,
                                    @RequestBody Map<String, Object> body,
                                    Authentication who) {
        return masters.definition(code)
                .<ResponseEntity<?>>map(definition -> masters.update(definition, id, body, actor(who))
                        .<ResponseEntity<?>>map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(error("그런 행이 없습니다: " + id))))
                .orElseGet(() -> notFound(code));
    }

    @DeleteMapping("/{code}/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MASTER_DELETE')")
    @Operation(summary = "지우기",
            description = "지운 값은 변경 이력에 남습니다 — 기준정보는 다른 데이터가 참조하고 "
                    + "있어서, 지운 뒤에 그 참조가 무엇을 가리켰는지 물어볼 일이 반드시 생깁니다.")
    public ResponseEntity<?> delete(@PathVariable String code, @PathVariable String id,
                                    Authentication who) {
        return masters.definition(code)
                .<ResponseEntity<?>>map(definition -> masters.delete(definition, id, actor(who))
                        ? ResponseEntity.noContent().build()
                        : ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(error("그런 행이 없습니다: " + id)))
                .orElseGet(() -> notFound(code));
    }

    /**
     * 변경 이력 (§6.2 "접속과 변경 로그").
     *
     * <p>공통 CRUD의 가장 큰 값어치입니다 — 화면을 손으로 마흔 벌 만들면
     * 이력도 마흔 벌 붙여야 하고, 그중 몇 개는 반드시 빠집니다.
     */
    @GetMapping("/{code}/{id}/history")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MASTER_VIEW')")
    @Operation(summary = "그 행의 변경 이력")
    public ResponseEntity<?> history(@PathVariable String code, @PathVariable String id) {
        return masters.definition(code)
                .<ResponseEntity<?>>map(definition ->
                        ResponseEntity.ok(masters.history(definition.code(), id, 50)))
                .orElseGet(() -> notFound(code));
    }

    /** 정의를 화면이 읽을 모양으로. */
    private static Map<String, Object> describe(MasterDefinition definition) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", definition.code());
        view.put("label", definition.label());
        view.put("idColumn", definition.idColumn());
        view.put("fields", definition.fields().stream().map(MasterController::describe).toList());
        return view;
    }

    private static Map<String, Object> describe(MasterField field) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", field.name());
        view.put("label", field.label());
        view.put("type", field.type().name());
        view.put("required", field.required());
        view.put("searchable", field.searchable());
        view.put("editable", field.editable());
        view.put("codeGroup", field.codeGroup());
        return view;
    }

    /**
     * 필수 칸이 비었거나 타입이 안 맞습니다.
     *
     * <p>500이 아니라 400이고, <b>서버가 만든 문장을 그대로</b> 보냅니다 —
     * 운영자가 읽고 고칠 수 있어야 하는 값이라 "요청이 잘못됐습니다"로는
     * 아무것도 못 합니다.
     */
    @ExceptionHandler(MasterService.InvalidMasterException.class)
    ResponseEntity<Map<String, String>> onInvalid(MasterService.InvalidMasterException e) {
        return ResponseEntity.badRequest().body(error(e.getMessage()));
    }

    /** 같은 키가 이미 있습니다. 유일 제약이 막은 것이지 서버가 고장난 것이 아닙니다. */
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    ResponseEntity<Map<String, String>> onDuplicate(org.springframework.dao.DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("같은 코드가 이미 있습니다"));
    }

    /**
     * 다른 데이터가 이 행을 참조하고 있습니다.
     *
     * <p>지워지지 않는 것이 맞습니다 — 참조가 끊긴 기준정보는 그것을
     * 가리키던 거래를 읽을 수 없게 만듭니다. 화면에는 "왜"가 보여야
     * 하므로 409로 답합니다.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> onReferenced(
            org.springframework.dao.DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("다른 자료가 쓰고 있어 지울 수 없습니다"));
    }

    private static ResponseEntity<?> notFound(String code) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("그런 기준정보가 없습니다: " + code));
    }

    private static String actor(Authentication who) {
        return who == null ? "bo" : who.getName();
    }

    private static Map<String, String> error(String message) {
        return Map.of("error", String.valueOf(message));
    }
}
