package com.sunmoon.bo.domain.store;

import java.util.List;
import java.util.Optional;

public interface StoreRepository {

    List<Store> findAll();

    Optional<Store> find(String storeId);

    void insert(Store store);

    boolean update(Store store);

    boolean delete(String storeId);
}
