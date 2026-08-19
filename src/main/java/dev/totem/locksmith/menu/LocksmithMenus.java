package dev.totem.locksmith.menu;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;

/** Menu registrations for command-free Locksmith management. */
public final class LocksmithMenus {
    public static final Identifier MANAGEMENT_ID =
            Identifier.fromNamespaceAndPath("totem-locksmith", "management");

    public static final ExtendedMenuType<LocksmithManagementMenu, LocksmithManagementOpenData> MANAGEMENT =
            Registry.register(
                    BuiltInRegistries.MENU,
                    MANAGEMENT_ID,
                    new ExtendedMenuType<>(
                            (containerId, inventory, data) -> new LocksmithManagementMenu(
                                    clientType(), containerId, inventory, data),
                            LocksmithManagementOpenData.STREAM_CODEC
                    )
            );

    private LocksmithMenus() {
    }

    public static void register() {
        // Class loading performs the registry write.
    }

    private static MenuType<?> clientType() {
        return BuiltInRegistries.MENU.get(MANAGEMENT_ID)
                .map(reference -> reference.value())
                .orElseThrow();
    }
}
