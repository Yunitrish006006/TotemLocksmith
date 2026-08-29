package dev.totem.locksmith.menu;

import dev.totem.locksmith.component.KeyBinding;
import dev.totem.locksmith.component.LocksmithDataComponents;
import dev.totem.locksmith.domain.AccessMode;
import dev.totem.locksmith.domain.AutomationMode;
import dev.totem.locksmith.domain.KeyGrant;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.MemberEntry;
import dev.totem.locksmith.domain.MemberRole;
import dev.totem.locksmith.persistence.LocksmithSavedData;
import dev.totem.locksmith.registry.LocksmithItems;
import dev.totem.locksmith.service.LocksmithAuthority;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Command-free control plane for a single lock. Every mutation is re-resolved
 * against authoritative SavedData and the snapshot revision before it commits.
 */
public final class LocksmithManagementMenu extends AbstractContainerMenu {
    public static final int ACCESS_BASE = 0;
    public static final int AUTOMATION_BASE = 10;
    public static final int BIND_KEY = 20;
    public static final int ROTATE_KEYS = 21;
    public static final int REMOVE_LOCK = 22;
    public static final int MEMBER_ROLE_BASE = 100;
    public static final int MEMBER_REMOVE_BASE = 200;
    public static final int CANDIDATE_ADD_BASE = 300;
    public static final int KEY_REVOKE_BASE = 400;
    public static final int MAX_ROWS = 32;

    private LocksmithManagementOpenData snapshot;

    public LocksmithManagementMenu(
            MenuType<?> type,
            int containerId,
            Inventory inventory,
            LocksmithManagementOpenData snapshot
    ) {
        super(type, containerId);
        this.snapshot = snapshot;
    }

    public LocksmithManagementOpenData snapshot() {
        return snapshot;
    }

