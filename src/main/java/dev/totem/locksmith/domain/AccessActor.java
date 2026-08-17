package dev.totem.locksmith.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable Server-resolved actor. Client-supplied role or identity is never accepted. */
public record AccessActor(
        Kind kind,
        UUID playerId,
        Set<HeldKey> heldKeys,
        boolean mutualFriend,
        boolean administrator
) {
    public AccessActor {
        kind = Objects.requireNonNull(kind, "kind");
        heldKeys = Set.copyOf(heldKeys == null ? Set.of() : heldKeys);
        if ((kind == Kind.PLAYER || kind == Kind.IDENTIFIED_AUTOMATION || kind == Kind.ADMIN)
                && playerId == null) {
            throw new IllegalArgumentException("identified actor requires playerId");
        }
    }

    public static AccessActor player(UUID playerId, Set<HeldKey> keys, boolean friend, boolean admin) {
        return new AccessActor(Kind.PLAYER, playerId, keys, friend, admin);
    }

    public static AccessActor anonymousAutomation() {
        return new AccessActor(Kind.ANONYMOUS_AUTOMATION, null, Set.of(), false, false);
    }

    public static AccessActor identifiedAutomation(UUID operator) {
        return new AccessActor(Kind.IDENTIFIED_AUTOMATION, operator, Set.of(), false, false);
    }

    public enum Kind {
        PLAYER,
        IDENTIFIED_AUTOMATION,
        ANONYMOUS_AUTOMATION,
        ENVIRONMENT,
        ADMIN
    }

    public record HeldKey(UUID lockId, UUID keyId, int epoch) {
        public HeldKey {
            Objects.requireNonNull(lockId, "lockId");
            Objects.requireNonNull(keyId, "keyId");
        }
    }
}
