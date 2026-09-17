package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.apporder.AppOrderRepository;
import com.sunmoon.bo.domain.apporder.AppOrderRow;
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
 * <p><b>2026-09-17에 읽는 곳이 바뀌었습니다.</b> 전에는 OMS의 매출
 * 내역을 읽고 "테이블 번호가 있는가"로 QR 주문을 짐작했습니다. 이제는
 * APP-SERVER의 <b>주문 원장</b>을 읽습니다 (umbrella {@code docs/adr/0012}).
 * 두 가지가 달라집니다:
 *
 * <ul>
 *   <li>채널이 <b>짐작이 아니라 기록</b>입니다. 도착지(테이블 번호)와
 *       유입 경로(QR/APP)는 다른 값이고, 테이블 없는 QR이 생기는 날
 *       옛 짐작은 틀립니다.
 *   <li><b>OMS가 거절한 주문도 보입니다.</b> 손님에게는 그것도 일어난
 *       일인데, 매출 내역에는 아예 없었습니다.
 * </ul>
 *
 * <p>매출 조회({@link SalesController})와 여전히 다른 화면입니다. 저쪽은
 * "하루에 얼마를 팔았나"라서 일자×매장으로 접어 보고, 여기는 "그 주문이
 * 몇 번 자리로 갔고 수령번호가 무엇이었나"라서 한 건씩 펼쳐 봅니다.
 *
 * <p>권한도 따로입니다 — 홀 담당에게 "어느 자리 주문이었나"는 열어주되
 * 매장 전체 매출액은 안 보이게 할 수 있어야 합니다.
 */
@RestController
@RequestMapping("/api/app-sales")
@Tag(name = "AppSales", description = "APP·QR 주문 조회 (APP-SERVER 원장을 읽습니다)")
public class AppSalesController {

    private final AppOrderRepository orders;

    public AppSalesController(AppOrderRepository orders) {
        this.orders = orders;
    }

    /**
     * 그 판매일자에 들어온 APP·QR 주문.
     *
     * <p>{@code channel} 은 {@code QR} 또는 {@code APP} 입니다. 비우면
     * 전부입니다. 거르는 일을 화면이 아니라 원장에서 하는 이유는, 화면이
     * "전부 받아서 몇 개만 그리는" 모양이 되면 건수와 합계가 화면마다
     * 달라지기 때문입니다.
     */
    @GetMapping("/orders")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('APP_SALES_VIEW')")
    @Operation(summary = "APP·QR 주문 내역",
            description = "판매일자 기준입니다. channel 은 QR 또는 APP, 비우면 전부입니다. "
                    + "OMS가 거절한 주문도 함께 나옵니다 — 손님에게는 그것도 일어난 일입니다.")
    @ApiResponse(responseCode = "502", description = "APP-SERVER에 연결하지 못했습니다")
    public List<AppOrderRow> orders(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String channel) {
        return orders.findByDay(businessDate, storeId, channel);
    }
}
