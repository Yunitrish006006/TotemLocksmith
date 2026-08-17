package dev.totem.locksmith.mixin;

import dev.totem.locksmith.service.LocksmithAccessService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerBlockEntityMixin {
    @Inject(method = "canOpen", at = @At("HEAD"), cancellable = true)
    private void totemLocksmith$denyUnauthorizedOpen(Player player, CallbackInfoReturnable<Boolean> cir) {
        BaseContainerBlockEntity self = (BaseContainerBlockEntity) (Object) this;
        if (player instanceof ServerPlayer serverPlayer
                && !LocksmithAccessService.mayOpen(serverPlayer, self.getBlockPos())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void totemLocksmith$closeRevokedMenu(Player player, CallbackInfoReturnable<Boolean> cir) {
        BaseContainerBlockEntity self = (BaseContainerBlockEntity) (Object) this;
        if (player instanceof ServerPlayer serverPlayer
                && !LocksmithAccessService.mayOpen(serverPlayer, self.getBlockPos())) {
            cir.setReturnValue(false);
        }
    }
}
