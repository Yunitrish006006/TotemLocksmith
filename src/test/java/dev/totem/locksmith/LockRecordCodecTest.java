package dev.totem.locksmith;

import com.mojang.serialization.JsonOps;
import dev.totem.locksmith.domain.ContainerKind;
import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.LogicalContainerNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LockRecordCodecTest {
    @Test
    void v1RoundTripPreservesAuthorityAndDerivedPositionCount() {
        LockLocation first = new LockLocation("minecraft:overworld", 1, 64, 2);
        LockLocation second = new LockLocation("minecraft:overworld", 2, 64, 2);
        LockLocation hopper = new LockLocation("minecraft:overworld", 3, 64, 2);
        LockRecord original = LockRecord.create(UUID.randomUUID(), UUID.randomUUID(), "Owner",
                first, List.of(new LogicalContainerNode(ContainerKind.CHEST, List.of(second, first))),
                List.of(hopper));
        var encoded = LockRecord.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        LockRecord decoded = LockRecord.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(original, decoded);
        assertEquals(3, decoded.positionCount());
        assertEquals(first, decoded.containers().getFirst().anchor());
    }

    @Test
    void rejectsDuplicatePositionsAndInvalidRoot() {
        LockLocation first = new LockLocation("minecraft:overworld", 1, 64, 2);
        assertThrows(IllegalArgumentException.class, () -> LockRecord.create(
                UUID.randomUUID(), UUID.randomUUID(), "Owner", first,
                List.of(new LogicalContainerNode(ContainerKind.CHEST, List.of(first))), List.of(first)));
        assertThrows(IllegalArgumentException.class, () -> LockRecord.create(
                UUID.randomUUID(), UUID.randomUUID(), "Owner",
                new LockLocation("minecraft:overworld", 9, 64, 9),
                List.of(new LogicalContainerNode(ContainerKind.CHEST, List.of(first))), List.of()));
    }
}
