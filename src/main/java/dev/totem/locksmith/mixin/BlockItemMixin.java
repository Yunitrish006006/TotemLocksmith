package dev.totem.locksmith.mixin;

import dev.totem.locksmith.service.LocksmithAuthority;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents an unauthorized placement from joining or merging a protected network. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Unique
    private static final ThreadLocal<LocksmithAuthority.PlacementGuard> TOTEM_LOCKSMITH$PLACEMENT =
            new ThreadLocal<>();

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void totemLocksmith$preflightPlacement(
            BlockPlaceContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        TOTEM_LOCKSMITH$PLACEMENT.remove();
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) return;
        BlockState proposed = ((BlockItem) (Object) this).getBlock().getStateForPlacement(context);
        if (proposed == null) return;
        LocksmithAuthority.PlacementGuard guard = LocksmithAuthority.preparePlacement(
                player, level, context.getClickedPos(), proposed, context.getHand(), context.getItemInHand());
        if (!guard.relevant()) return;
        if (!guard.allowed()) {
            player.sendOverlayMessage(Component.translatable(
                    "message.totem.locksmith.placement_failed." + guard.reason()));
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        TOTEM_LOCKSMITH$PLACEMENT.set(guard);
    }

    @Inject(method = "place", at = @At("RETURN"), cancellable = true)
    private void totemLocksmith$commitPlacement(
            BlockPlaceContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        LocksmithAuthority.PlacementGuard guard = TOTEM_LOCKSMITH$PLACEMENT.get();
        TOTEM_LOCKSMITH$PLACEMENT.remove();
        if (guard == null || !cir.getReturnValue().consumesAction()
                || !(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) return;
        if (!LocksmithAuthority.commitPlacement(player, level, guard)) {
            LocksmithAuthority.rollbackPlacement(player, level, guard);
            player.sendOverlayMessage(Component.translatable(
                    "message.totem.locksmith.placement_failed.commit"));
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
