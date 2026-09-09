package com.sunmoon.platform.domain.operator;

import java.util.Optional;

public interface OperatorRepository {

    Optional<Operator> findByUsername(String username);

    boolean existsAny();

    Operator create(String username, String passwordHash, String displayName, boolean superAdmin);
}
