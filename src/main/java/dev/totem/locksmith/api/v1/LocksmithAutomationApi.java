package dev.totem.locksmith.api.v1;

import dev.totem.locksmith.domain.AccessOperation;
import dev.totem.locksmith.service.LocksmithAccessService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

/** Stable, server-only v1 boundary for automation modules. Null means anonymous automation. */
public final class LocksmithAutomationApi {
    private LocksmithAutomationApi() {
    }

    public static boolean mayTransfer(Container source, Container destination, UUID operatorId) {
        return LocksmithAccessService.allowAutomationTransfer(source, destination, operatorId);
    }

    public static boolean mayExtract(Container source, UUID operatorId) {
        return evaluate(source, AccessOperation.EXTRACT, operatorId);
    }

    public static boolean mayInsert(Container destination, UUID operatorId) {
        return evaluate(destination, AccessOperation.INSERT, operatorId);
    }

    private static boolean evaluate(Container container, AccessOperation operation, UUID operatorId) {
        if (!(container instanceof BlockEntity blockEntity)
                || !(blockEntity.getLevel() instanceof ServerLevel level)) return true;
        return LocksmithAccessService.allowAutomationAt(
                level, blockEntity.getBlockPos(), operation, operatorId);
    }
}
