package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.sales.SalesOrder;
import com.sunmoon.bo.domain.sales.SalesService;
import com.sunmoon.bo.domain.sales.SalesSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 매출 조회 — 조회 전용입니다. BO에는 매출을 고치는 화면이 없고, 앞으로도
 * 없어야 합니다: 매출은 단말에서 일어난 일의 결과이고, 그것을 관리 화면에서
 * 바꿀 수 있게 만드는 순간 어느 쪽이 사실인지 알 수 없게 됩니다.
 *
 * <p>Only VIEW is wired for that reason — there is no CREATE, SAVE or
 * DELETE to grant on this screen, and the permission matrix showing three
 * dead checkboxes is a smaller cost than an endpoint that could exist.
 */
@RestController
@RequestMapping("/api/sales")
@Tag(name = "Sales", description = "일자별 매출 조회 (Order 서비스에서 읽어옵니다)")
public class SalesController {

    private final SalesService service;

    public SalesController(SalesService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('SALES_VIEW')")
    @Operation(summary = "일자별 매출", description = "from/to를 비우면 오늘까지의 최근 2주입니다.")
    @ApiResponse(responseCode = "502", description = "Order 서비스에 연결하지 못했습니다")
    public List<SalesSummary> summarize(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String storeId) {
        return service.summarize(from, to, storeId);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('SALES_VIEW')")
    @Operation(summary = "그 날의 주문 내역", description = "집계 한 줄을 눌렀을 때 펼쳐지는 목록입니다.")
    @ApiResponse(responseCode = "502", description = "Order 서비스에 연결하지 못했습니다")
    public List<SalesOrder> detail(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(required = false) String storeId) {
        return service.detail(businessDate, storeId);
    }
}
