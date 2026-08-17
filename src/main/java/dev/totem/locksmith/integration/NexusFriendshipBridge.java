package dev.totem.locksmith.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.UUID;

/** Optional fail-closed Nexus v1 friendship lookup. */
public final class NexusFriendshipBridge {
    private static volatile Method method;
    private static volatile boolean resolved;

    private NexusFriendshipBridge() {
    }

    public static boolean areMutualFriends(MinecraftServer server, UUID first, UUID second) {
        if (!FabricLoader.getInstance().isModLoaded("totem-nexus")) return false;
        Method current = resolve();
        if (current == null) return false;
        try {
            return (boolean) current.invoke(null, server, first, second);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static Method resolve() {
        if (resolved) return method;
        synchronized (NexusFriendshipBridge.class) {
            if (resolved) return method;
            try {
                method = Class.forName("dev.totem.nexus.api.v1.NexusFriendshipApi")
                        .getMethod("areMutualFriends", MinecraftServer.class, UUID.class, UUID.class);
            } catch (ReflectiveOperationException ignored) {
                method = null;
            }
            resolved = true;
            return method;
        }
    }
}
