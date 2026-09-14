package com.sunmoon.bo.domain.operator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * What the 운영자 관리 screen posts.
 *
 * <p>{@code password} is optional on 저장 and required on 신규 — a blank
 * one on an edit means "leave it alone", which is what an admin editing
 * someone's screen permissions actually wants. That rule is in
 * {@link OperatorService} rather than in an annotation here, because the
 * same record serves both calls and bean validation cannot see which.
 */
public record OperatorRequest(
        Long id,
        @NotBlank
        @Pattern(regexp = "[a-zA-Z0-9._-]{3,64}",
                message = "username must be 3-64 characters of letters, digits, dot, underscore or hyphen")
        String username,
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        String password,
        @NotBlank String displayName,
        Boolean superAdmin,
        Boolean active,
        List<PermissionRequest> permissions) {

    public boolean wantsSuperAdmin() {
        return Boolean.TRUE.equals(superAdmin);
    }

    public boolean wantsActive() {
        return active == null || active;
    }

    public List<OperatorScreenPermission> toPermissions() {
        return permissions == null
                ? List.of()
                : permissions.stream().map(PermissionRequest::toDomain).toList();
    }

    /**
     * One row of the screen × action matrix. {@code screen} is a string
     * here and parsed in {@link #toDomain()}: an unknown name arriving as
     * a 400 is better than the enum binding failing somewhere in Spring's
     * converters with a message nobody can act on.
     */
    public record PermissionRequest(
            @NotBlank String screen,
            boolean canView,
            boolean canCreate,
            boolean canSave,
            boolean canDelete) {

        public OperatorScreenPermission toDomain() {
            try {
                return new OperatorScreenPermission(
                        Screen.valueOf(screen), canView, canCreate, canSave, canDelete);
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("No such screen: " + screen);
            }
        }
    }
}