    /** Applies a newer, already-authorized read-only Observer projection. */
    public boolean applyObserverSnapshot(LocksmithManagementOpenData replacement) {
        if (replacement == null || !snapshot.lockId().equals(replacement.lockId())
                || replacement.revision() < snapshot.revision()) {
            return false;
        }
        snapshot = replacement;
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        return current(serverPlayer)
                .map(record -> mayManage(serverPlayer, record, false))
                .orElse(false);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer actor)) {
            return false;
        }
        Optional<LockRecord> found = current(actor);
        if (found.isEmpty()) {
            actor.closeContainer();
            return false;
        }
        LockRecord record = found.orElseThrow();
        if (record.revision() != snapshot.revision()) {
            reopen(actor, record.id());
            return false;
        }

        if (buttonId >= ACCESS_BASE && buttonId < ACCESS_BASE + AccessMode.values().length) {
            if (!mayManage(actor, record, true)) return denied(actor);
            AccessMode mode = AccessMode.values()[buttonId - ACCESS_BASE];
            return replaceAndRefresh(actor, record, record.withModes(mode, record.automationMode()));
        }
        if (buttonId >= AUTOMATION_BASE
                && buttonId < AUTOMATION_BASE + AutomationMode.values().length) {
            if (!mayManage(actor, record, true)) return denied(actor);
            AutomationMode mode = AutomationMode.values()[buttonId - AUTOMATION_BASE];
            return replaceAndRefresh(actor, record, record.withModes(record.accessMode(), mode));
        }
        if (buttonId == BIND_KEY) {
            return bindKey(actor, record);
        }
        if (buttonId == ROTATE_KEYS) {
            if (!mayManage(actor, record, true)) return denied(actor);
            return replaceAndRefresh(actor, record, record.rotateKeys());
        }
        if (buttonId == REMOVE_LOCK) {
            if (!mayManage(actor, record, true)) return denied(actor);
            if (!LocksmithAuthority.removeLock(actor, record)) {
                return stale(actor);
            }
            actor.closeContainer();
            actor.sendSystemMessage(Component.translatable("message.totem.locksmith.removed"));
            return true;
        }
        if (buttonId >= MEMBER_ROLE_BASE && buttonId < MEMBER_ROLE_BASE + MAX_ROWS) {
            return cycleMemberRole(actor, record, buttonId - MEMBER_ROLE_BASE);
        }
        if (buttonId >= MEMBER_REMOVE_BASE && buttonId < MEMBER_REMOVE_BASE + MAX_ROWS) {
            return removeMember(actor, record, buttonId - MEMBER_REMOVE_BASE);
        }
        if (buttonId >= CANDIDATE_ADD_BASE && buttonId < CANDIDATE_ADD_BASE + MAX_ROWS) {
            return addCandidate(actor, record, buttonId - CANDIDATE_ADD_BASE);
        }
        if (buttonId >= KEY_REVOKE_BASE && buttonId < KEY_REVOKE_BASE + MAX_ROWS) {
            return revokeKey(actor, record, buttonId - KEY_REVOKE_BASE);
        }
        return false;
    }

    private boolean bindKey(ServerPlayer actor, LockRecord record) {
        if (!mayManage(actor, record, false)) return denied(actor);
        InteractionHand hand = actor.getMainHandItem().is(LocksmithItems.KEY_BLANK)
                ? InteractionHand.MAIN_HAND
                : actor.getOffhandItem().is(LocksmithItems.KEY_BLANK)
                ? InteractionHand.OFF_HAND
                : null;
        if (hand == null) {
            actor.sendOverlayMessage(Component.translatable("message.totem.locksmith.need_key_blank"));
            return false;
        }

        UUID keyId = UUID.randomUUID();
        List<KeyGrant> keys = new ArrayList<>(record.keys());
        keys.add(new KeyGrant(keyId, "Key " + (keys.size() + 1), record.keyEpoch()));
        LockRecord replacement;
        try {
            replacement = record.withKeys(keys);
        } catch (IllegalArgumentException limit) {
            actor.sendOverlayMessage(Component.translatable("message.totem.locksmith.key_limit"));
            return false;
        }
        if (!data(actor).replace(record, replacement)) return stale(actor);

        if (!actor.getAbilities().instabuild) {
            actor.getItemInHand(hand).shrink(1);
        }
        ItemStack key = new ItemStack(LocksmithItems.BOUND_KEY);
        key.set(LocksmithDataComponents.KEY_BINDING,
                new KeyBinding(record.id(), keyId, record.keyEpoch()));
        if (!actor.getInventory().add(key)) {
            actor.drop(key, false);
        }
        reopen(actor, replacement.id());
        return true;
    }

    private boolean cycleMemberRole(ServerPlayer actor, LockRecord record, int index) {
        if (!mayManage(actor, record, false) || index < 0 || index >= record.members().size()) {
            return false;
        }
        MemberEntry target = record.members().get(index);
        boolean actorOwner = record.ownerId().equals(actor.getUUID());
        if (target.role() == MemberRole.MANAGER && !actorOwner) return denied(actor);

        MemberRole next;
        if (actorOwner) {
            next = switch (target.role()) {
                case MANAGER -> MemberRole.USER;
                case USER -> MemberRole.BLOCKED;
                case BLOCKED -> MemberRole.MANAGER;
            };
        } else {
            next = target.role() == MemberRole.USER ? MemberRole.BLOCKED : MemberRole.USER;
        }
        List<MemberEntry> members = new ArrayList<>(record.members());
        members.set(index, new MemberEntry(target.playerId(), target.lastKnownName(), next));
        return replaceAndRefresh(actor, record, record.withMembers(members));
    }

    private boolean removeMember(ServerPlayer actor, LockRecord record, int index) {
        if (!mayManage(actor, record, false) || index < 0 || index >= record.members().size()) {
            return false;
        }
        MemberEntry target = record.members().get(index);
        if (target.role() == MemberRole.MANAGER && !record.ownerId().equals(actor.getUUID())) {
            return denied(actor);
        }
        List<MemberEntry> members = new ArrayList<>(record.members());
        members.remove(index);
        return replaceAndRefresh(actor, record, record.withMembers(members));
    }

    private boolean addCandidate(ServerPlayer actor, LockRecord record, int index) {
        if (!mayManage(actor, record, false) || index < 0 || index >= snapshot.candidates().size()) {
            return false;
        }
        LocksmithManagementOpenData.PlayerView candidate = snapshot.candidates().get(index);
        ServerPlayer target = actor.level().getServer().getPlayerList().getPlayer(candidate.playerId());
        if (target == null || target.getUUID().equals(record.ownerId())
                || record.member(target.getUUID()).isPresent()) {
            reopen(actor, record.id());
            return false;
        }
        List<MemberEntry> members = new ArrayList<>(record.members());
        members.add(new MemberEntry(target.getUUID(), target.getGameProfile().name(), MemberRole.USER));
        try {
            return replaceAndRefresh(actor, record, record.withMembers(members));
        } catch (IllegalArgumentException limit) {
            actor.sendOverlayMessage(Component.translatable("message.totem.locksmith.limit"));
            return false;
        }
    }

    private boolean revokeKey(ServerPlayer actor, LockRecord record, int index) {
        if (!mayManage(actor, record, false) || index < 0 || index >= record.keys().size()) {
            return false;
        }
        List<KeyGrant> keys = new ArrayList<>(record.keys());
        keys.remove(index);
        return replaceAndRefresh(actor, record, record.withKeys(keys));
    }

    private boolean replaceAndRefresh(ServerPlayer actor, LockRecord before, LockRecord after) {
        if (after.equals(before)) return false;
        if (!data(actor).replace(before, after)) return stale(actor);
        reopen(actor, after.id());
        return true;
    }

    private Optional<LockRecord> current(ServerPlayer actor) {
        return data(actor).get(snapshot.lockId());
    }

    private static LocksmithSavedData data(ServerPlayer actor) {
        return LocksmithSavedData.forServer(actor.level().getServer());
    }

    private static boolean mayManage(ServerPlayer actor, LockRecord record, boolean ownerOnly) {
        if (record.ownerId().equals(actor.getUUID())) return true;
        return !ownerOnly && record.member(actor.getUUID())
                .map(entry -> entry.role() == MemberRole.MANAGER)
                .orElse(false);
    }

    private static void reopen(ServerPlayer actor, UUID lockId) {
        LocksmithManagementMenuOpener.open(actor, lockId);
    }

    private static boolean denied(ServerPlayer actor) {
        actor.sendOverlayMessage(Component.translatable("message.totem.locksmith.denied"));
        return false;
    }

    private static boolean stale(ServerPlayer actor) {
        actor.sendOverlayMessage(Component.translatable("message.totem.locksmith.stale"));
        return false;
    }
}
