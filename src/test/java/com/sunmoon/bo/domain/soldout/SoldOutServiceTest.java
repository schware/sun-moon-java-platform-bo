package com.sunmoon.bo.domain.soldout;

import com.sunmoon.bo.domain.menu.Menu;
import com.sunmoon.bo.domain.menu.MenuRepository;
import com.sunmoon.bo.domain.menu.MenuSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결품의 규칙은 셋입니다: 없는 메뉴는 못 걸고, 두 번 눌러도 한 번이며,
 * <b>바뀌었을 때만</b> 알립니다.
 *
 * <p>마지막 것이 제일 중요합니다. 바뀌지 않았는데 알리면 매장의 모든
 * 단말이 목록을 다시 읽습니다 — 운영자가 토글을 연타하는 순간 주방
 * 화면이 깜빡이기 시작합니다.
 */
class SoldOutServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 17);

    private FakeSoldOuts soldOuts;
    private FakeMenus menus;
    private RecordingPublisher events;
    private SoldOutService service;

    @BeforeEach
    void setUp() {
        soldOuts = new FakeSoldOuts();
        menus = new FakeMenus("menu-01", "menu-02");
        events = new RecordingPublisher();
        service = new SoldOutService(soldOuts, menus, events,
                Clock.fixed(Instant.parse("2026-09-17T01:00:00Z"), SoldOutService.ZONE));
    }

    @Test
    void declaringSoldOutStoresItAndAnnouncesIt() {
        assertThat(service.declare("store-01", "menu-01", DAY, "admin"))
                .isEqualTo(SoldOutService.Result.OK);

        assertThat(service.list("store-01", DAY)).extracting(SoldOut::menuCode).containsExactly("menu-01");
        assertThat(events.published).extracting(MenuEvent::type).containsExactly(MenuEvent.SOLD_OUT);
    }

    @Test
    void declaringTwiceAnnouncesOnce() {
        service.declare("store-01", "menu-01", DAY, "admin");
        service.declare("store-01", "menu-01", DAY, "admin");

        assertThat(service.list("store-01", DAY)).hasSize(1);
        // 두 번째는 상태를 바꾸지 않았으니 단말을 깨울 이유가 없습니다.
        assertThat(events.published).hasSize(1);
    }

    @Test
    void releasingSomethingThatWasNotSoldOutAnnouncesNothing() {
        service.release("store-01", "menu-01", DAY);

        assertThat(events.published).isEmpty();
    }

    @Test
    void releasingAnnouncesTheRestore() {
        service.declare("store-01", "menu-01", DAY, "admin");
        events.published.clear();

        service.release("store-01", "menu-01", DAY);

        assertThat(service.list("store-01", DAY)).isEmpty();
        assertThat(events.published).extracting(MenuEvent::type).containsExactly(MenuEvent.RESTORED);
    }

    @Test
    void aMenuThatDoesNotExistCannotBeSoldOut() {
        // 오타 하나가 손님 화면에 영원히 안 풀리는 결품으로 남는 것을 막습니다
        // — 해제는 목록에서 고르는데, 목록에 없는 코드는 고를 수가 없습니다.
        assertThat(service.declare("store-01", "menu-99", DAY, "admin"))
                .isEqualTo(SoldOutService.Result.NO_SUCH_MENU);
        assertThat(events.published).isEmpty();
    }

    @Test
    void soldOutIsPerStoreAndPerBusinessDate() {
        service.declare("store-01", "menu-01", DAY, "admin");

        assertThat(service.list("store-02", DAY)).isEmpty();
        // 내일은 저절로 풀립니다 — 자정에 도는 배치가 없는 이유입니다.
        assertThat(service.list("store-01", DAY.plusDays(1))).isEmpty();
    }

    @Test
    void theDefaultDateIsTodayInSeoul() {
        // 2026-09-17T01:00Z 는 서울에서 이미 09-17 오전 10시입니다.
        assertThat(service.today()).isEqualTo(DAY);
    }

    // --- fakes -------------------------------------------------------

    private static final class FakeSoldOuts implements SoldOutRepository {
        private final List<SoldOut> rows = new ArrayList<>();

        @Override
        public List<SoldOut> findByStoreAndDate(String storeId, LocalDate businessDate) {
            return rows.stream()
                    .filter(r -> r.storeId().equals(storeId) && r.businessDate().equals(businessDate))
                    .toList();
        }

        @Override
        public boolean declare(SoldOut soldOut) {
            boolean exists = rows.stream().anyMatch(r -> r.storeId().equals(soldOut.storeId())
                    && r.menuCode().equals(soldOut.menuCode())
                    && r.businessDate().equals(soldOut.businessDate()));
            if (exists) {
                return false;
            }
            rows.add(new SoldOut(soldOut.storeId(), soldOut.menuCode(), soldOut.businessDate(),
                    Instant.now(), soldOut.declaredBy()));
            return true;
        }

        @Override
        public boolean release(String storeId, String menuCode, LocalDate businessDate) {
            return rows.removeIf(r -> r.storeId().equals(storeId)
                    && r.menuCode().equals(menuCode)
                    && r.businessDate().equals(businessDate));
        }
    }

    private record FakeMenus(String... codes) implements MenuRepository {
        @Override
        public List<MenuSummary> findAllWithCurrentPrice() {
            return List.of();
        }

        @Override
        public Optional<Menu> find(String menuCode) {
            return List.of(codes).contains(menuCode)
                    ? Optional.of(new Menu(menuCode, menuCode, null, null, null, true, null))
                    : Optional.empty();
        }

        @Override
        public void insert(Menu menu) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean update(Menu menu) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete(String menuCode) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingPublisher implements MenuEventPublisher {
        private final List<MenuEvent> published = new ArrayList<>();

        @Override
        public void publish(MenuEvent event) {
            published.add(event);
        }
    }
}
