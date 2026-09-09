package com.sunmoon.bo.domain.commoncode;

import java.util.List;
import java.util.Optional;

public interface CommonCodeRepository {

    List<CommonCode> findAll();

    List<CommonCode> findByGroup(String groupCode);

    Optional<CommonCode> find(String groupCode, String code);

    void insert(CommonCode commonCode);

    boolean update(CommonCode commonCode);

    boolean delete(String groupCode, String code);
}
