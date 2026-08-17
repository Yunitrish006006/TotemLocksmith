package dev.totem.locksmith.service;

import dev.totem.locksmith.domain.LockRecord;

import java.util.Optional;

public record LockBreakResult(
        LockRecord before,
        Optional<LockRecord> after,
        int detachedUnlockedContainers,
        boolean rootMoved,
        boolean lockRemoved
) {
}
