package dev.totem.locksmith.service;

import dev.totem.locksmith.domain.LockRecord;

import java.util.Optional;

public record ResolvedLock(Optional<LockRecord> record, Status status) {
    public static ResolvedLock unlocked() {
        return new ResolvedLock(Optional.empty(), Status.UNLOCKED);
    }

    public static ResolvedLock active(LockRecord record) {
        return new ResolvedLock(Optional.of(record), Status.ACTIVE);
    }

    public static ResolvedLock inconsistent(Optional<LockRecord> record) {
        return new ResolvedLock(record, Status.INCONSISTENT);
    }

    public enum Status {
        UNLOCKED,
        ACTIVE,
        INCONSISTENT
    }
}
