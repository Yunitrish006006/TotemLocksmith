package dev.totem.locksmith.domain;

import java.util.UUID;

/** Fixed-priority policy shared by player menus, commands, Hoppers, and optional adapters. */
public final class LockAccessPolicy {
    private LockAccessPolicy() {
    }

    public static AccessDecision evaluate(LockRecord record, AccessActor actor, AccessOperation operation) {
        if (record.state() != LockState.ACTIVE) return AccessDecision.deny("repair_required");
        UUID actorId = actor.playerId();
        boolean owner = actorId != null && record.ownerId().equals(actorId);
        if (operation == AccessOperation.BREAK) {
            return AccessDecision.breakAllowed(
                    owner ? BreakDisposition.OWNER_BREAK : BreakDisposition.NON_OWNER_ALERT,
                    owner ? "owner" : "non_owner_alert"
            );
        }
        if (actor.administrator() || actor.kind() == AccessActor.Kind.ADMIN) {
            return AccessDecision.allow("administrator");
        }
        if (owner) return AccessDecision.allow("owner");

        MemberRole role = actorId == null ? null : record.member(actorId).map(MemberEntry::role).orElse(null);
        if (role == MemberRole.BLOCKED) return AccessDecision.deny("blocked");
        if (role == MemberRole.MANAGER) return AccessDecision.allow("manager");

        if (actor.kind() == AccessActor.Kind.ANONYMOUS_AUTOMATION
                || actor.kind() == AccessActor.Kind.IDENTIFIED_AUTOMATION) {
            if (operation != AccessOperation.INSERT && operation != AccessOperation.EXTRACT) {
                return AccessDecision.deny("automation_operation");
            }
            return switch (record.automationMode()) {
                case DENY -> AccessDecision.deny("automation_denied");
                case TRUSTED -> actor.kind() == AccessActor.Kind.IDENTIFIED_AUTOMATION
                        ? AccessDecision.allow("trusted_automation")
                        : AccessDecision.deny("automation_unidentified");
                case ALL -> AccessDecision.allow("automation_all");
            };
        }

        if (operation == AccessOperation.CONFIGURE) return AccessDecision.deny("owner_or_manager_only");
        boolean key = actor.heldKeys().stream().anyMatch(held -> held.lockId().equals(record.id())
                && record.isActiveKey(held.keyId(), held.epoch()));
        if (key) return AccessDecision.allow("key");

        return switch (record.accessMode()) {
            case PRIVATE -> AccessDecision.deny("private");
            case ALLOWLIST -> role == MemberRole.USER
                    ? AccessDecision.allow("user") : AccessDecision.deny("not_allowlisted");
            case FRIENDS -> actor.mutualFriend()
                    ? AccessDecision.allow("friend") : AccessDecision.deny("not_friend");
            case PUBLIC -> AccessDecision.allow("public");
        };
    }
}
