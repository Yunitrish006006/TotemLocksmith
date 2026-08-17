package dev.totem.locksmith.topology;

import dev.totem.locksmith.domain.ContainerKind;
import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.LogicalContainerNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/** Bounded, non-force-loading resolver for fixed Hopper transfer routes. */
public final class FixedContainerTopologyResolver {
    private FixedContainerTopologyResolver() {
    }

    public static TopologyScanResult scan(ServerLevel level, BlockPos start, int maximumPositions) {
        return scan(level, start, maximumPositions, null, null);
    }

    /**
     * Resolves the component as it would look immediately after one block is
     * placed. This lets placement be rejected before the item or world is
     * mutated, including bridges between two existing lock UUIDs.
     */
    public static TopologyScanResult scanIncludingPlacement(
            ServerLevel level,
            BlockPos start,
            BlockState placementState,
            int maximumPositions
    ) {
        return scan(level, start, maximumPositions, start.immutable(), placementState);
    }

    private static TopologyScanResult scan(
            ServerLevel level,
            BlockPos start,
            int maximumPositions,
            BlockPos virtualPosition,
            BlockState virtualState
    ) {
        if (!level.hasChunkAt(start)
                || !isSupportedContainerOrHopper(level, start, virtualPosition, virtualState)) {
            return TopologyScanResult.failure("unsupported_container");
        }
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new LinkedHashSet<>();
        Set<BlockPos> assignedContainerPositions = new HashSet<>();
        Map<LockLocation, LogicalContainerNode> nodesByAnchor = new LinkedHashMap<>();
        Set<LockLocation> connectors = new LinkedHashSet<>();
        queue.add(start.immutable());

        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            if (!visited.add(current)) continue;
            if (visited.size() > maximumPositions) {
                return TopologyScanResult.failure("network_too_large");
            }
            if (!level.hasChunkAt(current)) {
                return TopologyScanResult.failure("network_chunk_unloaded");
            }
            BlockState state = stateAt(level, current, virtualPosition, virtualState);
            if (isSupportedContainerState(state)) {
                if (!assignedContainerPositions.contains(current)) {
                    Optional<LogicalContainerNode> node = logicalNode(
                            level, current, virtualPosition, virtualState);
                    if (node.isEmpty()) return TopologyScanResult.failure("incomplete_double_chest");
                    LogicalContainerNode resolved = node.get();
                    nodesByAnchor.putIfAbsent(resolved.anchor(), resolved);
                    for (LockLocation position : resolved.positions()) {
                        BlockPos member = position.blockPos();
                        assignedContainerPositions.add(member);
                        if (!visited.contains(member)) queue.add(member);
                        String failure = addTransferNeighbors(
                                level, member, queue, virtualPosition, virtualState);
                        if (failure != null) return TopologyScanResult.failure(failure);
                    }
                } else {
                    String failure = addTransferNeighbors(
                            level, current, queue, virtualPosition, virtualState);
                    if (failure != null) return TopologyScanResult.failure(failure);
                }
            } else if (state.is(Blocks.HOPPER) && hasRequiredBlockEntity(
                    level, current, virtualPosition, HopperBlockEntity.class)) {
                connectors.add(LockLocation.of(level, current));
                String failure = addTransferNeighbors(
                        level, current, queue, virtualPosition, virtualState);
                if (failure != null) return TopologyScanResult.failure(failure);
            }
        }

