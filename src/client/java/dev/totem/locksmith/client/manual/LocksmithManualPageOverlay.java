package dev.totem.locksmith.client.manual;

import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import dev.totem.locksmith.manual.LocksmithManualRecipeSync;
import dev.totem.locksmith.network.LocksmithManualRecipesPayload;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Draws live crafting grids on Locksmith manual recipe pages. */
public final class LocksmithManualPageOverlay {
    private static final String RECIPE_PAGE_PREFIX = "book.totem.locksmith_manual.recipe.page.";
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier CRAFTING_BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");

    private LocksmithManualPageOverlay() {}

    public static void register() {
        TotemManualPageOverlayRegistry.register(
                Identifier.fromNamespaceAndPath("totem-locksmith", "manual_recipes"),
                LocksmithManualPageOverlay::render
        );
    }

    private static void render(TotemManualPageRenderContext context) {
        if (context.pageKey() == null || !context.pageKey().startsWith(RECIPE_PAGE_PREFIX)) return;
        int recipeIndex;
        try {
            recipeIndex = Integer.parseInt(context.pageKey().substring(RECIPE_PAGE_PREFIX.length())) - 1;
        } catch (NumberFormatException ignored) {
            return;
        }
        if (recipeIndex < 0 || recipeIndex >= LocksmithManualRecipeSync.RECIPE_IDS.size()) return;

        String recipeId = LocksmithManualRecipeSync.RECIPE_IDS.get(recipeIndex).toString();
        LocksmithManualRecipesPayload.Entry recipe = LocksmithManualRecipeCache.get(recipeId);
        if (recipe == null || !recipe.available()) {
            Component status = Component.translatable(
                    LocksmithManualRecipeCache.isSynchronizedFromServer()
                            ? "book.totem.locksmith_manual.recipe.unavailable"
                            : "book.totem.locksmith_manual.recipe.loading"
            );
            context.graphics().centeredText(context.font(), status,
                    context.pageLeft() + 93, context.pageTop() + 86, 0xFF9B2C20);
            return;
        }

        int gridLeft = context.pageLeft() + 43;
        int gridTop = context.pageTop() + 61;
        for (int slot = 0; slot < 9; slot++) {
            int x = gridLeft + slot % 3 * 18;
            int y = gridTop + slot / 3 * 18;
            context.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x, y, 18, 18);
            ItemStack ingredient = ingredientAt(recipe, slot);
            if (!ingredient.isEmpty()) renderStack(context, ingredient, x, y);
        }

        context.graphics().blit(RenderPipelines.GUI_TEXTURED, CRAFTING_BACKGROUND,
                gridLeft + 60, gridTop + 20, 89.0F, 34.0F, 15, 15, 256, 256);
        int resultX = gridLeft + 79;
        int resultY = gridTop + 18;
        context.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, resultX, resultY, 18, 18);
        renderStack(context, recipe.result(), resultX, resultY);
        context.graphics().itemDecorations(context.font(), recipe.result(), resultX + 1, resultY + 1);
        context.graphics().centeredText(context.font(),
                Component.translatable("book.totem.locksmith_manual.recipe.live"),
                context.pageLeft() + 93, context.pageTop() + 127, 0xFF6F5637);
    }

    private static ItemStack ingredientAt(LocksmithManualRecipesPayload.Entry recipe, int gridSlot) {
        int row = gridSlot / 3;
        int column = gridSlot % 3;
        if (row >= recipe.height() || column >= recipe.width()) return ItemStack.EMPTY;
        int recipeSlot = row * recipe.width() + column;
        return recipeSlot < recipe.ingredients().size() ? recipe.ingredients().get(recipeSlot) : ItemStack.EMPTY;
    }

    private static void renderStack(TotemManualPageRenderContext context, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        context.graphics().item(stack, x + 1, y + 1);
        if (context.mouseX() >= x && context.mouseX() < x + 18 && context.mouseY() >= y && context.mouseY() < y + 18) {
            context.graphics().setTooltipForNextFrame(context.font(), stack, context.mouseX(), context.mouseY());
        }
    }
}
