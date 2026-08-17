package dev.totem.locksmith.mixin;

import dev.totem.locksmith.config.LocksmithConfig;
import dev.totem.locksmith.service.LocksmithAccessService;
import dev.totem.locksmith.service.LocksmithAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies the configured explosion policy without attributing environment damage to a player. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Shadow @Final private ServerLevel level;

    @Unique
    private Map<BlockPos, BlockState> totemLocksmith$environmentMembers = Map.of();

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
    private void totemLocksmith$protectLockedMembers(
            CallbackInfoReturnable<List<BlockPos>> cir
    ) {
        if (!LocksmithConfig.active().explosionProtection()) return;
        List<BlockPos> filtered = new ArrayList<>(cir.getReturnValue());
        filtered.removeIf(position -> LocksmithAccessService.isProtectedMember(level, position));
        // Vanilla shuffles this list in-place before processing drops.
        cir.setReturnValue(filtered);
    }

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void totemLocksmith$captureEnvironmentMembers(
            List<BlockPos> positions,
            CallbackInfo ci
    ) {
        if (LocksmithConfig.active().explosionProtection()) {
            totemLocksmith$environmentMembers = Map.of();
            return;
        }
        Map<BlockPos, BlockState> captured = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            if (LocksmithAccessService.isProtectedMember(level, position)) {
                captured.put(position.immutable(), level.getBlockState(position));
            }
        }
        totemLocksmith$environmentMembers = Map.copyOf(captured);
    }

    @Inject(method = "interactWithBlocks", at = @At("RETURN"))
    private void totemLocksmith$finalizeEnvironmentMembers(
            List<BlockPos> positions,
            CallbackInfo ci
    ) {
        Map<BlockPos, BlockState> captured = totemLocksmith$environmentMembers;
        totemLocksmith$environmentMembers = Map.of();
        captured.forEach((position, state) -> {
            if (!level.getBlockState(position).is(state.getBlock())) {
                LocksmithAuthority.finalizeEnvironmentBrokenMember(level, position, state, null);
            }
        });
    }
}
