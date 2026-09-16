package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.sales.SalesOrder;
import com.sunmoon.bo.domain.sales.SalesService;
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
 * APP 매출 조회 — 손님 쪽으로 들어온 주문을 <b>어디서 와서 어디로
 * 갔는가</b>로 봅니다.
 *
 * <p>매출 조회({@link SalesController})와 <b>같은 데이터</b>를 읽습니다.
 * 따로 있는 이유는 묻는 질문이 다르기 때문입니다: 저쪽은 "하루에 얼마를
 * 팔았나"이고 여기는 "그 주문이 몇 번 자리로 갔고 수령번호가 무엇이었나"
 * 입니다. 한 화면에 둘을 섞으면 열이 열두 개가 되고, 둘 다 읽기 어려워
 * 집니다.
 *
 * <p>권한도 따로입니다 — 홀 담당에게 "어느 자리 주문이었나"는 열어주되
 * 매장 전체 매출액은 안 보이게 할 수 있어야 합니다.
 *
 * <p><b>지금은 채널 구분이 없습니다.</b> 주문에 "어느 경로로 들어왔나"를
 * 적는 칸이 아직 없어서, 갈라볼 수 있는 것은 <b>테이블 QR로 들어왔는가</b>
 * ({@code tableNo}가 있는가) 하나뿐입니다. POS가 직접 주문을 만들기
 * 시작하면 그때는 진짜 채널 칸이 필요해집니다 — 지금 지어내면 그날
 * 두 군데를 고치게 됩니다.
 */
@RestController
@RequestMapping("/api/app-sales")
@Tag(name = "AppSales", description = "APP·테이블 주문 조회 (Order 서비스에서 읽어옵니다)")
public class AppSalesController {

    private final SalesService service;

    public AppSalesController(SalesService service) {
        this.service = service;
    }

    /**
     * 그 판매일자에 그 매장으로 들어온 주문.
     *
     * <p>{@code fromTable}이 {@code true}면 테이블 QR로 들어온 것만,
     * {@code false}면 그 밖의 것만, 비우면 전부입니다. 거르는 일을
     * 화면이 아니라 여기서 하는 이유는, 화면이 "전부 받아서 세 개만
     * 그리는" 모양이 되면 건수와 합계가 화면마다 달라지기 때문입니다.
     */
    @GetMapping("/orders")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('APP_SALES_VIEW')")
    @Operation(summary = "APP·테이블 주문 내역",
            description = "판매일자 기준입니다. fromTable=true 면 테이블 QR로 들어온 주문만, "
                    + "false 면 그 밖(매장 선택 화면)만, 비우면 전부입니다.")
    @ApiResponse(responseCode = "502", description = "Order 서비스에 연결하지 못했습니다")
    public List<SalesOrder> orders(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) Boolean fromTable) {

        List<SalesOrder> all = service.detail(businessDate, storeId);
        if (fromTable == null) {
            return all;
        }
        return all.stream().filter(order -> order.fromTable() == fromTable).toList();
    }
}
