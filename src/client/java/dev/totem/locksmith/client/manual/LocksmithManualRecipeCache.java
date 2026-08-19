package dev.totem.locksmith.client.manual;

import dev.totem.locksmith.network.LocksmithManualRecipesPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class LocksmithManualRecipeCache {
    private static volatile Map<String, LocksmithManualRecipesPayload.Entry> recipes = Map.of();
    private static volatile boolean synchronizedFromServer;

    private LocksmithManualRecipeCache() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                LocksmithManualRecipesPayload.TYPE,
                (payload, context) -> {
                    recipes = payload.recipes().stream().collect(Collectors.toUnmodifiableMap(
                            LocksmithManualRecipesPayload.Entry::id,
                            Function.identity()
                    ));
                    synchronizedFromServer = true;
                }
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static LocksmithManualRecipesPayload.Entry get(String id) {
        return recipes.get(id);
    }

    public static boolean isSynchronizedFromServer() {
        return synchronizedFromServer;
    }

    private static void clear() {
        recipes = Map.of();
        synchronizedFromServer = false;
    }
}
