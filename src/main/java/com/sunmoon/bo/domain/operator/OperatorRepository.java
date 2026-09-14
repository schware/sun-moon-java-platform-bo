package com.sunmoon.bo.domain.operator;

import java.util.List;
import java.util.Optional;

public interface OperatorRepository {

    Optional<Operator> findByUsername(String username);

    boolean existsAny();

    Operator create(String username, String passwordHash, String displayName, boolean superAdmin);

    List<Operator> findAll();

    Optional<Operator> find(long id);

    /** Never touches the password — {@link #updatePassword} is its own call for that reason. */
    boolean update(long id, String displayName, boolean superAdmin, boolean active);

    boolean updatePassword(long id, String passwordHash);

    /**
     * Deletes every permission row for the operator and writes the given
     * ones. Replacing rather than merging: the screen posts the whole
     * matrix, and a merge would leave a screen granted that the operator
     * unticked.
     */
    void replacePermissions(long id, List<OperatorScreenPermission> permissions);

    boolean delete(long id);

    /**
     * How many 전체 관리자 accounts are still usable. Counted in the
     * database rather than by loading every operator, because the answer
     * decides whether a delete is allowed to happen at all.
     */
    int countActiveSuperAdmins();
}
