package dev.totem.locksmith.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Immutable, server-authored snapshot used only to render the management screen. */
public record LocksmithManagementOpenData(
        UUID lockId,
        long revision,
        String ownerName,
        boolean ownerActor,
        boolean managerActor,
        boolean physicalKeysRequired,
        int accessModeOrdinal,
        int automationModeOrdinal,
        int logicalContainerCount,
        int connectorCount,
        List<MemberView> members,
        List<KeyView> keys,
        List<PlayerView> candidates
) {
    private static final int MAX_ROWS = 32;
    private static final int MAX_TEXT = 64;

    public LocksmithManagementOpenData {
        members = List.copyOf(members);
        keys = List.copyOf(keys);
        candidates = List.copyOf(candidates);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LocksmithManagementOpenData> STREAM_CODEC = StreamCodec.of(
            LocksmithManagementOpenData::encode,
            LocksmithManagementOpenData::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, LocksmithManagementOpenData data) {
        buf.writeUUID(data.lockId());
        buf.writeLong(data.revision());
        buf.writeUtf(data.ownerName(), MAX_TEXT);
        buf.writeBoolean(data.ownerActor());
        buf.writeBoolean(data.managerActor());
        buf.writeBoolean(data.physicalKeysRequired());
        buf.writeVarInt(data.accessModeOrdinal());
        buf.writeVarInt(data.automationModeOrdinal());
        buf.writeVarInt(data.logicalContainerCount());
        buf.writeVarInt(data.connectorCount());
        writeMembers(buf, data.members());
        writeKeys(buf, data.keys());
        writePlayers(buf, data.candidates());
    }

    private static LocksmithManagementOpenData decode(RegistryFriendlyByteBuf buf) {
        return new LocksmithManagementOpenData(
                buf.readUUID(),
                buf.readLong(),
                buf.readUtf(MAX_TEXT),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                readMembers(buf),
                readKeys(buf),
                readPlayers(buf)
        );
    }

    private static void writeMembers(RegistryFriendlyByteBuf buf, List<MemberView> values) {
        buf.writeVarInt(Math.min(MAX_ROWS, values.size()));
        for (int i = 0; i < values.size() && i < MAX_ROWS; i++) {
            MemberView value = values.get(i);
            buf.writeUUID(value.playerId());
            buf.writeUtf(value.name(), MAX_TEXT);
            buf.writeVarInt(value.roleOrdinal());
        }
    }

    private static List<MemberView> readMembers(RegistryFriendlyByteBuf buf) {
        int count = boundedCount(buf.readVarInt());
        List<MemberView> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new MemberView(buf.readUUID(), buf.readUtf(MAX_TEXT), buf.readVarInt()));
        }
        return List.copyOf(result);
    }

    private static void writeKeys(RegistryFriendlyByteBuf buf, List<KeyView> values) {
        buf.writeVarInt(Math.min(MAX_ROWS, values.size()));
        for (int i = 0; i < values.size() && i < MAX_ROWS; i++) {
            KeyView value = values.get(i);
            buf.writeUUID(value.keyId());
            buf.writeUtf(value.label(), MAX_TEXT);
        }
    }

    private static List<KeyView> readKeys(RegistryFriendlyByteBuf buf) {
        int count = boundedCount(buf.readVarInt());
        List<KeyView> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new KeyView(buf.readUUID(), buf.readUtf(MAX_TEXT)));
        }
        return List.copyOf(result);
    }

    private static void writePlayers(RegistryFriendlyByteBuf buf, List<PlayerView> values) {
        buf.writeVarInt(Math.min(MAX_ROWS, values.size()));
        for (int i = 0; i < values.size() && i < MAX_ROWS; i++) {
            PlayerView value = values.get(i);
            buf.writeUUID(value.playerId());
            buf.writeUtf(value.name(), MAX_TEXT);
        }
    }

    private static List<PlayerView> readPlayers(RegistryFriendlyByteBuf buf) {
        int count = boundedCount(buf.readVarInt());
        List<PlayerView> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new PlayerView(buf.readUUID(), buf.readUtf(MAX_TEXT)));
        }
        return List.copyOf(result);
    }

    private static int boundedCount(int value) {
        if (value < 0 || value > MAX_ROWS) {
            throw new IllegalArgumentException("Invalid Locksmith menu row count: " + value);
        }
        return value;
    }

    public record MemberView(UUID playerId, String name, int roleOrdinal) { }
    public record KeyView(UUID keyId, String label) { }
    public record PlayerView(UUID playerId, String name) { }
}
