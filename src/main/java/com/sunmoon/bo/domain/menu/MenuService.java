package com.sunmoon.bo.domain.menu;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class MenuService {

    private final MenuRepository menus;
    private final MenuPriceRepository prices;

    public MenuService(MenuRepository menus, MenuPriceRepository prices) {
        this.menus = menus;
        this.prices = prices;
    }

    public List<MenuSummary> list() {
        return menus.findAllWithCurrentPrice();
    }

    public boolean create(Menu menu) {
        if (menus.find(menu.menuCode()).isPresent()) {
            return false;
        }
        menus.insert(menu);
        return true;
    }

    public boolean save(Menu menu) {
        return menus.update(menu);
    }

    /** Price history goes with the menu — nothing should reference a menu_code that no longer exists. */
    public boolean delete(String menuCode) {
        prices.deleteAllForMenu(menuCode);
        return menus.delete(menuCode);
    }

    public Optional<Menu> find(String menuCode) {
        return menus.find(menuCode);
    }

    public List<MenuPrice> priceHistory(String menuCode) {
        return prices.findByMenu(menuCode);
    }

    /**
     * The one rule that makes this a history rather than a list: two rows
     * for the same menu may never cover the same date.
     */
    public PriceAddResult addPrice(String menuCode, MenuPrice price) {
        if (menus.find(menuCode).isEmpty()) {
            return PriceAddResult.NO_SUCH_MENU;
        }
        boolean overlaps = prices.findByMenu(menuCode).stream().anyMatch(existing -> existing.overlaps(price));
        if (overlaps) {
            return PriceAddResult.OVERLAPS_EXISTING;
        }
        prices.insert(price);
        return PriceAddResult.OK;
    }

    public boolean deletePrice(String menuCode, long priceId) {
        return prices.delete(menuCode, priceId);
    }

    /** "메뉴 연동시" — the price to use for a given date, resolved from history. */
    public Optional<MenuPrice> effectivePrice(String menuCode, LocalDate date) {
        return prices.findByMenu(menuCode).stream().filter(p -> p.covers(date)).findFirst();
    }

    public enum PriceAddResult { OK, NO_SUCH_MENU, OVERLAPS_EXISTING }
}
