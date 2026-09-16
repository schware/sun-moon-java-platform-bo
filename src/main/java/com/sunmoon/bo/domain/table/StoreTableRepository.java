package com.sunmoon.bo.domain.table;

import java.util.List;
import java.util.Optional;

public interface StoreTableRepository {

    /** 한 매장의 전부. 층·번호 순입니다 — 배치도가 그 순서로 읽힙니다. */
    List<StoreTable> findByStore(String storeId);

    Optional<StoreTable> find(long id);

    Optional<StoreTable> findByQrCode(String qrCode);

    boolean existsQrCode(String qrCode);

    StoreTable insert(StoreTable table);

    boolean update(StoreTable table);

    boolean delete(long id);
}
