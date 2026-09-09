package com.sunmoon.bo.domain.menu;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MenuPriceTest {

    private static MenuPrice row(String start, String end) {
        return new MenuPrice(1L, "menu-01", BigDecimal.TEN,
                LocalDate.parse(start), end == null ? null : LocalDate.parse(end));
    }

    @Test
    void coversIncludesBothEndpoints() {
        MenuPrice price = row("2026-01-01", "2026-01-31");
        assertThat(price.covers(LocalDate.parse("2026-01-01"))).isTrue();
        assertThat(price.covers(LocalDate.parse("2026-01-31"))).isTrue();
        assertThat(price.covers(LocalDate.parse("2026-02-01"))).isFalse();
        assertThat(price.covers(LocalDate.parse("2025-12-31"))).isFalse();
    }

    @Test
    void openEndedCoversEveryDateFromStartOnward() {
        MenuPrice price = row("2026-01-01", null);
        assertThat(price.covers(LocalDate.parse("2030-01-01"))).isTrue();
        assertThat(price.covers(LocalDate.parse("2025-12-31"))).isFalse();
    }

    @Test
    void adjacentRangesDoNotOverlap() {
        MenuPrice jan = row("2026-01-01", "2026-01-31");
        MenuPrice feb = row("2026-02-01", "2026-02-28");
        assertThat(jan.overlaps(feb)).isFalse();
        assertThat(feb.overlaps(jan)).isFalse();
    }

    @Test
    void anySharedDateIsAnOverlap() {
        MenuPrice jan = row("2026-01-01", "2026-01-31");
        MenuPrice straddling = row("2026-01-31", "2026-02-28");
        assertThat(jan.overlaps(straddling)).isTrue();
    }

    @Test
    void openEndedRowOverlapsAnythingAfterItStarts() {
        MenuPrice openEnded = row("2026-01-01", null);
        MenuPrice later = row("2030-01-01", "2030-12-31");
        assertThat(openEnded.overlaps(later)).isTrue();
    }
}
