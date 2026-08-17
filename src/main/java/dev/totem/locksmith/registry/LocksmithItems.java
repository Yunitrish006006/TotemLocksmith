package dev.totem.locksmith.registry;

import dev.totem.locksmith.item.BoundKeyItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class LocksmithItems {
    public static final Item PADLOCK = register("padlock", new Item.Properties().stacksTo(16), false);
    public static final Item KEY_BLANK = register("key_blank", new Item.Properties(), false);
    public static final BoundKeyItem BOUND_KEY = (BoundKeyItem) register(
            "bound_key", new Item.Properties().stacksTo(1), true);
    private static boolean initialized;

    private LocksmithItems() {
    }

    public static synchronized void register() {
        if (initialized) return;
        ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(
                Registries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath("totem-locksmith", "main")
        );
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.totem_locksmith.main"))
                        .icon(() -> new ItemStack(PADLOCK))
                        .build());
        CreativeModeTabEvents.modifyOutputEvent(tabKey).register(LocksmithItems::addCreativeItems);
        initialized = true;
    }

    private static Item register(String path, Item.Properties properties, boolean boundKey) {
        Identifier id = Identifier.fromNamespaceAndPath("totem", "locksmith/" + path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = boundKey
                ? new BoundKeyItem(properties.setId(key))
                : new Item(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static void addCreativeItems(FabricCreativeModeTabOutput output) {
        output.accept(PADLOCK);
        output.accept(KEY_BLANK);
        output.accept(BOUND_KEY);
    }
}
