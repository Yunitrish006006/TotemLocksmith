package dev.totem.locksmith.mixin;

import dev.totem.locksmith.service.LocksmithAccessService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Locked BlockEntities keep stable positions and may not be piston-moved or destroyed. */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
    @Shadow @Final private Level level;

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void totemLocksmith$rejectLockedMovement(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !(level instanceof ServerLevel serverLevel)) return;
        PistonStructureResolver self = (PistonStructureResolver) (Object) this;
        boolean touchesLock = self.getToPush().stream()
                .anyMatch(position -> LocksmithAccessService.isProtectedMember(serverLevel, position))
                || self.getToDestroy().stream()
                .anyMatch(position -> LocksmithAccessService.isProtectedMember(serverLevel, position));
        if (touchesLock) cir.setReturnValue(false);
    }
}
