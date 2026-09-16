package com.sunmoon.bo.domain.soldout;

import com.sunmoon.bo.domain.menu.MenuRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 결품 선언과 해제.
 *
 * <p>날짜는 <b>받습니다</b>. BO는 영업일자를 모르고, 알 방법도 없습니다 —
 * 그건 Order가 매장별로 들고 있는 값입니다. 화면의 기본값만 여기서
 * Asia/Seoul 오늘로 정하고, 자정을 넘겨 장사 중인 매장이 "오늘"을
 * 직접 고를 수 있게 열어둡니다.
 *
 * <p>없는 메뉴를 결품으로 걸 수는 없습니다. 오타 하나가 손님 화면에
 * 영원히 안 풀리는 결품으로 남는 것을 막습니다 — 해제는 목록에서
 * 고르는데, 목록에 안 나오는 코드는 손으로 지울 방법이 없습니다.
 */
@Service
public class SoldOutService {

    /** 이 계열의 영업일 기준 시간대. Order의 것과 같아야 합니다. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final SoldOutRepository soldOuts;
    private final MenuRepository menus;
    private final MenuEventPublisher events;
    private final Clock clock;

    @Autowired
    public SoldOutService(SoldOutRepository soldOuts, MenuRepository menus, MenuEventPublisher events) {
        this(soldOuts, menus, events, Clock.system(ZONE));
    }

    /** 테스트가 날짜를 고정할 수 있게. */
    public SoldOutService(SoldOutRepository soldOuts, MenuRepository menus, MenuEventPublisher events,
                          Clock clock) {
        this.soldOuts = soldOuts;
        this.menus = menus;
        this.events = events;
        this.clock = clock;
    }

    /** 화면이 날짜를 안 줬을 때 쓰는 기본값. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public List<SoldOut> list(String storeId, LocalDate businessDate) {
        return soldOuts.findByStoreAndDate(storeId, businessDate);
    }

    public Result declare(String storeId, String menuCode, LocalDate businessDate, String declaredBy) {
        if (menus.find(menuCode).isEmpty()) {
            return Result.NO_SUCH_MENU;
        }
        boolean changed = soldOuts.declare(
                new SoldOut(storeId, menuCode, businessDate, null, declaredBy));
        if (changed) {
            events.publish(MenuEvent.soldOut(storeId, menuCode, businessDate));
        }
        // 이미 결품이어도 성공입니다. 두 번 누른 운영자에게 오류를 보여줄
        // 이유가 없습니다 — 원하던 상태가 이미 참이니까요.
        return Result.OK;
    }

    public Result release(String storeId, String menuCode, LocalDate businessDate) {
        boolean changed = soldOuts.release(storeId, menuCode, businessDate);
        if (changed) {
            events.publish(MenuEvent.restored(storeId, menuCode, businessDate));
        }
        return Result.OK;
    }

    public enum Result {
        OK,
        NO_SUCH_MENU
    }
}
