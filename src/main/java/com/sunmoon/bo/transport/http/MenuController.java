package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.menu.Menu;
import com.sunmoon.bo.domain.menu.MenuPrice;
import com.sunmoon.bo.domain.menu.MenuPriceRequest;
import com.sunmoon.bo.domain.menu.MenuRequest;
import com.sunmoon.bo.domain.menu.MenuService;
import com.sunmoon.bo.domain.menu.MenuSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Menu master data and its price history. 대/중/소 size tiers are not here
 * yet — see the umbrella repo's roadmap.
 */
@RestController
@RequestMapping("/api/menus")
@Tag(name = "Menus", description = "메뉴 기준 정보 및 가격 이력 관리")
public class MenuController {

    private final MenuService service;

    public MenuController(MenuService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MENU_VIEW')")
    @Operation(summary = "조회", description = "Lists menus with the price effective today.")
    public List<MenuSummary> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MENU_CREATE')")
    @Operation(summary = "신규")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "409", description = "That menuCode already exists")
    public ResponseEntity<?> create(@Valid @RequestBody MenuRequest request) {
        Menu menu = request.toDomain();
        return service.create(menu)
                ? ResponseEntity.status(HttpStatus.CREATED).body(menu)
                : ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Menu already exists: " + request.menuCode()));
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MENU_SAVE')")
    @Operation(summary = "저장", description = "Updates an existing menu. Never inserts.")
    @ApiResponse(responseCode = "404", description = "No such menuCode")
    public ResponseEntity<?> save(@Valid @RequestBody MenuRequest request) {
        Menu menu = request.toDomain();
        return service.save(menu)
                ? ResponseEntity.ok(menu)
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(request.menuCode()));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MENU_DELETE')")
    @Operation(summary = "삭제", description = "Also removes that menu's price history.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such menuCode")
    public ResponseEntity<?> delete(@RequestParam String menuCode) {
        return service.delete(menuCode)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(menuCode));
    }

    @GetMapping("/{menuCode}/prices")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MENU_VIEW')")
    @Operation(summary = "가격 이력 조회")
    public List<MenuPrice> priceHistory(@PathVariable String menuCode) {
        return service.priceHistory(menuCode);
    }

    @PostMapping("/{menuCode}/prices")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MENU_SAVE')")
    @Operation(summary = "가격 이력 등록", description = "Rejects a date range that overlaps an existing row for this menu.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "404", description = "No such menuCode")
    @ApiResponse(responseCode = "409", description = "Overlaps an existing price row")
    public ResponseEntity<?> addPrice(@PathVariable String menuCode, @Valid @RequestBody MenuPriceRequest request) {
        MenuPrice price = request.toDomain(menuCode);
        return switch (service.addPrice(menuCode, price)) {
            case OK -> ResponseEntity.status(HttpStatus.CREATED).body(price);
            case NO_SUCH_MENU -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(menuCode));
            case OVERLAPS_EXISTING -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Overlaps an existing price row for " + menuCode));
        };
    }

    @DeleteMapping("/{menuCode}/prices/{priceId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MENU_SAVE')")
    @Operation(summary = "가격 이력 삭제")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such price row")
    public ResponseEntity<?> deletePrice(@PathVariable String menuCode, @PathVariable long priceId) {
        return service.deletePrice(menuCode, priceId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such price row: " + priceId));
    }

    @GetMapping("/{menuCode}/price")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MENU_VIEW')")
    @Operation(summary = "특정 일자의 유효 가격 조회", description = "메뉴 연동시 사용할 가격 — date를 생략하면 오늘 기준.")
    @ApiResponse(responseCode = "404", description = "No price row covers that date")
    public ResponseEntity<?> effectivePrice(@PathVariable String menuCode, @RequestParam(required = false) LocalDate date) {
        LocalDate lookupDate = date != null ? date : LocalDate.now();
        return service.effectivePrice(menuCode, lookupDate)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No price for " + menuCode + " on " + lookupDate)));
    }

    private static Map<String, String> notFound(String menuCode) {
        return Map.of("error", "No such menu: " + menuCode);
    }
}
