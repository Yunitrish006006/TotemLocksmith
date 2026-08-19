package dev.totem.locksmith.service;

import dev.totem.locksmith.component.KeyBinding;
import dev.totem.locksmith.component.LocksmithDataComponents;
import dev.totem.locksmith.domain.AccessActor;
import dev.totem.locksmith.domain.AccessDecision;
import dev.totem.locksmith.domain.AccessOperation;
import dev.totem.locksmith.domain.LockAccessPolicy;
import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.LockState;
import dev.totem.locksmith.integration.NexusFriendshipBridge;
import dev.totem.locksmith.persistence.LockMarkerAttachments;
import dev.totem.locksmith.persistence.LocksmithSavedData;
import dev.totem.locksmith.registry.LocksmithGameRules;
import dev.totem.locksmith.registry.LocksmithItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One shared policy boundary for player and known automation paths. */
public final class LocksmithAccessService {
    private LocksmithAccessService() {
    }

    public static ResolvedLock resolve(ServerLevel level, BlockPos position) {
        LockLocation location = LockLocation.of(level, position);
        Optional<LockRecord> indexed = LocksmithSavedData.forServer(level.getServer()).findAt(location);
        BlockEntity blockEntity = level.getBlockEntity(position);
        Optional<UUID> marker = blockEntity == null ? Optional.empty() : LockMarkerAttachments.read(blockEntity);
        if (indexed.isEmpty() && marker.isEmpty()) return ResolvedLock.unlocked();
        if (indexed.isPresent() && marker.isPresent()
                && indexed.get().id().equals(marker.get())
                && indexed.get().state() == LockState.ACTIVE) {
            return ResolvedLock.active(indexed.get());
        }
        return ResolvedLock.inconsistent(indexed);
    }

    public static AccessDecision playerDecision(ServerPlayer player, BlockPos position, AccessOperation operation) {
        if (!(player.level() instanceof ServerLevel level)) return AccessDecision.deny("server_only");
        ResolvedLock resolved = resolve(level, position);
        if (resolved.status() == ResolvedLock.Status.UNLOCKED) return AccessDecision.allow("unlocked");
        if (resolved.status() == ResolvedLock.Status.INCONSISTENT || resolved.record().isEmpty()) {
            return AccessDecision.deny("repair_required");
        }
        LockRecord record = resolved.record().orElseThrow();
        return LockAccessPolicy.evaluate(
                record,
                playerActor(player, record),
                operation,
                LocksmithGameRules.requirePhysicalKeys(level)
        );
    }

    public static boolean mayOpen(ServerPlayer player, BlockPos position) {
        return playerDecision(player, position, AccessOperation.OPEN).allowed();
    }

    /** True for active and inconsistent members; world mechanics must fail closed. */
    public static boolean isProtectedMember(ServerLevel level, BlockPos position) {
        return resolve(level, position).status() != ResolvedLock.Status.UNLOCKED;
    }

    public static boolean allowAutomationTransfer(Container source, Container destination) {
        return allowAutomationTransfer(source, destination, null);
    }

    public static boolean allowAutomationTransfer(Container source, Container destination, UUID operatorId) {
        Endpoint from = endpoint(source);
        Endpoint to = endpoint(destination);
        if (from.lockId().isPresent() && from.lockId().equals(to.lockId())) return true;
        AccessActor actor = operatorId == null
                ? AccessActor.anonymousAutomation() : AccessActor.identifiedAutomation(operatorId);
        if (!from.allowed(AccessOperation.EXTRACT, actor)) return false;
        return to.allowed(AccessOperation.INSERT, actor);
    }

    public static boolean allowAutomationAt(
            ServerLevel level,
            BlockPos position,
            AccessOperation operation,
            UUID operatorId
    ) {
        ResolvedLock resolved = resolve(level, position);
        if (resolved.status() == ResolvedLock.Status.UNLOCKED) return true;
        if (resolved.status() == ResolvedLock.Status.INCONSISTENT || resolved.record().isEmpty()) return false;
        AccessActor actor = operatorId == null
                ? AccessActor.anonymousAutomation() : AccessActor.identifiedAutomation(operatorId);
        return LockAccessPolicy.evaluate(resolved.record().orElseThrow(), actor, operation).allowed();
    }

    public static AccessActor playerActor(ServerPlayer player, LockRecord record) {
        Set<AccessActor.HeldKey> held = new HashSet<>();
        collectKey(player.getMainHandItem(), held);
        collectKey(player.getOffhandItem(), held);
        boolean friend = NexusFriendshipBridge.areMutualFriends(
                player.level().getServer(), player.getUUID(), record.ownerId());
        return AccessActor.player(player.getUUID(), held, friend, false);
    }

    private static void collectKey(ItemStack stack, Set<AccessActor.HeldKey> target) {
        if (!stack.is(LocksmithItems.BOUND_KEY)) return;
        KeyBinding binding = stack.get(LocksmithDataComponents.KEY_BINDING);
        if (binding != null) {
            target.add(new AccessActor.HeldKey(binding.lockId(), binding.keyId(), binding.epoch()));
        }
    }

    private static Endpoint endpoint(Container container) {
        if (!(container instanceof BlockEntity blockEntity)
                || !(blockEntity.getLevel() instanceof ServerLevel level)) {
            return Endpoint.unlocked();
        }
        ResolvedLock resolved = resolve(level, blockEntity.getBlockPos());
        if (resolved.status() == ResolvedLock.Status.UNLOCKED) return Endpoint.unlocked();
        if (resolved.status() == ResolvedLock.Status.INCONSISTENT || resolved.record().isEmpty()) {
            return Endpoint.inconsistent();
        }
        return Endpoint.locked(resolved.record().orElseThrow());
    }

    private record Endpoint(Optional<UUID> lockId, Optional<LockRecord> record, boolean consistent) {
        static Endpoint unlocked() {
            return new Endpoint(Optional.empty(), Optional.empty(), true);
        }

        static Endpoint inconsistent() {
            return new Endpoint(Optional.empty(), Optional.empty(), false);
        }

        static Endpoint locked(LockRecord record) {
            return new Endpoint(Optional.of(record.id()), Optional.of(record), true);
        }

        boolean allowed(AccessOperation operation, AccessActor actor) {
            if (!consistent) return false;
            return record.isEmpty() || LockAccessPolicy.evaluate(
                    record.orElseThrow(), actor, operation).allowed();
        }
    }
}
