package dev.totem.locksmith.menu;

import dev.totem.locksmith.domain.MemberRole;
import dev.totem.locksmith.service.LocksmithAccessService;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/** Intercepts the owner/manager management gesture before normal container access. */
public final class LocksmithManagementInteraction {
    private LocksmithManagementInteraction() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide()
                    || !(world instanceof ServerLevel level)
                    || !(player instanceof ServerPlayer serverPlayer)
                    || !player.isShiftKeyDown()
                    || !player.getItemInHand(hand).isEmpty()) {
                return InteractionResult.PASS;
            }

            var resolved = LocksmithAccessService.resolve(level, hit.getBlockPos());
            var record = resolved.record().orElse(null);
            if (record == null) {
                return InteractionResult.PASS;
            }
            boolean owner = record.ownerId().equals(player.getUUID());
            boolean manager = record.member(player.getUUID())
                    .map(entry -> entry.role() == MemberRole.MANAGER)
                    .orElse(false);
            if (!owner && !manager) {
                return InteractionResult.PASS;
            }
            return LocksmithManagementMenuOpener.open(serverPlayer, record)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.FAIL;
        });
    }
}
