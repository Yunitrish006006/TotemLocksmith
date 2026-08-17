package dev.totem.locksmith.topology;

import java.util.Optional;

public record TopologyScanResult(Optional<LockTopology> topology, String error) {
    public static TopologyScanResult success(LockTopology topology) {
        return new TopologyScanResult(Optional.of(topology), "");
    }

    public static TopologyScanResult failure(String error) {
        return new TopologyScanResult(Optional.empty(), error);
    }

    public boolean successful() {
        return topology.isPresent();
    }
}
