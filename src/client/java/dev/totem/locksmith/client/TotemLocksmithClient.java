package dev.totem.locksmith.client;

import dev.totem.locksmith.client.manual.LocksmithManualPageOverlay;
import dev.totem.locksmith.client.manual.LocksmithManualRecipeCache;
import net.fabricmc.api.ClientModInitializer;

/** Client-only visual bootstrap. Server policy never depends on this class. */
public final class TotemLocksmithClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LocksmithManualRecipeCache.register();
        LocksmithManualPageOverlay.register();
        LocksmithManagementScreenRegistration.register();
    }
}
