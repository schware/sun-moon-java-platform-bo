package com.sunmoon.bo.domain.sales;

import com.sunmoon.bo.domain.store.Store;
import com.sunmoon.bo.domain.store.StoreRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 매출 조회. All of the counting happens in Order; what BO adds is the one
 * thing Order cannot know — which shop {@code store-07} is.
 *
 * <p>The store lookup is one query for the whole page rather than one per
 * row: ten stores is the whole table, and an N+1 against the 기준 정보 a
 * screen already lists is not worth the shape it would set.
 */
@Service
public class SalesService {

    private final SalesRepository salesRepository;
    private final StoreRepository storeRepository;

    public SalesService(SalesRepository salesRepository, StoreRepository storeRepository) {
        this.salesRepository = salesRepository;
        this.storeRepository = storeRepository;
    }

    public List<SalesSummary> summarize(LocalDate from, LocalDate to, String storeId) {
        Map<String, String> names = storeNames();
        return salesRepository.summarize(from, to, storeId).stream()
                .map(row -> row.withStoreName(names.get(row.storeId())))
                .toList();
    }

    public List<SalesOrder> detail(LocalDate businessDate, String storeId) {
        return salesRepository.detail(businessDate, storeId);
    }

    private Map<String, String> storeNames() {
        return storeRepository.findAll().stream()
                .collect(Collectors.toMap(Store::storeId, Store::name, (first, second) -> first));
    }
}
