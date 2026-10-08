package com.sunmoon.bo.domain.reprocess;

import com.sunmoon.bo.domain.UpstreamUnavailableException;
import com.sunmoon.bo.domain.posops.PosOps;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 세 서비스를 한 화면에 — 한 곳이 안 닿아도 나머지는 보이고, "다시" 는 그 서비스로 갑니다. */
class ReprocessBoardTest {

    static final class Fake implements ReprocessSource {
        final String service;
        final PosOps.Reprocess answer;
        final List<String> calls = new ArrayList<>();

        Fake(String service, PosOps.Reprocess answer) {
            this.service = service;
            this.answer = answer;
        }

        @Override public String service() { return service; }

        @Override public PosOps.Reprocess list(String storeId) {
            if (answer == null) {
                throw new UpstreamUnavailableException(service, new RuntimeException("connection refused"));
            }
            return answer;
        }

        @Override public PosOps.RetryOutcome retryOne(String kind, String storeId, String id) {
            calls.add("one " + kind + " " + id);
            return new PosOps.RetryOutcome(kind, id, true, true, service + " 에서 다시 했습니다");
        }

        @Override public PosOps.RetriedAll retryAll(String kind, String storeId) {
            calls.add("all " + kind);
            return new PosOps.RetriedAll(kind, 1, 0);
        }
    }

    private static PosOps.Reprocess answer(String kind, int count) {
        List<PosOps.FailedEntry> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            items.add(new PosOps.FailedEntry(String.valueOf(i), "store-01", "건 " + i, "사유", 1, Instant.EPOCH));
        }
        return new PosOps.Reprocess("store-01", List.of(new PosOps.ReprocessKind(kind, kind + " 라벨",
                kind.startsWith("ORDER_DUP") ? Boolean.FALSE : null, count, items)));
    }

    private final Fake pos = new Fake("POS-SERVER", answer("SALE", 0));
    private final Fake order = new Fake("ORDER", answer("ORDER_EVENT", 2));
    private final Fake app = new Fake("APP-SERVER", null);
    private final ReprocessBoard board = new ReprocessBoard(List.of(pos, order, app));

    @Test
    void oneServiceDownDoesNotHideTheOthers() {
        ReprocessBoard.Board view = board.list("store-01");

        assertThat(view.kinds()).extracting(ReprocessBoard.Kind::service).containsExactly("POS-SERVER", "ORDER");
        assertThat(view.kinds().get(1).count()).isEqualTo(2);
        assertThat(view.unreachable()).as("빈 목록이 아니라 '읽지 못함' 으로").containsExactly("APP-SERVER");
    }

    @Test
    void retryGoesToTheServiceThatOwnsIt() {
        board.retryOne("ORDER", "ORDER_EVENT", "store-01", "7");
        board.retryAll("ORDER", "ORDER_EVENT", null);

        assertThat(order.calls).containsExactly("one ORDER_EVENT 7", "all ORDER_EVENT");
        assertThat(pos.calls).isEmpty();
    }

    @Test
    void noServiceMeansPosServerAsBefore() {
        board.retryOne(null, "SALE", null, "3");

        assertThat(pos.calls).containsExactly("one SALE 3");
    }

    @Test
    void retryableDefaultsToTrueAndFindOnlyKindsSayFalse() {
        ReprocessBoard mixed = new ReprocessBoard(List.of(
                new Fake("POS-SERVER", answer("SALE", 0)), new Fake("ORDER", answer("ORDER_DUPLICATE", 1))));

        List<ReprocessBoard.Kind> kinds = mixed.list(null).kinds();

        assertThat(kinds.get(0).retryable()).as("칸이 없는 옛 응답은 참").isTrue();
        assertThat(kinds.get(1).retryable()).isFalse();
    }

    @Test
    void unknownServiceIsRefused() {
        assertThatThrownBy(() -> board.retryOne("NOPE", "SALE", null, "3"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
