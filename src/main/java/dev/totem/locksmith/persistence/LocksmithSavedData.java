package dev.totem.locksmith.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.LockState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Overworld-backed authority. The position index is derived and never serialized separately. */
public final class LocksmithSavedData extends SavedData {
    public static final int DATA_VERSION = 1;
    public static final int MAX_LOCKS_PER_OWNER = 128;
    public static final Codec<LocksmithSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(LocksmithSavedData::dataVersion),
            LockRecord.CODEC.listOf().optionalFieldOf("records", List.of()).forGetter(LocksmithSavedData::recordList)
    ).apply(instance, LocksmithSavedData::new));
    public static final SavedDataType<LocksmithSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("totem", "locksmith/locks"),
            LocksmithSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final int dataVersion;
    private final Map<UUID, LockRecord> records = new LinkedHashMap<>();
    private final Map<LockLocation, UUID> index = new HashMap<>();
    private final Map<UUID, Integer> ownerCounts = new HashMap<>();
    private final Map<UUID, String> diagnostics = new LinkedHashMap<>();

    public LocksmithSavedData() {
        this(DATA_VERSION, List.of());
    }

    private LocksmithSavedData(int dataVersion, List<LockRecord> persisted) {
        if (dataVersion > DATA_VERSION) {
            throw new IllegalArgumentException("unsupported newer Locksmith data version " + dataVersion);
        }
        this.dataVersion = DATA_VERSION;
        for (LockRecord record : persisted) {
            LockRecord duplicate = records.putIfAbsent(record.id(), record);
            if (duplicate != null) {
                diagnostics.put(record.id(), "duplicate_lock_uuid");
            }
        }
        rebuildIndex();
    }

    public static LocksmithSavedData forServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public synchronized Optional<LockRecord> get(UUID id) {
        return Optional.ofNullable(records.get(id));
    }

    public synchronized Optional<LockRecord> findAt(LockLocation location) {
        UUID id = index.get(location);
        return id == null ? Optional.empty() : Optional.ofNullable(records.get(id));
    }

    public synchronized boolean create(LockRecord record) {
        Objects.requireNonNull(record, "record");
        if (records.containsKey(record.id())
                || ownerCounts.getOrDefault(record.ownerId(), 0) >= MAX_LOCKS_PER_OWNER) {
            return false;
        }
        if (record.allPositions().stream().anyMatch(index::containsKey)) return false;
        records.put(record.id(), record);
        record.allPositions().forEach(position -> index.put(position, record.id()));
        ownerCounts.merge(record.ownerId(), 1, Integer::sum);
        setDirty();
        return true;
    }

    public synchronized boolean replace(LockRecord expected, LockRecord replacement) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(replacement, "replacement");
        LockRecord current = records.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || !replacement.id().equals(expected.id())) return false;
        if (!expected.ownerId().equals(replacement.ownerId())
                && ownerCounts.getOrDefault(replacement.ownerId(), 0) >= MAX_LOCKS_PER_OWNER) return false;
        for (LockLocation position : replacement.allPositions()) {
            UUID occupant = index.get(position);
            if (occupant != null && !occupant.equals(expected.id())) return false;
        }
        expected.allPositions().forEach(position -> index.remove(position, expected.id()));
        records.put(replacement.id(), replacement);
        replacement.allPositions().forEach(position -> index.put(position, replacement.id()));
        if (!expected.ownerId().equals(replacement.ownerId())) {
            decrementOwner(expected.ownerId());
            ownerCounts.merge(replacement.ownerId(), 1, Integer::sum);
        }
        setDirty();
        return true;
    }

    public synchronized Optional<LockRecord> remove(UUID id) {
        LockRecord removed = records.remove(id);
        if (removed == null) return Optional.empty();
        removed.allPositions().forEach(position -> index.remove(position, id));
        decrementOwner(removed.ownerId());
        diagnostics.remove(id);
        setDirty();
        return Optional.of(removed);
    }

    public synchronized int ownerLockCount(UUID ownerId) {
        return ownerCounts.getOrDefault(ownerId, 0);
    }

    public synchronized Map<UUID, LockRecord> snapshot() {
        return Map.copyOf(records);
    }

    public synchronized Map<UUID, String> diagnostics() {
        return Map.copyOf(diagnostics);
    }

    public synchronized int indexedPositionCount() {
        return index.size();
    }

    public synchronized void rebuildIndex() {
        index.clear();
        ownerCounts.clear();
        Map<LockLocation, Set<UUID>> claims = new LinkedHashMap<>();
        records.values().forEach(record -> {
            ownerCounts.merge(record.ownerId(), 1, Integer::sum);
            record.allPositions().forEach(position ->
                    claims.computeIfAbsent(position, ignored -> new LinkedHashSet<>()).add(record.id()));
        });
        Set<UUID> conflicts = new LinkedHashSet<>();
        claims.forEach((position, ids) -> {
            if (ids.size() == 1) {
                index.put(position, ids.iterator().next());
            } else {
                // Keep a deterministic sentinel claim in the derived index. All
                // claimants are marked CONFLICT below, so lookups remain
                // fail-closed even when a block-entity attachment is missing.
                index.put(position, ids.iterator().next());
                conflicts.addAll(ids);
            }
        });
        for (UUID id : conflicts) {
            LockRecord record = records.get(id);
            if (record != null && record.state() != LockState.CONFLICT) {
                records.put(id, record.withState(LockState.CONFLICT));
            }
            diagnostics.put(id, "duplicate_position");
        }
    }

    private int dataVersion() {
        return dataVersion;
    }

    private synchronized List<LockRecord> recordList() {
        return new ArrayList<>(records.values());
    }

    private void decrementOwner(UUID ownerId) {
        ownerCounts.computeIfPresent(ownerId, (ignored, count) -> count <= 1 ? null : count - 1);
    }
}