        if (nodesByAnchor.isEmpty()) return TopologyScanResult.failure("no_container");
        LockTopology topology = new LockTopology(List.copyOf(nodesByAnchor.values()), List.copyOf(connectors));
        return topology.positionCount() > maximumPositions
                ? TopologyScanResult.failure("network_too_large")
                : TopologyScanResult.success(topology);
    }

    public static List<LockTopology> survivingComponents(
            ServerLevel level,
            LockRecord record,
            BlockPos removed
    ) {
        Map<BlockPos, LogicalContainerNode> containerByPosition = new LinkedHashMap<>();
        Map<LockLocation, LogicalContainerNode> uniqueNodes = new LinkedHashMap<>();
        for (LogicalContainerNode original : record.containers()) {
            List<LockLocation> surviving = original.positions().stream()
                    .filter(location -> !location.blockPos().equals(removed))
                    .filter(location -> isSupportedContainer(level, location.blockPos()))
                    .toList();
            if (surviving.isEmpty()) continue;
            LogicalContainerNode node = new LogicalContainerNode(original.kind(), surviving);
            uniqueNodes.put(node.anchor(), node);
            node.positions().forEach(location -> containerByPosition.put(location.blockPos(), node));
        }
        Set<BlockPos> connectorPositions = new LinkedHashSet<>();
        for (LockLocation connector : record.connectors()) {
            BlockPos position = connector.blockPos();
            if (!position.equals(removed) && level.hasChunkAt(position)
                    && level.getBlockState(position).is(Blocks.HOPPER)
                    && level.getBlockEntity(position) instanceof HopperBlockEntity) {
                connectorPositions.add(position);
            }
        }

        Set<BlockPos> allPositions = new LinkedHashSet<>(containerByPosition.keySet());
        allPositions.addAll(connectorPositions);
        Set<BlockPos> visited = new HashSet<>();
        List<LockTopology> result = new ArrayList<>();
        for (BlockPos seed : allPositions.stream().sorted(FixedContainerTopologyResolver::comparePos).toList()) {
            if (!visited.add(seed)) continue;
            Queue<BlockPos> queue = new ArrayDeque<>();
            Set<LogicalContainerNode> componentNodes = new LinkedHashSet<>();
            Set<LockLocation> componentConnectors = new LinkedHashSet<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                BlockPos current = queue.remove();
                LogicalContainerNode node = containerByPosition.get(current);
                if (node != null) {
                    componentNodes.add(node);
                    for (LockLocation member : node.positions()) {
                        if (allPositions.contains(member.blockPos()) && visited.add(member.blockPos())) {
                            queue.add(member.blockPos());
                        }
                    }
                } else if (connectorPositions.contains(current)) {
                    componentConnectors.add(LockLocation.of(level, current));
                }
                for (BlockPos candidate : directNeighbors(current)) {
                    if (allPositions.contains(candidate) && hasTransferEdge(level, current, candidate)
                            && visited.add(candidate)) {
                        queue.add(candidate);
                    }
                }
            }
            if (!componentNodes.isEmpty()) {
                result.add(new LockTopology(
                        componentNodes.stream().sorted(Comparator.comparing(LogicalContainerNode::anchor)).toList(),
                        List.copyOf(componentConnectors)
                ));
            }
        }
        return List.copyOf(result);
    }

    public static Optional<LogicalContainerNode> logicalNode(ServerLevel level, BlockPos position) {
        return logicalNode(level, position, null, null);
    }

    private static Optional<LogicalContainerNode> logicalNode(
            ServerLevel level,
            BlockPos position,
            BlockPos virtualPosition,
            BlockState virtualState
    ) {
        if (!level.hasChunkAt(position)) return Optional.empty();
        BlockState state = stateAt(level, position, virtualPosition, virtualState);
        ContainerKind kind = kind(state);
        if (kind == null || !hasRequiredBlockEntity(
                level, position, virtualPosition, BaseContainerBlockEntity.class)) {
            return Optional.empty();
        }
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return Optional.of(new LogicalContainerNode(kind, List.of(LockLocation.of(level, position))));
        }
        BlockPos partner = ChestBlock.getConnectedBlockPos(position, state);
        if (!level.hasChunkAt(partner)
                || !stateAt(level, partner, virtualPosition, virtualState).is(state.getBlock())
                || !hasRequiredBlockEntity(
                        level, partner, virtualPosition, BaseContainerBlockEntity.class)) {
            return Optional.empty();
        }
        return Optional.of(new LogicalContainerNode(kind, List.of(
                LockLocation.of(level, position), LockLocation.of(level, partner))));
    }

    public static boolean isSupportedContainer(ServerLevel level, BlockPos position) {
        return level.hasChunkAt(position)
                && isSupportedContainerState(level.getBlockState(position))
                && level.getBlockEntity(position) instanceof BaseContainerBlockEntity;
    }

    public static boolean isSupportedContainerState(BlockState state) {
        return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL);
    }

    public static String memberKind(BlockState state) {
        ContainerKind kind = kind(state);
        return kind == null ? (state.is(Blocks.HOPPER) ? "hopper" : "adapter") : kind.id();
    }

    public static Map<BlockPos, Integer> preBreakDistances(LockRecord record) {
        Map<BlockPos, Integer> distance = new HashMap<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        BlockPos root = record.rootContainer().blockPos();
        Set<BlockPos> positions = new LinkedHashSet<>();
        record.allPositions().forEach(location -> positions.add(location.blockPos()));
        distance.put(root, 0);
        queue.add(root);
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            int nextDistance = distance.get(current) + 1;
            for (BlockPos candidate : positions) {
                if (distance.containsKey(candidate)) continue;
                boolean sameLogical = record.containers().stream()
                        .anyMatch(node -> node.positions().stream().map(LockLocation::blockPos).toList().containsAll(List.of(current, candidate)));
                if (sameLogical || manhattanAdjacent(current, candidate)) {
                    distance.put(candidate, nextDistance);
                    queue.add(candidate);
                }
            }
        }
        return Map.copyOf(distance);
    }

    private static String addTransferNeighbors(
            ServerLevel level,
            BlockPos position,
            Queue<BlockPos> queue,
            BlockPos virtualPosition,
            BlockState virtualState
    ) {
        BlockState currentState = stateAt(level, position, virtualPosition, virtualState);
        if (currentState.is(Blocks.HOPPER)) {
            BlockPos source = position.above();
            BlockPos destination = position.relative(currentState.getValue(HopperBlock.FACING));
            if (!level.hasChunkAt(source) || !level.hasChunkAt(destination)) {
                return "network_chunk_unloaded";
            }
        }
        for (BlockPos candidate : directNeighbors(position)) {
            if (!level.hasChunkAt(candidate)) continue;
            BlockState candidateState = stateAt(level, candidate, virtualPosition, virtualState);
            if ((isSupportedContainerState(candidateState) || candidateState.is(Blocks.HOPPER))
                    && hasTransferEdge(level, position, candidate, virtualPosition, virtualState)) {
                queue.add(candidate.immutable());
            }
        }
        return null;
    }

    private static boolean hasTransferEdge(ServerLevel level, BlockPos first, BlockPos second) {
        return hasTransferEdge(level, first, second, null, null);
    }

    private static boolean hasTransferEdge(
            ServerLevel level,
            BlockPos first,
            BlockPos second,
            BlockPos virtualPosition,
            BlockState virtualState
    ) {
        return directedHopperEdge(level, first, second, virtualPosition, virtualState)
                || directedHopperEdge(level, second, first, virtualPosition, virtualState);
    }

    private static boolean directedHopperEdge(
            ServerLevel level,
            BlockPos hopperPos,
            BlockPos endpoint,
            BlockPos virtualPosition,
            BlockState virtualState
    ) {
        BlockState state = stateAt(level, hopperPos, virtualPosition, virtualState);
        if (!state.is(Blocks.HOPPER) || !hasRequiredBlockEntity(
                level, hopperPos, virtualPosition, HopperBlockEntity.class)) {
            return false;
        }
        Direction facing = state.getValue(HopperBlock.FACING);
        return hopperPos.above().equals(endpoint) || hopperPos.relative(facing).equals(endpoint);
    }

    private static List<BlockPos> directNeighbors(BlockPos position) {
        List<BlockPos> neighbors = new ArrayList<>(6);
        for (Direction direction : Direction.values()) neighbors.add(position.relative(direction));
        return neighbors;
    }

    private static ContainerKind kind(BlockState state) {
        if (state.is(Blocks.CHEST)) return ContainerKind.CHEST;
        if (state.is(Blocks.TRAPPED_CHEST)) return ContainerKind.TRAPPED_CHEST;
        if (state.is(Blocks.BARREL)) return ContainerKind.BARREL;
        return null;
    }

    private static boolean isSupportedContainerOrHopper(
            ServerLevel level,
            BlockPos position,
            BlockPos virtualPosition,
            BlockState virtualState
    ) {
        BlockState state = stateAt(level, position, virtualPosition, virtualState);
        if (isSupportedContainerState(state)) {
            return hasRequiredBlockEntity(
                    level, position, virtualPosition, BaseContainerBlockEntity.class);
        }
        return state.is(Blocks.HOPPER) && hasRequiredBlockEntity(
                level, position, virtualPosition, HopperBlockEntity.class);
    }

    private static BlockState stateAt(
            ServerLevel level,
            BlockPos position,
            BlockPos virtualPosition,
            BlockState virtualState
    ) {
        return virtualPosition != null && virtualPosition.equals(position)
                ? virtualState : level.getBlockState(position);
    }

    private static boolean hasRequiredBlockEntity(
            ServerLevel level,
            BlockPos position,
            BlockPos virtualPosition,
            Class<? extends net.minecraft.world.level.block.entity.BlockEntity> type
    ) {
        return virtualPosition != null && virtualPosition.equals(position)
                || type.isInstance(level.getBlockEntity(position));
    }

    private static boolean manhattanAdjacent(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ()) == 1;
    }

    private static int comparePos(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) return x;
        int y = Integer.compare(first.getY(), second.getY());
        return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
    }
}
