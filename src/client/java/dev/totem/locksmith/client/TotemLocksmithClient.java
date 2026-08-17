package dev.totem.locksmith.client;

import net.fabricmc.api.ClientModInitializer;

/** Client-only visual bootstrap. Server policy never depends on this class. */
public final class TotemLocksmithClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Attached lock rendering and the management screen consume only server snapshots.
    }
}
