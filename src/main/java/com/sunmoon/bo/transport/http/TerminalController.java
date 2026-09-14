package com.sunmoon.bo.transport.http;

import com.sunmoon.bo.domain.terminal.TerminalStatus;
import com.sunmoon.bo.domain.terminal.TerminalStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 단말 현황 — 조회 전용. BO는 단말을 <em>등록</em>하고(장비 화면), 여기서는
 * 그 등록이 실제와 맞는지만 봅니다. 연결을 끊거나 붙이는 버튼은 없습니다:
 * 연결은 Device Server가 가진 사실이고, BO가 그것을 바꾸려면 BO가 운영
 * 경로 위에 올라앉게 됩니다.
 */
@RestController
@RequestMapping("/api/terminals")
@Tag(name = "Terminals", description = "단말 연결 현황 (Device Server에서 읽어옵니다)")
public class TerminalController {

    private final TerminalStatusService service;

    public TerminalController(TerminalStatusService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TERMINAL_VIEW')")
    @Operation(summary = "단말 현황",
            description = "등록된 단말과 실제 접속 중인 단말을 맞춰서 돌려줍니다. registered=false는 "
                    + "장비에 없는 단말기번호로 접속했다는 뜻이고, connected=false는 등록은 되어 있는데 "
                    + "화면이 안 떠 있다는 뜻입니다.")
    @ApiResponse(responseCode = "502", description = "Device Server에 연결하지 못했습니다")
    public List<TerminalStatus> list() {
        return service.list();
    }
}
