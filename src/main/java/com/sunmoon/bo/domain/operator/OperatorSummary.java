package com.sunmoon.bo.domain.operator;

import java.util.List;

/**
 * An operator as the 운영자 관리 screen shows one — {@link Operator} minus
 * the password hash.
 *
 * <p>The omission is the whole reason this record exists. {@code Operator}
 * carries {@code passwordHash}, and returning it from a controller would
 * serialize a BCrypt hash to every browser with the 운영자 screen open. A
 * separate type makes that impossible rather than merely discouraged: no
 * {@code @JsonIgnore} to forget, no field to accidentally re-add.
 */
public record OperatorSummary(
        Long id,
        String username,
        String displayName,
        boolean superAdmin,
        boolean active,
        List<OperatorScreenPermission> permissions) {

    public static OperatorSummary of(Operator operator) {
        return new OperatorSummary(operator.id(), operator.username(), operator.displayName(),
                operator.superAdmin(), operator.active(), operator.permissions());
    }
}
