package dev.totem.locksmith.item;

import dev.totem.locksmith.component.KeyBinding;
import dev.totem.locksmith.component.LocksmithDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public final class BoundKeyItem extends Item {
    public BoundKeyItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> textConsumer,
            TooltipFlag flag
    ) {
        KeyBinding binding = stack.get(LocksmithDataComponents.KEY_BINDING);
        if (binding == null) {
            textConsumer.accept(Component.translatable("tooltip.totem.locksmith.key_unbound")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        textConsumer.accept(Component.translatable("tooltip.totem.locksmith.key_bound")
                .withStyle(ChatFormatting.GRAY));
        textConsumer.accept(Component.translatable("tooltip.totem.locksmith.key_epoch", binding.epoch())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(LocksmithDataComponents.KEY_BINDING);
    }
}
