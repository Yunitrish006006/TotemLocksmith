package dev.totem.locksmith.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.totem.locksmith.TotemLocksmith;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Immutable safe defaults; file-backed reload can replace this snapshot without mutating active reads. */
public record LocksmithConfig(
        boolean explosionProtection,
        boolean allowUnpackedLootTables,
        int maxLocksPerOwner,
        int maxMembersPerLock,
        int maxKeysPerLock,
        int maxNetworkPositions
) {
    private static volatile LocksmithConfig active = defaults();

    public LocksmithConfig {
        if (maxLocksPerOwner < 1 || maxMembersPerLock < 1 || maxKeysPerLock < 1
                || maxNetworkPositions < 1 || maxNetworkPositions > 1_024) {
            throw new IllegalArgumentException("invalid Locksmith config limits");
        }
    }

    public static LocksmithConfig defaults() {
        return new LocksmithConfig(true, false, 128, 32, 32, 128);
    }

    public static LocksmithConfig active() {
        return active;
    }

    public static void install(LocksmithConfig snapshot) {
        active = java.util.Objects.requireNonNull(snapshot, "snapshot");
    }

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("totem-locksmith.json");
    }

    /** Atomically installs a valid snapshot; parse failures retain the last known-good settings. */
    public static boolean reload() {
        Path path = defaultPath();
        try {
            if (Files.notExists(path)) writeDefaults(path);
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                LocksmithConfig fallback = defaults();
                LocksmithConfig parsed = new LocksmithConfig(
                        bool(json, "explosion_protection", fallback.explosionProtection()),
                        bool(json, "allow_unpacked_loot_tables", fallback.allowUnpackedLootTables()),
                        integer(json, "max_locks_per_owner", fallback.maxLocksPerOwner()),
                        integer(json, "max_members_per_lock", fallback.maxMembersPerLock()),
                        integer(json, "max_keys_per_lock", fallback.maxKeysPerLock()),
                        integer(json, "max_network_positions", fallback.maxNetworkPositions())
                );
                install(parsed);
                return true;
            }
        } catch (IOException | RuntimeException exception) {
            TotemLocksmith.LOGGER.error("Keeping last valid Locksmith config after reload failure: {}", path, exception);
            return false;
        }
    }

    private static void writeDefaults(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        LocksmithConfig value = defaults();
        JsonObject json = new JsonObject();
        json.addProperty("explosion_protection", value.explosionProtection());
        json.addProperty("allow_unpacked_loot_tables", value.allowUnpackedLootTables());
        json.addProperty("max_locks_per_owner", value.maxLocksPerOwner());
        json.addProperty("max_members_per_lock", value.maxMembersPerLock());
        json.addProperty("max_keys_per_lock", value.maxKeysPerLock());
        json.addProperty("max_network_positions", value.maxNetworkPositions());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n",
                StandardCharsets.UTF_8);
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }
}
