package com.sunmoon.bo.domain.operator;

public record OperatorScreenPermission(
        Screen screen, boolean canView, boolean canCreate, boolean canSave, boolean canDelete) {

    public boolean allows(Action action) {
        return switch (action) {
            case VIEW -> canView;
            case CREATE -> canCreate;
            case SAVE -> canSave;
            case DELETE -> canDelete;
        };
    }

    /**
     * The Spring Security authorities this permission grants, one per
     * allowed action, named {@code SCREEN_ACTION} so controllers can say
     * {@code @PreAuthorize("hasAuthority('COMMON_CODE_VIEW')")}.
     */
    public java.util.List<String> authorities() {
        java.util.List<String> granted = new java.util.ArrayList<>();
        for (Action action : Action.values()) {
            if (allows(action)) {
                granted.add(screen.name() + "_" + action.name());
            }
        }
        return granted;
    }
}
