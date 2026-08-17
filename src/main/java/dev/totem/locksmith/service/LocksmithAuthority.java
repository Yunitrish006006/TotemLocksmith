package dev.totem.locksmith.service;

import dev.totem.core.api.v1.event.LockedContainerNetworkBrokenEvent;
import dev.totem.core.api.v1.event.TotemEventBus;
import dev.totem.locksmith.TotemLocksmith;
import dev.totem.locksmith.config.LocksmithConfig;
import dev.totem.locksmith.domain.AccessOperation;
import dev.totem.locksmith.domain.BreakDisposition;
import dev.totem.locksmith.domain.LockAccessPolicy;
import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.LockState;
import dev.totem.locksmith.domain.LogicalContainerNode;
import dev.totem.locksmith.persistence.LockMarkerAttachments;
import dev.totem.locksmith.persistence.LocksmithSavedData;
import dev.totem.locksmith.registry.LocksmithItems;
import dev.totem.locksmith.topology.FixedContainerTopologyResolver;
import dev.totem.locksmith.topology.LockTopology;
import dev.totem.locksmith.topology.TopologyScanResult;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-thread transactions for applying and finalizing one physical lock. */
public final class LocksmithAuthority {
    private LocksmithAuthority() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockPos position = hit.getBlockPos();
            ItemStack held = player.getItemInHand(hand);
            boolean supported = FixedContainerTopologyResolver.isSupportedContainerState(world.getBlockState(position));
            if (supported && held.is(LocksmithItems.PADLOCK)) {
                if (world.isClientSide()) return InteractionResult.SUCCESS;
                return applyPadlock((ServerPlayer) player, (ServerLevel) world, position, hand)
                        ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            if (world.isClientSide() || !(world instanceof ServerLevel level)) return InteractionResult.PASS;
            ResolvedLock resolved = LocksmithAccessService.resolve(level, position);
            if (resolved.status() == ResolvedLock.Status.UNLOCKED) return InteractionResult.PASS;
            if (player.isShiftKeyDown() && held.isEmpty() && resolved.record().isPresent()) {
                LockRecord record = resolved.record().orElseThrow();
                if (record.ownerId().equals(player.getUUID())
                        || record.member(player.getUUID()).map(entry -> entry.role().name().equals("MANAGER")).orElse(false)) {
                    player.sendSystemMessage(Component.translatable(
                            "message.totem.locksmith.inspect",
                            record.logicalContainerCount(), record.connectors().size(),
                            record.accessMode().name(), record.automationMode().name(), record.revision()));
                    return InteractionResult.SUCCESS;
                }
            }
            if (!LocksmithAccessService.playerDecision((ServerPlayer) player, position, AccessOperation.OPEN).allowed()) {
                player.sendOverlayMessage(Component.translatable("message.totem.locksmith.denied"));
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, position, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return;
            finalizeBrokenMember(level, serverPlayer, position, state, blockEntity);
        });
    }

    public static boolean applyPadlock(
            ServerPlayer player,
            ServerLevel level,
            BlockPos position,
            InteractionHand hand
    ) {
        LocksmithConfig config = LocksmithConfig.active();
        TopologyScanResult scan = FixedContainerTopologyResolver.scan(level, position, config.maxNetworkPositions());
        if (!scan.successful()) {
            player.sendOverlayMessage(Component.translatable(
                    "message.totem.locksmith.apply_failed." + scan.error()));
            return false;
        }
        LockTopology topology = scan.topology().orElseThrow();
        LocksmithSavedData data = LocksmithSavedData.forServer(level.getServer());
        if (data.ownerLockCount(player.getUUID()) >= config.maxLocksPerOwner()) {
            player.sendOverlayMessage(Component.translatable("message.totem.locksmith.apply_failed.owner_limit"));
            return false;
        }
        for (LockLocation location : topology.allPositions()) {
            BlockEntity blockEntity = level.getBlockEntity(location.blockPos());
            if (data.findAt(location).isPresent()
                    || (blockEntity != null && LockMarkerAttachments.read(blockEntity).isPresent())) {
                player.sendOverlayMessage(Component.translatable("message.totem.locksmith.apply_failed.already_locked"));
                return false;
            }
        }
        for (LogicalContainerNode node : topology.containers()) {
            for (LockLocation location : node.positions()) {
                BlockEntity blockEntity = level.getBlockEntity(location.blockPos());
                if (!(blockEntity instanceof BaseContainerBlockEntity container)) return false;
                if (container.isLocked()) {
                    player.sendOverlayMessage(Component.translatable("message.totem.locksmith.apply_failed.vanilla_lock"));
                    return false;
                }
                if (!config.allowUnpackedLootTables()
                        && container instanceof RandomizableContainerBlockEntity randomizable
                        && randomizable.getLootTable() != null) {
                    player.sendOverlayMessage(Component.translatable("message.totem.locksmith.apply_failed.loot_table"));
                    return false;
                }
            }
        }
        LockLocation clicked = LockLocation.of(level, position);
        LockLocation root = topology.containers().stream()
                .filter(node -> node.contains(clicked))
                .findFirst().map(LogicalContainerNode::anchor).orElse(clicked);
        LockRecord record = LockRecord.create(UUID.randomUUID(), player.getUUID(),
                player.getGameProfile().name(), root, topology.containers(), topology.connectors());
        if (!data.create(record)) return false;
        List<BlockEntity> marked = new ArrayList<>();
        try {
            for (LockLocation location : record.allPositions()) {
                BlockEntity blockEntity = level.getBlockEntity(location.blockPos());
                if (blockEntity == null) throw new IllegalStateException("member disappeared while applying lock");
                LockMarkerAttachments.write(blockEntity, record.id());
                marked.add(blockEntity);
            }
        } catch (RuntimeException exception) {
            marked.forEach(LockMarkerAttachments::clear);
            data.remove(record.id());
            TotemLocksmith.LOGGER.warn("Rolled back partial lock marker transaction", exception);
            return false;
        }
        if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
        player.sendOverlayMessage(Component.translatable(
                "message.totem.locksmith.applied", record.logicalContainerCount(), record.connectors().size()));
        award(player, "deadrecall:locksmith/locked_network");
        return true;
    }

    /** Preflight for a supported BlockItem placement before vanilla mutates the world or stack. */
    public static PlacementGuard preparePlacement(
            ServerPlayer player,
            ServerLevel level,
            BlockPos position,
            BlockState proposedState,
            InteractionHand hand,
            ItemStack itemBeforePlacement
    ) {
        if (!isTopologyMember(proposedState)) return PlacementGuard.unrelated();
        BlockState previousState = level.getBlockState(position);
        TopologyScanResult scan = FixedContainerTopologyResolver.scanIncludingPlacement(
                level, position, proposedState, LocksmithConfig.active().maxNetworkPositions());
        if (!scan.successful()) {
            if ("no_container".equals(scan.error()) && !hasClaimAtOrNextTo(level, position)) {
                return PlacementGuard.allowed(position, previousState, hand,
                        itemBeforePlacement, Optional.empty(), -1L);
            }
            return PlacementGuard.denied(position, previousState, hand,
                    itemBeforePlacement, scan.error());
        }
        PlacementEvaluation evaluation = evaluatePlacement(
                player, level, scan.topology().orElseThrow(), position, true);
        if (!evaluation.allowed()) {
            return PlacementGuard.denied(
                    position, previousState, hand, itemBeforePlacement, evaluation.reason());
        }
        return PlacementGuard.allowed(
                position,
                previousState,
                hand,
                itemBeforePlacement,
                evaluation.record().map(LockRecord::id),
                evaluation.record().map(LockRecord::revision).orElse(-1L)
        );
    }

    /** Commits owner-approved topology expansion after vanilla reports a successful placement. */
    public static boolean commitPlacement(
            ServerPlayer player,
            ServerLevel level,
            PlacementGuard guard
    ) {
        if (!guard.relevant() || !guard.allowed()) return guard.allowed();
        TopologyScanResult scan = FixedContainerTopologyResolver.scan(
                level, guard.position(), LocksmithConfig.active().maxNetworkPositions());
        if (!scan.successful()) {
            // A standalone Hopper is not part of any protected storage network.
            return guard.expectedLockId().isEmpty() && "no_container".equals(scan.error())
                    && !hasClaimAtOrNextTo(level, guard.position());
        }
        LockTopology topology = scan.topology().orElseThrow();
        PlacementEvaluation evaluation = evaluatePlacement(
                player, level, topology, null, false);
        if (!evaluation.allowed()) return false;
        Optional<LockRecord> candidate = evaluation.record();
        if (guard.expectedLockId().isPresent()) {
            if (candidate.isEmpty()
                    || !candidate.get().id().equals(guard.expectedLockId().orElseThrow())
                    || candidate.get().revision() != guard.expectedRevision()) {
                return false;
            }
        } else if (candidate.isEmpty()) {
            return true;
        }
        if (candidate.isEmpty()) return false;

        LockRecord before = candidate.orElseThrow();
        LockRecord replacement = before.withTopology(
                before.rootContainer(), topology.containers(), topology.connectors());
        LocksmithSavedData data = LocksmithSavedData.forServer(level.getServer());
        Map<LockLocation, Optional<UUID>> priorMarkers = new HashMap<>();
        for (LockLocation location : replacement.allPositions()) {
            BlockEntity blockEntity = level.getBlockEntity(location.blockPos());
            if (blockEntity == null) return false;
            priorMarkers.put(location, LockMarkerAttachments.read(blockEntity));
        }
        if (!data.replace(before, replacement)) return false;
        try {
            replacement.allPositions().forEach(location -> writeMarker(level, location, replacement.id()));
            return true;
        } catch (RuntimeException exception) {
            data.replace(replacement, before);
            priorMarkers.forEach((location, marker) -> {
                BlockEntity blockEntity = level.getBlockEntity(location.blockPos());
                if (blockEntity == null) return;
                marker.ifPresentOrElse(
                        id -> LockMarkerAttachments.write(blockEntity, id),
                        () -> LockMarkerAttachments.clear(blockEntity));
            });
            TotemLocksmith.LOGGER.warn("Rolled back failed lock topology expansion", exception);
            return false;
        }
    }

    public static void rollbackPlacement(ServerPlayer player, ServerLevel level, PlacementGuard guard) {
        BlockEntity placed = level.getBlockEntity(guard.position());
        if (placed != null) LockMarkerAttachments.clear(placed);
        level.setBlock(guard.position(), guard.previousState(), Block.UPDATE_ALL);
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(guard.hand(), guard.itemBeforePlacement().copy());
        }
    }

    public static Optional<LockBreakResult> finalizeBrokenMember(
            ServerLevel level,
            ServerPlayer actor,
            BlockPos brokenPosition,
            BlockState brokenState,
            BlockEntity removedBlockEntity
    ) {
        LocksmithSavedData data = LocksmithSavedData.forServer(level.getServer());
        LockLocation brokenLocation = LockLocation.of(level, brokenPosition);
        Optional<LockRecord> found = data.findAt(brokenLocation);
        if (found.isEmpty() && removedBlockEntity != null) {
            found = LockMarkerAttachments.read(removedBlockEntity).flatMap(data::get);
        }
        if (found.isEmpty()) return Optional.empty();
        LockRecord before = found.orElseThrow();
        if (removedBlockEntity != null) LockMarkerAttachments.clear(removedBlockEntity);

        List<LockTopology> components = FixedContainerTopologyResolver.survivingComponents(
                level, before, brokenPosition);
        boolean removed = components.isEmpty();
        Optional<LockRecord> after = Optional.empty();
        int detached = 0;
        boolean rootMoved = false;

        if (removed) {
            if (data.remove(before.id()).isEmpty()) return Optional.empty();
            clearMarkers(level, before.allPositions());
            Block.popResource(level, brokenPosition, new ItemStack(LocksmithItems.PADLOCK));
        } else {
            LockTopology kept;
            Optional<LockTopology> rootComponent = components.stream()
                    .filter(component -> component.containers().stream()
                            .anyMatch(node -> node.contains(before.rootContainer())))
                    .findFirst();
            LockLocation nextRoot = before.rootContainer();
            if (rootComponent.isPresent()) {
                kept = rootComponent.orElseThrow();
            } else {
                Map<BlockPos, Integer> distances = preBreakDistances(level, before);
                nextRoot = components.stream().flatMap(component -> component.containers().stream())
                        .flatMap(node -> node.positions().stream())
                        .min(Comparator.comparingInt((LockLocation location) ->
                                        distances.getOrDefault(location.blockPos(), Integer.MAX_VALUE))
                                .thenComparing(Comparator.naturalOrder()))
                        .orElseThrow();
                LockLocation selected = nextRoot;
                kept = components.stream().filter(component -> component.containers().stream()
                        .anyMatch(node -> node.contains(selected))).findFirst().orElseThrow();
                rootMoved = true;
            }
            int allContainers = components.stream().mapToInt(component -> component.containers().size()).sum();
            detached = allContainers - kept.containers().size();
            LockRecord replacement = before.withTopology(nextRoot, kept.containers(), kept.connectors());
            if (!data.replace(before, replacement)) return Optional.empty();
            Set<LockLocation> retained = new HashSet<>(replacement.allPositions());
            before.allPositions().stream().filter(location -> !retained.contains(location))
                    .forEach(location -> clearMarker(level, location));
            replacement.allPositions().forEach(location -> writeMarker(level, location, replacement.id()));
            after = Optional.of(replacement);
        }

        LockBreakResult result = new LockBreakResult(before, after, detached, rootMoved, removed);
        if (actor != null) {
            BreakDisposition disposition = LockAccessPolicy.evaluate(
                    before,
                    LocksmithAccessService.playerActor(actor, before),
                    AccessOperation.BREAK
            ).breakDisposition();
            if (disposition == BreakDisposition.NON_OWNER_ALERT) {
                publishBreakEvent(actor, brokenPosition, brokenState, result);
            }
        }
        return Optional.of(result);
    }

    /** Finalization path for environment destruction when explosion protection is disabled. */
    public static Optional<LockBreakResult> finalizeEnvironmentBrokenMember(
            ServerLevel level,
            BlockPos brokenPosition,
            BlockState brokenState,
            BlockEntity removedBlockEntity
    ) {
        return finalizeBrokenMember(level, null, brokenPosition, brokenState, removedBlockEntity);
    }

    public static boolean removeLock(ServerPlayer actor, LockRecord record) {
        if (!record.ownerId().equals(actor.getUUID())) return false;
        LocksmithSavedData data = LocksmithSavedData.forServer(actor.level().getServer());
        if (data.get(record.id()).map(current -> current.revision() != record.revision()).orElse(true)) {
            return false;
        }
        if (data.remove(record.id()).isEmpty()) return false;
        ServerLevel level = (ServerLevel) actor.level();
        clearMarkers(level, record.allPositions());
        ItemStack padlock = new ItemStack(LocksmithItems.PADLOCK);
        if (!actor.getInventory().add(padlock)) actor.drop(padlock, false);
        return true;
    }

    private static void publishBreakEvent(
            ServerPlayer actor,
            BlockPos position,
            BlockState state,
            LockBreakResult result
    ) {
        LockRecord before = result.before();
        int remaining = result.after().map(LockRecord::logicalContainerCount).orElse(0);
        TotemEventBus.publish(new LockedContainerNetworkBrokenEvent(
                UUID.randomUUID(), before.id(), actor.getUUID(), actor.getGameProfile().name(),
                before.ownerId(), before.ownerName(), FixedContainerTopologyResolver.memberKind(state),
                actor.level().dimension().identifier().toString(), position.getX(), position.getY(), position.getZ(),
                remaining, result.detachedUnlockedContainers(), result.rootMoved(), result.lockRemoved(),
                System.currentTimeMillis()
        ));
    }

    private static Map<BlockPos, Integer> preBreakDistances(ServerLevel level, LockRecord record) {
        Set<BlockPos> all = new HashSet<>();
        record.allPositions().forEach(location -> all.add(location.blockPos()));
        Map<BlockPos, Integer> distance = new HashMap<>();
        List<BlockPos> frontier = new ArrayList<>(List.of(record.rootContainer().blockPos()));
        distance.put(record.rootContainer().blockPos(), 0);
        while (!frontier.isEmpty()) {
            BlockPos current = frontier.removeFirst();
            int nextDistance = distance.get(current) + 1;
            for (BlockPos candidate : all) {
                if (distance.containsKey(candidate)) continue;
                boolean sameLogical = record.containers().stream().anyMatch(node -> {
                    List<BlockPos> positions = node.positions().stream().map(LockLocation::blockPos).toList();
                    return positions.contains(current) && positions.contains(candidate);
                });
                boolean linkedHopper = isStoredTransferNeighbor(level, current, candidate);
                if (sameLogical || linkedHopper) {
                    distance.put(candidate, nextDistance);
                    frontier.add(candidate);
                }
            }
        }
        return distance;
    }

    private static boolean isStoredTransferNeighbor(ServerLevel level, BlockPos first, BlockPos second) {
        int manhattan = Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY()) + Math.abs(first.getZ() - second.getZ());
        if (manhattan != 1) return false;
        return hopperPointsTo(level, first, second) || hopperPointsTo(level, second, first);
    }

    private static boolean hopperPointsTo(ServerLevel level, BlockPos hopper, BlockPos endpoint) {
        BlockState state = level.getBlockState(hopper);
        if (!state.is(Blocks.HOPPER)) return false;
        net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.HopperBlock.FACING);
        return hopper.above().equals(endpoint) || hopper.relative(facing).equals(endpoint);
    }

    private static PlacementEvaluation evaluatePlacement(
            ServerPlayer player,
            ServerLevel level,
            LockTopology topology,
            BlockPos virtualPosition,
            boolean allowVirtualMember
    ) {
        LocksmithSavedData data = LocksmithSavedData.forServer(level.getServer());
        Set<UUID> claimedIds = new HashSet<>();
        for (LockLocation location : topology.allPositions()) {
            Optional<LockRecord> indexed = data.findAt(location);
            boolean virtual = allowVirtualMember && location.blockPos().equals(virtualPosition);
            BlockEntity blockEntity = virtual ? null : level.getBlockEntity(location.blockPos());
            Optional<UUID> marker = blockEntity == null
                    ? Optional.empty() : LockMarkerAttachments.read(blockEntity);
            if (indexed.isPresent()) claimedIds.add(indexed.orElseThrow().id());
            if (marker.isPresent()) claimedIds.add(marker.orElseThrow());
            if (virtual) continue;
            if (indexed.isPresent() != marker.isPresent()
                    || (indexed.isPresent() && !indexed.orElseThrow().id().equals(marker.orElseThrow()))) {
                return PlacementEvaluation.denied("inconsistent_marker");
            }
            if (marker.isPresent() && data.get(marker.orElseThrow()).isEmpty()) {
                return PlacementEvaluation.denied("orphan_marker");
            }
        }
        if (claimedIds.isEmpty()) return PlacementEvaluation.allowed(Optional.empty());
        if (claimedIds.size() != 1) return PlacementEvaluation.denied("different_locks");
        LockRecord record = data.get(claimedIds.iterator().next()).orElse(null);
        if (record == null || record.state() != LockState.ACTIVE) {
            return PlacementEvaluation.denied("repair_required");
        }
        if (!record.ownerId().equals(player.getUUID())) {
            return PlacementEvaluation.denied("owner_only");
        }
        Set<LockLocation> resolvedPositions = new HashSet<>(topology.allPositions());
        if (!resolvedPositions.containsAll(record.allPositions())) {
            return PlacementEvaluation.denied("incomplete_existing_lock");
        }
        if (topology.positionCount() > LocksmithConfig.active().maxNetworkPositions()) {
            return PlacementEvaluation.denied("network_too_large");
        }
        Set<LockLocation> existingPositions = new HashSet<>(record.allPositions());
        for (LogicalContainerNode node : topology.containers()) {
            for (LockLocation location : node.positions()) {
                if (existingPositions.contains(location)) continue;
                if (allowVirtualMember && location.blockPos().equals(virtualPosition)) continue;
                BlockEntity blockEntity = level.getBlockEntity(location.blockPos());
                if (!(blockEntity instanceof BaseContainerBlockEntity container)) {
                    return PlacementEvaluation.denied("member_disappeared");
                }
                if (container.isLocked()) return PlacementEvaluation.denied("vanilla_lock");
                if (!LocksmithConfig.active().allowUnpackedLootTables()
                        && container instanceof RandomizableContainerBlockEntity randomizable
                        && randomizable.getLootTable() != null) {
                    return PlacementEvaluation.denied("loot_table");
                }
            }
        }
        return PlacementEvaluation.allowed(Optional.of(record));
    }

    private static boolean hasClaimAtOrNextTo(ServerLevel level, BlockPos position) {
        LocksmithSavedData data = LocksmithSavedData.forServer(level.getServer());
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(position);
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            candidates.add(position.relative(direction));
        }
        for (BlockPos candidate : candidates) {
            if (!level.hasChunkAt(candidate)) continue;
            LockLocation location = LockLocation.of(level, candidate);
            if (data.findAt(location).isPresent()) return true;
            BlockEntity blockEntity = level.getBlockEntity(candidate);
            if (blockEntity != null && LockMarkerAttachments.read(blockEntity).isPresent()) return true;
        }
        return false;
    }

    private static boolean isTopologyMember(BlockState state) {
        return FixedContainerTopologyResolver.isSupportedContainerState(state) || state.is(Blocks.HOPPER);
    }

    public record PlacementGuard(
            boolean relevant,
            boolean allowed,
            BlockPos position,
            BlockState previousState,
            InteractionHand hand,
            ItemStack itemBeforePlacement,
            Optional<UUID> expectedLockId,
            long expectedRevision,
            String reason
    ) {
        private static PlacementGuard unrelated() {
            return new PlacementGuard(false, true, BlockPos.ZERO, Blocks.AIR.defaultBlockState(),
                    InteractionHand.MAIN_HAND, ItemStack.EMPTY, Optional.empty(), -1L, "");
        }

        private static PlacementGuard allowed(
                BlockPos position,
                BlockState previousState,
                InteractionHand hand,
                ItemStack item,
                Optional<UUID> lockId,
                long revision
        ) {
            return new PlacementGuard(true, true, position.immutable(), previousState, hand,
                    item.copy(), lockId, revision, "");
        }

        private static PlacementGuard denied(
                BlockPos position,
                BlockState previousState,
                InteractionHand hand,
                ItemStack item,
                String reason
        ) {
            return new PlacementGuard(true, false, position.immutable(), previousState, hand,
                    item.copy(), Optional.empty(), -1L, reason);
        }
    }

    private record PlacementEvaluation(boolean allowed, String reason, Optional<LockRecord> record) {
        private static PlacementEvaluation allowed(Optional<LockRecord> record) {
            return new PlacementEvaluation(true, "", record);
        }

        private static PlacementEvaluation denied(String reason) {
            return new PlacementEvaluation(false, reason, Optional.empty());
        }
    }

    private static void clearMarkers(ServerLevel level, List<LockLocation> positions) {
        positions.forEach(location -> clearMarker(level, location));
    }

    private static void clearMarker(ServerLevel level, LockLocation location) {
        BlockEntity blockEntity = level.getBlockEntity(location.blockPos());
        if (blockEntity != null) LockMarkerAttachments.clear(blockEntity);
    }

    private static void writeMarker(ServerLevel level, LockLocation location, UUID id) {
        BlockEntity blockEntity = level.getBlockEntity(location.blockPos());
        if (blockEntity != null) LockMarkerAttachments.write(blockEntity, id);
    }

    private static void award(ServerPlayer player, String id) {
        net.minecraft.resources.Identifier identifier = net.minecraft.resources.Identifier.tryParse(id);
        if (identifier == null) return;
        var holder = player.level().getServer().getAdvancements().get(identifier);
        if (holder == null) return;
        var progress = player.getAdvancements().getOrStartProgress(holder);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
