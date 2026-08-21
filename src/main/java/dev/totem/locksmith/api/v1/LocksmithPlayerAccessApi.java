package dev.totem.locksmith.api.v1;

import dev.totem.locksmith.domain.AccessOperation;
import dev.totem.locksmith.service.LocksmithAccessService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Stable, server-only v1 boundary for another mod performing a real player's
 * container action. The supplied player is authoritative; callers cannot
 * replace it with a UUID or an automation identity.
 */
public final class LocksmithPlayerAccessApi {
    private LocksmithPlayerAccessApi() {
    }

    public static boolean mayExtract(ServerPlayer player, ServerLevel level, BlockPos position) {
        return evaluate(player, level, position, AccessOperation.EXTRACT);
    }

    public static boolean mayInsert(ServerPlayer player, ServerLevel level, BlockPos position) {
        return evaluate(player, level, position, AccessOperation.INSERT);
    }

    private static boolean evaluate(
            ServerPlayer player,
            ServerLevel level,
            BlockPos position,
            AccessOperation operation
    ) {
        if (player == null || level == null || position == null
                || player.level() != level) {
            return false;
        }
        return LocksmithAccessService.playerDecision(player, position, operation).allowed();
    }
}
