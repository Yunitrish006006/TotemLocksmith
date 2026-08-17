package dev.totem.locksmith.topology;

import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LogicalContainerNode;

import java.util.List;

public record LockTopology(List<LogicalContainerNode> containers, List<LockLocation> connectors) {
    public LockTopology {
        containers = List.copyOf(containers);
        connectors = connectors.stream().distinct().sorted().toList();
    }

    public int positionCount() {
        return containers.stream().mapToInt(node -> node.positions().size()).sum() + connectors.size();
    }

    public List<LockLocation> allPositions() {
        return java.util.stream.Stream.concat(
                containers.stream().flatMap(node -> node.positions().stream()),
                connectors.stream()
        ).distinct().sorted().toList();
    }
}
