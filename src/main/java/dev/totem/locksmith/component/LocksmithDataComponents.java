package dev.totem.locksmith.component;

import dev.totem.locksmith.TotemLocksmith;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class LocksmithDataComponents {
    public static final DataComponentType<KeyBinding> KEY_BINDING = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath("totem", "locksmith/key_binding"),
            DataComponentType.<KeyBinding>builder()
                    .persistent(KeyBinding.CODEC)
                    .networkSynchronized(KeyBinding.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    private LocksmithDataComponents() {
    }

    public static void register() {
        TotemLocksmith.LOGGER.debug("Registered Locksmith data components");
    }
}
