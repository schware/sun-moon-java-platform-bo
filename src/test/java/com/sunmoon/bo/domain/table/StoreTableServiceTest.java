package com.sunmoon.bo.domain.table;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QR 코드가 <b>발급 뒤 절대 바뀌지 않는다</b>는 것이 이 서비스의 핵심
 * 성질입니다. 코드가 바뀌면 이미 인쇄해서 테이블에 붙인 스티커가 아무것도
 * 가리키지 않게 되고, 스티커는 회수가 안 됩니다.
 */
class StoreTableServiceTest {

    private final StubRepository repository = new StubRepository();
    private final StoreTableService service = new StoreTableService(repository);

    private static StoreTableRequest request(String tableNo, Integer x, Integer y) {
        return new StoreTableRequest(null, "store-01", 1, tableNo, null, x, y, true);
    }

    @Test
    void issuesACodeOnCreate() {
        StoreTable created = service.create(request("3", 2, 2));

        assertThat(created.qrCode()).isNotBlank();
        assertThat(created.id()).isNotNull();
    }

    @Test
    void keepsTheSameCodeWhenTheTableIsRenumberedOrMoved() {
        StoreTable created = service.create(request("3", 2, 2));
        String printed = created.qrCode();

        // 번호를 바꾸고 자리도 옮깁니다 — 스티커는 그대로 붙어 있습니다.
        service.save(created.id(), new StoreTableRequest(
                created.id(), "store-01", 2, "7", "창가", 9, 9, true));

        assertThat(repository.rows.get(created.id()).qrCode()).isEqualTo(printed);
        assertThat(repository.rows.get(created.id()).tableNo()).isEqualTo("7");
        assertThat(repository.rows.get(created.id()).floor()).isEqualTo(2);
    }

    @Test
    void neverIssuesTheSameCodeTwice() {
        // 200개를 뽑아도 겹치지 않아야 합니다. 겹치면 유니크 제약에 걸려
        // 등록이 실패하고, 운영자는 이유를 알 수 없습니다.
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            codes.add(service.create(request("t" + i, null, null)).qrCode());
        }

        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void aTableWithoutCoordinatesIsNotPlaced() {
        StoreTable floating = service.create(request("9", null, null));

        // 배치도에 못 그리지만 존재는 합니다 — 좌표는 나중에 찍어도 됩니다.
        assertThat(floating.placed()).isFalse();
        assertThat(service.create(request("10", 1, 1)).placed()).isTrue();
    }

    @Test
    void labelFallsBackToTheTableNumber() {
        StoreTable plain = new StoreTable(1L, "store-01", 1, "3", null, null, null, "AAA", true);
        StoreTable named = new StoreTable(2L, "store-01", 1, "4", "창가", null, null, "BBB", true);

        assertThat(plain.label()).isEqualTo("3");
        assertThat(named.label()).isEqualTo("창가");
    }

    private static final class StubRepository implements StoreTableRepository {
        private final java.util.Map<Long, StoreTable> rows = new java.util.LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public List<StoreTable> findByStore(String storeId) {
            return rows.values().stream().filter(r -> r.storeId().equals(storeId)).toList();
        }

        @Override
        public Optional<StoreTable> find(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<StoreTable> findByQrCode(String qrCode) {
            return rows.values().stream().filter(r -> r.qrCode().equals(qrCode)).findFirst();
        }

        @Override
        public boolean existsQrCode(String qrCode) {
            return findByQrCode(qrCode).isPresent();
        }

        @Override
        public StoreTable insert(StoreTable table) {
            long id = nextId++;
            StoreTable saved = new StoreTable(id, table.storeId(), table.floor(), table.tableNo(),
                    table.displayName(), table.posX(), table.posY(), table.qrCode(), table.active());
            rows.put(id, saved);
            return saved;
        }

        @Override
        public boolean update(StoreTable table) {
            if (!rows.containsKey(table.id())) {
                return false;
            }
            rows.put(table.id(), table);
            return true;
        }

        @Override
        public boolean delete(long id) {
            return rows.remove(id) != null;
        }
    }
}
