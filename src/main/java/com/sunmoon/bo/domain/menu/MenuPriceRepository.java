package com.sunmoon.bo.domain.menu;

import java.util.List;

public interface MenuPriceRepository {

    List<MenuPrice> findByMenu(String menuCode);

    void insert(MenuPrice price);

    boolean delete(String menuCode, long priceId);

    boolean deleteAllForMenu(String menuCode);
}
