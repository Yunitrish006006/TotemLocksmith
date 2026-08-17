package dev.totem.locksmith.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable authority for one physical lock across one fixed-container network. */
public record LockRecord(
        UUID id,
        UUID ownerId,
        String ownerName,
        LockLocation rootContainer,
        List<LogicalContainerNode> containers,
        List<LockLocation> connectors,
        AccessMode accessMode,
        AutomationMode automationMode,
        List<MemberEntry> members,
        List<KeyGrant> keys,
        int keyEpoch,
        long revision,
        LockState state,
        int topologySchema
) {
    public static final int CURRENT_TOPOLOGY_SCHEMA = 1;
    public static final int MAX_NETWORK_POSITIONS = 128;
    public static final int MAX_MEMBERS = 32;
    public static final int MAX_KEYS = 32;
    private static final Codec<AccessMode> ACCESS_CODEC = enumCodec(AccessMode.class);
    private static final Codec<AutomationMode> AUTOMATION_CODEC = enumCodec(AutomationMode.class);
    private static final Codec<LockState> STATE_CODEC = enumCodec(LockState.class);
    public static final Codec<LockRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(LockRecord::id),
            UUIDUtil.CODEC.fieldOf("owner_id").forGetter(LockRecord::ownerId),
            Codec.STRING.optionalFieldOf("owner_name", "Unknown").forGetter(LockRecord::ownerName),
            LockLocation.CODEC.fieldOf("root_container").forGetter(LockRecord::rootContainer),
            LogicalContainerNode.CODEC.listOf().fieldOf("containers").forGetter(LockRecord::containers),
            LockLocation.CODEC.listOf().optionalFieldOf("connectors", List.of()).forGetter(LockRecord::connectors),
            ACCESS_CODEC.optionalFieldOf("access_mode", AccessMode.PRIVATE).forGetter(LockRecord::accessMode),
            AUTOMATION_CODEC.optionalFieldOf("automation_mode", AutomationMode.DENY).forGetter(LockRecord::automationMode),
            MemberEntry.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(LockRecord::members),
            KeyGrant.CODEC.listOf().optionalFieldOf("keys", List.of()).forGetter(LockRecord::keys),
            Codec.INT.optionalFieldOf("key_epoch", 0).forGetter(LockRecord::keyEpoch),
            Codec.LONG.optionalFieldOf("revision", 1L).forGetter(LockRecord::revision),
            STATE_CODEC.optionalFieldOf("state", LockState.ACTIVE).forGetter(LockRecord::state),
            Codec.INT.optionalFieldOf("topology_schema", CURRENT_TOPOLOGY_SCHEMA).forGetter(LockRecord::topologySchema)
    ).apply(instance, LockRecord::new));

    public LockRecord {
        id = Objects.requireNonNull(id, "id");
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        ownerName = SanitizedText.displayName(ownerName);
        rootContainer = Objects.requireNonNull(rootContainer, "rootContainer");
        accessMode = Objects.requireNonNull(accessMode, "accessMode");
        automationMode = Objects.requireNonNull(automationMode, "automationMode");
        state = Objects.requireNonNull(state, "state");
        containers = List.copyOf(Objects.requireNonNull(containers, "containers"));
        connectors = Objects.requireNonNull(connectors, "connectors").stream().distinct().sorted().toList();
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
        if (containers.isEmpty()) throw new IllegalArgumentException("lock requires a container");
        int validatedPositionCount = containers.stream().mapToInt(node -> node.positions().size()).sum()
                + connectors.size();
        if (validatedPositionCount > MAX_NETWORK_POSITIONS) throw new IllegalArgumentException("network position limit exceeded");
        if (members.size() > MAX_MEMBERS) throw new IllegalArgumentException("member limit exceeded");
        if (keys.size() > MAX_KEYS) throw new IllegalArgumentException("key limit exceeded");
        if (keyEpoch < 0 || revision < 1L) throw new IllegalArgumentException("invalid epoch or revision");
        if (topologySchema != CURRENT_TOPOLOGY_SCHEMA) throw new IllegalArgumentException("unknown topology schema");
        LockLocation validatedRoot = rootContainer;
        if (containers.stream().noneMatch(node -> node.contains(validatedRoot))) {
            throw new IllegalArgumentException("root must be a container member");
        }
        validateUniqueness(containers, connectors, members, keys);
    }

    public static LockRecord create(
            UUID id,
            UUID ownerId,
            String ownerName,
            LockLocation root,
            List<LogicalContainerNode> containers,
            List<LockLocation> connectors
    ) {
        return new LockRecord(id, ownerId, ownerName, root, containers, connectors,
                AccessMode.PRIVATE, AutomationMode.DENY, List.of(), List.of(), 0, 1L,
                LockState.ACTIVE, CURRENT_TOPOLOGY_SCHEMA);
    }

    public int positionCount() {
        return containers.stream().mapToInt(node -> node.positions().size()).sum() + connectors.size();
    }

    public int logicalContainerCount() {
        return containers.size();
    }

    public List<LockLocation> allPositions() {
        return java.util.stream.Stream.concat(
                containers.stream().flatMap(node -> node.positions().stream()),
                connectors.stream()
        ).distinct().sorted().toList();
    }

    public Optional<MemberEntry> member(UUID playerId) {
        return members.stream().filter(entry -> entry.playerId().equals(playerId)).findFirst();
    }

    public boolean isActiveKey(UUID keyId, int epoch) {
        return epoch == keyEpoch && keys.stream().anyMatch(key -> key.keyId().equals(keyId) && key.epoch() == keyEpoch);
    }

    public LockRecord withTopology(
            LockLocation newRoot,
            List<LogicalContainerNode> newContainers,
            List<LockLocation> newConnectors
    ) {
        return new LockRecord(id, ownerId, ownerName, newRoot, newContainers, newConnectors,
                accessMode, automationMode, members, keys, keyEpoch, revision + 1L, state,
                topologySchema);
    }

    public LockRecord withModes(AccessMode newAccessMode, AutomationMode newAutomationMode) {
        return new LockRecord(id, ownerId, ownerName, rootContainer, containers, connectors,
                newAccessMode, newAutomationMode, members, keys, keyEpoch, revision + 1L,
                state, topologySchema);
    }

    public LockRecord withMembers(List<MemberEntry> newMembers) {
        return new LockRecord(id, ownerId, ownerName, rootContainer, containers, connectors,
                accessMode, automationMode, newMembers, keys, keyEpoch, revision + 1L,
                state, topologySchema);
    }

    public LockRecord withKeys(List<KeyGrant> newKeys) {
        return new LockRecord(id, ownerId, ownerName, rootContainer, containers, connectors,
                accessMode, automationMode, members, newKeys, keyEpoch, revision + 1L,
                state, topologySchema);
    }

    public LockRecord rotateKeys() {
        return new LockRecord(id, ownerId, ownerName, rootContainer, containers, connectors,
                accessMode, automationMode, members, List.of(), keyEpoch + 1, revision + 1L,
                state, topologySchema);
    }

    public LockRecord transfer(UUID newOwner, String newOwnerName) {
        return new LockRecord(id, newOwner, newOwnerName, rootContainer, containers, connectors,
                AccessMode.PRIVATE, AutomationMode.DENY, List.of(), List.of(), keyEpoch + 1,
                revision + 1L, state, topologySchema);
    }

    public LockRecord withState(LockState newState) {
        return new LockRecord(id, ownerId, ownerName, rootContainer, containers, connectors,
                accessMode, automationMode, members, keys, keyEpoch, revision + 1L,
                newState, topologySchema);
    }

    private static void validateUniqueness(
            List<LogicalContainerNode> containers,
            List<LockLocation> connectors,
            List<MemberEntry> members,
            List<KeyGrant> keys
    ) {
        Set<LockLocation> positions = new HashSet<>();
        containers.forEach(node -> node.positions().forEach(position -> {
            if (!positions.add(position)) throw new IllegalArgumentException("duplicate network position");
        }));
        connectors.forEach(position -> {
            if (!positions.add(position)) throw new IllegalArgumentException("duplicate network position");
        });
        if (members.stream().map(MemberEntry::playerId).distinct().count() != members.size()) {
            throw new IllegalArgumentException("duplicate member");
        }
        if (keys.stream().map(KeyGrant::keyId).distinct().count() != keys.size()) {
            throw new IllegalArgumentException("duplicate key");
        }
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.xmap(value -> Enum.valueOf(type, value.toUpperCase()),
                value -> value.name().toLowerCase());
    }
}
