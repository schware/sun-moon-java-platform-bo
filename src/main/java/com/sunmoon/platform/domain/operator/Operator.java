package com.sunmoon.platform.domain.operator;

import java.util.List;

/**
 * {@code superAdmin} is the 전체 관리자 tier: it bypasses every
 * {@link OperatorScreenPermission} check. The other two tiers live in
 * {@code permissions} — which screens, and which actions within each.
 */
public record Operator(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        boolean superAdmin,
        boolean active,
        List<OperatorScreenPermission> permissions) {
}
