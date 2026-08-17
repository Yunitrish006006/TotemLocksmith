package dev.totem.locksmith.gametest;

import dev.totem.locksmith.domain.AccessMode;
import dev.totem.locksmith.domain.AutomationMode;
import dev.totem.locksmith.domain.KeyGrant;
import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.persistence.LockMarkerAttachments;
import dev.totem.locksmith.persistence.LocksmithSavedData;
import dev.totem.locksmith.topology.FixedContainerTopologyResolver;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Two-JVM proof that records, topology, ACL/key fields, indices, and markers survive restart. */
public final class LocksmithRestartProbe implements ModInitializer {
    private static final String PHASE_ENV = "TOTEM_LOCKSMITH_RESTART_PROBE_PHASE";
    private static final String MARKER_DIRECTORY_ENV = "TOTEM_LOCKSMITH_RESTART_PROBE_MARKER_DIR";
    private static final BlockPos ROOT = new BlockPos(8, 200, 8);
    private static final BlockPos HOPPER = ROOT.below();
    private static final BlockPos DESTINATION = HOPPER.east();
    private static final UUID LOCK_ID = UUID.fromString("83206462-1f5d-44d8-bbe9-aa6801f29618");
    private static final UUID OWNER_ID = UUID.fromString("0d9eafaf-bd17-4670-bbaa-36fac8ce2320");
    private static final UUID KEY_ID = UUID.fromString("42429bc3-bc38-4c98-ac7a-e44e62dc8206");
    private static final int CHUNK_X = SectionPos.blockToSectionCoord(ROOT.getX());
    private static final int CHUNK_Z = SectionPos.blockToSectionCoord(ROOT.getZ());

    @Override
    public void onInitialize() {
        String phase = System.getenv(PHASE_ENV);
        if (phase == null || phase.isBlank()) return;
        Path markers = markerDirectory();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel level = server.overworld();
            level.setChunkForced(CHUNK_X, CHUNK_Z, true);
            level.getChunk(ROOT);
            ServerTickEvents.END_SERVER_TICK.register(new Session(phase, markers)::tick);
        });
    }

    private static void runPhase(MinecraftServer server, String phase) {
        ServerLevel level = server.overworld();
        LocksmithSavedData data = LocksmithSavedData.forServer(server);
        if ("seed".equals(phase)) {
            require(data.get(LOCK_ID).isEmpty(), "Seed found a stale lock record");
            level.setBlockAndUpdate(ROOT, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(HOPPER, Blocks.HOPPER.defaultBlockState()
                    .setValue(HopperBlock.FACING, Direction.EAST));
            level.setBlockAndUpdate(DESTINATION, Blocks.BARREL.defaultBlockState());
            var topology = FixedContainerTopologyResolver.scan(level, ROOT, 128)
                    .topology().orElseThrow();
            LockRecord record = LockRecord.create(LOCK_ID, OWNER_ID, "Restart Owner",
                            LockLocation.of(level, ROOT), topology.containers(), topology.connectors())
                    .withModes(AccessMode.PUBLIC, AutomationMode.ALL)
                    .withKeys(java.util.List.of(new KeyGrant(KEY_ID, "Restart Key", 0)));
            require(data.create(record), "Could not seed lock record");
            record.allPositions().forEach(location -> {
                BlockEntity blockEntity = level.getBlockEntity(location.blockPos());
                require(blockEntity != null, "Seed member BlockEntity missing");
                LockMarkerAttachments.write(blockEntity, LOCK_ID);
            });
            return;
        }
        if ("verify".equals(phase)) {
            LockRecord record = data.get(LOCK_ID).orElseThrow(
                    () -> new IllegalStateException("Restart did not reload lock record"));
            require(record.ownerId().equals(OWNER_ID), "Owner UUID changed across restart");
            require(record.accessMode() == AccessMode.PUBLIC
                            && record.automationMode() == AutomationMode.ALL,
                    "Modes changed across restart");
            require(record.logicalContainerCount() == 2 && record.connectors().size() == 1,
                    "Topology changed across restart");
            require(record.keys().size() == 1 && record.keys().getFirst().keyId().equals(KEY_ID),
                    "Key grant changed across restart");
            record.allPositions().forEach(location -> {
                require(data.findAt(location).map(value -> value.id().equals(LOCK_ID)).orElse(false),
                        "Derived position index did not rebuild");
                BlockEntity blockEntity = level.getBlockEntity(location.blockPos());
                require(blockEntity != null && LockMarkerAttachments.read(blockEntity)
                                .filter(LOCK_ID::equals).isPresent(),
                        "Persistent member marker did not reload");
                LockMarkerAttachments.clear(blockEntity);
            });
            data.remove(LOCK_ID);
            level.removeBlock(ROOT, false);
            level.removeBlock(HOPPER, false);
            level.removeBlock(DESTINATION, false);
            level.setChunkForced(CHUNK_X, CHUNK_Z, false);
            return;
        }
        throw new IllegalArgumentException("Unknown Locksmith restart probe phase: " + phase);
    }

    private static Path markerDirectory() {
        String configured = System.getenv(MARKER_DIRECTORY_ENV);
        return configured == null || configured.isBlank()
                ? Path.of("locksmith-restart-probe").toAbsolutePath().normalize()
                : Path.of(configured).toAbsolutePath().normalize();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void writeMarker(Path directory, String name, String content) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write restart marker " + name, exception);
        }
    }

    private static final class Session {
        private final String phase;
        private final Path markerDirectory;
        private int ticksRemaining = 100;
        private boolean executed;

        private Session(String phase, Path markerDirectory) {
            this.phase = phase;
            this.markerDirectory = markerDirectory;
        }

        private void tick(MinecraftServer server) {
            if (--ticksRemaining > 0) return;
            try {
                if (!executed) {
                    runPhase(server, phase);
                    executed = true;
                    ticksRemaining = 40;
                    return;
                }
                writeMarker(markerDirectory, phase + ".ok", "success\n");
                server.halt(false);
            } catch (Throwable throwable) {
                writeMarker(markerDirectory, phase + ".failure", throwable + "\n");
                server.halt(false);
                throw new IllegalStateException("Locksmith restart probe failed in " + phase, throwable);
            }
        }
    }
}
