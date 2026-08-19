package dev.totem.locksmith.manual;

import dev.totem.locksmith.network.LocksmithManualRecipesPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.SlotDisplayContext;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps Locksmith manual recipe diagrams synchronized with live server recipes. */
public final class LocksmithManualRecipeSync {
    public static final List<Identifier> RECIPE_IDS = List.of(
            recipeId("padlock"),
            recipeId("key_blank"),
            recipeId("key_blank_copper")
    );
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private LocksmithManualRecipeSync() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        PayloadTypeRegistry.clientboundPlay().register(
                LocksmithManualRecipesPayload.TYPE,
                LocksmithManualRecipesPayload.CODEC
        );
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> send(player));
    }

    private static void send(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, LocksmithManualRecipesPayload.TYPE)) return;
        ContextMap context = SlotDisplayContext.fromLevel(player.level());
        ServerPlayNetworking.send(player, new LocksmithManualRecipesPayload(
                RECIPE_IDS.stream().map(id -> readRecipe(player, context, id)).toList()
        ));
    }

    private static LocksmithManualRecipesPayload.Entry readRecipe(ServerPlayer player, ContextMap context, Identifier id) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id);
        Optional<RecipeHolder<?>> holder = player.level().getServer().getRecipeManager().byKey(key);
        if (holder.isEmpty()) return LocksmithManualRecipesPayload.Entry.unavailable(id.toString());
        Optional<ShapedCraftingRecipeDisplay> display = holder.get().value().display().stream()
                .filter(ShapedCraftingRecipeDisplay.class::isInstance)
                .map(ShapedCraftingRecipeDisplay.class::cast)
                .findFirst();
        if (display.isEmpty()) return LocksmithManualRecipesPayload.Entry.unavailable(id.toString());
        ShapedCraftingRecipeDisplay shaped = display.get();
        List<ItemStack> ingredients = shaped.ingredients().stream()
                .map(slot -> slot.resolveForFirstStack(context)).toList();
        ItemStack result = shaped.result().resolveForFirstStack(context);
        if (result.isEmpty()) return LocksmithManualRecipesPayload.Entry.unavailable(id.toString());
        return new LocksmithManualRecipesPayload.Entry(
                id.toString(), true, shaped.width(), shaped.height(), ingredients, result
        );
    }

    private static Identifier recipeId(String path) {
        return Identifier.fromNamespaceAndPath("totem", "locksmith/" + path);
    }
}
