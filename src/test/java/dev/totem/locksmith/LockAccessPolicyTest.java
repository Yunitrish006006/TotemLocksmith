package dev.totem.locksmith;

import dev.totem.locksmith.domain.AccessActor;
import dev.totem.locksmith.domain.AccessMode;
import dev.totem.locksmith.domain.AccessOperation;
import dev.totem.locksmith.domain.AutomationMode;
import dev.totem.locksmith.domain.BreakDisposition;
import dev.totem.locksmith.domain.ContainerKind;
import dev.totem.locksmith.domain.KeyGrant;
import dev.totem.locksmith.domain.LockAccessPolicy;
import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.LockState;
import dev.totem.locksmith.domain.LogicalContainerNode;
import dev.totem.locksmith.domain.MemberEntry;
import dev.totem.locksmith.domain.MemberRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockAccessPolicyTest {
    private static final UUID LOCK = UUID.fromString("0b6ee534-4db4-4f3c-8c48-ecda51e84134");
    private static final UUID OWNER = UUID.fromString("20607b86-2867-4bc1-8566-8ff84e2c69a7");
    private static final UUID ACTOR = UUID.fromString("0e71f3e3-e747-4426-bead-516502bc2ba8");
    private static final UUID KEY = UUID.fromString("82c3cae3-a31f-4c4d-8c87-53cb93cfc352");

    @Test
    void fixedPriorityKeepsOwnerAndAdminAboveBlocked() {
        LockRecord record = record(AccessMode.PUBLIC, AutomationMode.ALL,
                List.of(new MemberEntry(ACTOR, "Actor", MemberRole.BLOCKED)),
                List.of(new KeyGrant(KEY, "Front", 2)), 2);
        assertTrue(LockAccessPolicy.evaluate(record,
                AccessActor.player(OWNER, Set.of(), false, false), AccessOperation.OPEN).allowed());
        assertTrue(LockAccessPolicy.evaluate(record,
                AccessActor.player(ACTOR, Set.of(), false, true), AccessOperation.OPEN).allowed());
    }

    @Test
    void blockedOverridesValidKeyFriendAndPublic() {
        LockRecord record = record(AccessMode.PUBLIC, AutomationMode.ALL,
                List.of(new MemberEntry(ACTOR, "Actor", MemberRole.BLOCKED)),
                List.of(new KeyGrant(KEY, "Front", 2)), 2);
        AccessActor actor = AccessActor.player(ACTOR,
                Set.of(new AccessActor.HeldKey(LOCK, KEY, 2)), true, false);
        assertFalse(LockAccessPolicy.evaluate(record, actor, AccessOperation.OPEN).allowed());
    }

    @Test
    void keyMustMatchLockGrantAndEpoch() {
        LockRecord record = record(AccessMode.PRIVATE, AutomationMode.DENY, List.of(),
                List.of(new KeyGrant(KEY, "Front", 2)), 2);
        assertTrue(LockAccessPolicy.evaluate(record, AccessActor.player(ACTOR,
                Set.of(new AccessActor.HeldKey(LOCK, KEY, 2)), false, false), AccessOperation.OPEN).allowed());
        assertFalse(LockAccessPolicy.evaluate(record, AccessActor.player(ACTOR,
                Set.of(new AccessActor.HeldKey(LOCK, KEY, 1)), false, false), AccessOperation.OPEN).allowed());
        assertFalse(LockAccessPolicy.evaluate(record, AccessActor.player(ACTOR,
                Set.of(new AccessActor.HeldKey(UUID.randomUUID(), KEY, 2)), false, false), AccessOperation.OPEN).allowed());
    }

    @Test
    void immersiveModeRequiresPhysicalKeyWhileConvenientModeAllowsFriends() {
        LockRecord record = record(AccessMode.FRIENDS, AutomationMode.DENY, List.of(),
                List.of(new KeyGrant(KEY, "Front", 2)), 2);
        AccessActor friendWithoutKey = AccessActor.player(ACTOR, Set.of(), true, false);
        assertTrue(LockAccessPolicy.evaluate(record, friendWithoutKey, AccessOperation.OPEN, false).allowed());
        assertFalse(LockAccessPolicy.evaluate(record, friendWithoutKey, AccessOperation.OPEN, true).allowed());
        AccessActor friendWithKey = AccessActor.player(ACTOR,
                Set.of(new AccessActor.HeldKey(LOCK, KEY, 2)), true, false);
        assertTrue(LockAccessPolicy.evaluate(record, friendWithKey, AccessOperation.OPEN, true).allowed());
    }

    @Test
    void immersiveModeAlsoSuppressesAllowlistAndPublicBypass() {
        AccessActor actor = AccessActor.player(ACTOR, Set.of(), false, false);
        LockRecord allowlist = record(AccessMode.ALLOWLIST, AutomationMode.DENY,
                List.of(new MemberEntry(ACTOR, "Actor", MemberRole.USER)), List.of(), 0);
        LockRecord publicLock = record(AccessMode.PUBLIC, AutomationMode.DENY, List.of(), List.of(), 0);
        assertFalse(LockAccessPolicy.evaluate(allowlist, actor, AccessOperation.OPEN, true).allowed());
        assertFalse(LockAccessPolicy.evaluate(publicLock, actor, AccessOperation.OPEN, true).allowed());
    }

    @Test
    void automationModeAndBreakDispositionAreExplicit() {
        LockRecord deny = record(AccessMode.PRIVATE, AutomationMode.DENY, List.of(), List.of(), 0);
        assertFalse(LockAccessPolicy.evaluate(deny, AccessActor.anonymousAutomation(), AccessOperation.INSERT).allowed());
        LockRecord all = deny.withModes(AccessMode.PRIVATE, AutomationMode.ALL);
        assertTrue(LockAccessPolicy.evaluate(all, AccessActor.anonymousAutomation(), AccessOperation.EXTRACT).allowed());
        assertEquals(BreakDisposition.OWNER_BREAK, LockAccessPolicy.evaluate(deny,
                AccessActor.player(OWNER, Set.of(), false, false), AccessOperation.BREAK).breakDisposition());
        assertEquals(BreakDisposition.NON_OWNER_ALERT, LockAccessPolicy.evaluate(deny,
                AccessActor.player(ACTOR, Set.of(), false, false), AccessOperation.BREAK).breakDisposition());
    }

    private static LockRecord record(
            AccessMode access,
            AutomationMode automation,
            List<MemberEntry> members,
            List<KeyGrant> keys,
            int epoch
    ) {
        LockLocation root = new LockLocation("minecraft:overworld", 1, 64, 2);
        return new LockRecord(LOCK, OWNER, "Owner", root,
                List.of(new LogicalContainerNode(ContainerKind.CHEST, List.of(root))), List.of(),
                access, automation, members, keys, epoch, 1L, LockState.ACTIVE, 1);
    }
}
