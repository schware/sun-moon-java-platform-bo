package com.sunmoon.platform.domain.commoncode;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommonCodeService {

    private final CommonCodeRepository repository;

    public CommonCodeService(CommonCodeRepository repository) {
        this.repository = repository;
    }

    public List<CommonCode> list(String groupCode) {
        return groupCode == null || groupCode.isBlank()
                ? repository.findAll()
                : repository.findByGroup(groupCode);
    }

    /** 신규 — distinct from save, because 신규 and 저장 are distinct permissions. */
    public boolean create(CommonCode commonCode) {
        if (repository.find(commonCode.groupCode(), commonCode.code()).isPresent()) {
            return false;
        }
        repository.insert(commonCode);
        return true;
    }

    /** 저장 — updates an existing row only; never silently inserts. */
    public boolean save(CommonCode commonCode) {
        return repository.update(commonCode);
    }

    public boolean delete(String groupCode, String code) {
        return repository.delete(groupCode, code);
    }
}
