package dev.totem.locksmith.mixin;

import dev.totem.locksmith.service.LocksmithAccessService;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(
            method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void totemLocksmith$guardAutomationBoundary(
            Container source,
            Container destination,
            ItemStack stack,
            Direction direction,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!LocksmithAccessService.allowAutomationTransfer(source, destination)) {
            cir.setReturnValue(stack);
        }
    }
}
