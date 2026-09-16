package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.menu.MenuService;
import com.sunmoon.bo.domain.menu.MenuSummary;
import com.sunmoon.bo.domain.soldout.SoldOut;
import com.sunmoon.bo.domain.soldout.SoldOutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 결품 (품절) — 오늘 이 매장에 <b>없는</b> 메뉴.
 *
 * <p>메뉴를 내리는 화면(메뉴 관리)과 일부러 갈라 두었습니다. 저쪽은 기준
 * 정보를 고치는 일이라 자주 없고, 이쪽은 장사 중에 몇 번씩 누르는 일입니다.
 * 권한도 그래서 따로입니다 — 홀 담당에게 결품은 열어주되 메뉴 마스터는
 * 못 건드리게 할 수 있어야 합니다.
 *
 * <p>날짜는 <b>판매일자</b>입니다. 안 주면 Asia/Seoul 오늘로 봅니다.
 */
@RestController
@RequestMapping("/api/sold-out")
@Tag(name = "SoldOut", description = "결품 선언과 해제")
public class SoldOutController {

    private final SoldOutService service;
    private final MenuService menus;

    public SoldOutController(SoldOutService service, MenuService menus) {
        this.service = service;
        this.menus = menus;
    }

    /**
     * 그 매장의 그 날짜 메뉴판 전체 — 결품 표시를 달아서.
     *
     * <p>결품 목록만 주지 않는 이유: 화면이 토글을 그리려면 어차피 메뉴
     * 전체가 필요하고, 두 번 부르게 하면 그 사이에 바뀐 값으로 어긋난
     * 화면이 나옵니다.
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('SOLD_OUT_VIEW')")
    @Operation(summary = "조회", description = "메뉴 전체에 결품 여부를 달아서 돌려줍니다.")
    public Map<String, Object> list(
            @RequestParam String storeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {

        LocalDate date = businessDate == null ? service.today() : businessDate;
        List<SoldOut> soldOuts = service.list(storeId, date);
        Set<String> soldOutCodes = soldOuts.stream().map(SoldOut::menuCode).collect(Collectors.toSet());

        List<Map<String, Object>> items = menus.list().stream()
                .map(menu -> item(menu, soldOutCodes.contains(menu.menuCode())))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("storeId", storeId);
        body.put("businessDate", date);
        body.put("menus", items);
        return body;
    }

    private static Map<String, Object> item(MenuSummary menu, boolean soldOut) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("menuCode", menu.menuCode());
        row.put("name", menu.name());
        row.put("active", menu.active());
        row.put("currentPrice", menu.currentPrice());
        row.put("cookMinutes", menu.cookMinutes());
        row.put("soldOut", soldOut);
        return row;
    }

    @PostMapping("/{menuCode}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('SOLD_OUT_CREATE')")
    @Operation(summary = "결품 선언",
            description = "이미 결품이어도 200입니다 — 원하던 상태가 이미 참이면 오류가 아닙니다.")
    public ResponseEntity<?> declare(
            @PathVariable String menuCode,
            @RequestParam String storeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            Authentication authentication) {

        LocalDate date = businessDate == null ? service.today() : businessDate;
        return switch (service.declare(storeId, menuCode, date, authentication.getName())) {
            case OK -> ResponseEntity.ok(Map.of(
                    "storeId", storeId, "menuCode", menuCode, "businessDate", date, "soldOut", true));
            case NO_SUCH_MENU -> ResponseEntity.status(404)
                    .body(Map.of("error", "존재하지 않는 메뉴입니다: " + menuCode));
        };
    }

    @DeleteMapping("/{menuCode}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('SOLD_OUT_DELETE')")
    @Operation(summary = "결품 해제",
            description = "결품이 아니어도 200입니다 — 없는 것을 지우라는 요청은 이미 이뤄진 요청입니다.")
    public ResponseEntity<?> release(
            @PathVariable String menuCode,
            @RequestParam String storeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {

        LocalDate date = businessDate == null ? service.today() : businessDate;
        service.release(storeId, menuCode, date);
        return ResponseEntity.ok(Map.of(
                "storeId", storeId, "menuCode", menuCode, "businessDate", date, "soldOut", false));
    }
}
