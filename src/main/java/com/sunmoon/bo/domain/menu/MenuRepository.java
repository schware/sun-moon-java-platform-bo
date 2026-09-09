package com.sunmoon.bo.domain.menu;

import java.util.List;
import java.util.Optional;

public interface MenuRepository {

    List<MenuSummary> findAllWithCurrentPrice();

    Optional<Menu> find(String menuCode);

    void insert(Menu menu);

    boolean update(Menu menu);

    boolean delete(String menuCode);
}
