package com.sunmoon.bo.domain.store;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StoreService {

    private final StoreRepository repository;

    public StoreService(StoreRepository repository) {
        this.repository = repository;
    }

    public List<Store> list() {
        return repository.findAll();
    }

    public boolean create(Store store) {
        if (repository.find(store.storeId()).isPresent()) {
            return false;
        }
        repository.insert(store);
        return true;
    }

    public boolean save(Store store) {
        return repository.update(store);
    }

    public boolean delete(String storeId) {
        return repository.delete(storeId);
    }
}
