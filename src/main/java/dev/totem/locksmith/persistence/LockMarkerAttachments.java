package dev.totem.locksmith.persistence;

import dev.totem.locksmith.TotemLocksmith;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;
import java.util.UUID;

/** Non-authoritative persistent marker written to every protected container and connector. */
public final class LockMarkerAttachments {
    public static final AttachmentType<UUID> LOCK_ID = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath("totem", "locksmith/lock_id"),
            UUIDUtil.CODEC
    );

    private LockMarkerAttachments() {
    }

    public static void register() {
        TotemLocksmith.LOGGER.debug("Registered Locksmith lock marker attachment");
    }

    public static Optional<UUID> read(BlockEntity blockEntity) {
        return Optional.ofNullable(((AttachmentTarget) blockEntity).getAttached(LOCK_ID));
    }

    public static void write(BlockEntity blockEntity, UUID lockId) {
        ((AttachmentTarget) blockEntity).setAttached(LOCK_ID, lockId);
    }

    public static void clear(BlockEntity blockEntity) {
        ((AttachmentTarget) blockEntity).removeAttached(LOCK_ID);
    }
}
