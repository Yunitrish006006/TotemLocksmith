package dev.totem.locksmith.domain;

public enum LockState {
    ACTIVE,
    REPAIR_REQUIRED,
    ORPHANED,
    CONFLICT
}
