package dev.totem.locksmith;

import dev.totem.locksmith.domain.ContainerKind;
import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.LogicalContainerNode;
import dev.totem.locksmith.persistence.LocksmithSavedData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocksmithSavedDataTest {
    @Test
    void createReplaceRemoveMaintainsConstantTimeDerivedIndex() {
        LocksmithSavedData data = new LocksmithSavedData();
        UUID id = UUID.randomUUID();
        LockLocation root = new LockLocation("minecraft:overworld", 1, 64, 2);
        LockLocation second = new LockLocation("minecraft:overworld", 3, 64, 2);
        LockRecord first = LockRecord.create(id, UUID.randomUUID(), "Owner", root,
                List.of(new LogicalContainerNode(ContainerKind.CHEST, List.of(root))), List.of());
        assertTrue(data.create(first));
        assertEquals(id, data.findAt(root).orElseThrow().id());
        LockRecord replacement = first.withTopology(root,
                List.of(new LogicalContainerNode(ContainerKind.CHEST, List.of(root)),
                        new LogicalContainerNode(ContainerKind.BARREL, List.of(second))), List.of());
        assertTrue(data.replace(first, replacement));
        assertEquals(id, data.findAt(second).orElseThrow().id());
        assertFalse(data.replace(first, first));
        assertTrue(data.remove(id).isPresent());
        assertTrue(data.findAt(root).isEmpty());
        assertEquals(0, data.indexedPositionCount());
    }

    @Test
    void tenThousandRecordsKeepPositionAndOwnerLookupsBounded() {
        LocksmithSavedData data = new LocksmithSavedData();
        LockLocation[] locations = new LockLocation[10_000];
        long started = System.nanoTime();
        for (int index = 0; index < locations.length; index++) {
            LockLocation location = new LockLocation("minecraft:overworld", index, 64, index * 2);
            locations[index] = location;
            UUID owner = new UUID(0L, index + 1L);
            LockRecord record = LockRecord.create(new UUID(1L, index + 1L), owner, "Owner", location,
                    List.of(new LogicalContainerNode(ContainerKind.BARREL, List.of(location))), List.of());
            assertTrue(data.create(record));
            assertEquals(1, data.ownerLockCount(owner));
        }
        for (LockLocation location : locations) assertTrue(data.findAt(location).isPresent());
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        assertEquals(10_000, data.indexedPositionCount());
        assertTrue(elapsedMillis < 5_000L,
                () -> "10,000-record O(1) baseline exceeded 5 seconds: " + elapsedMillis + " ms");
    }
}
