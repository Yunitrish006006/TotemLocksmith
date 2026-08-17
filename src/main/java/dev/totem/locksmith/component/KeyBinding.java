package dev.totem.locksmith.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.UUID;

/** Per-stack identity; authority additionally requires a matching active KeyGrant. */
public record KeyBinding(UUID lockId, UUID keyId, int epoch) {
    public static final Codec<KeyBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("lock_id").forGetter(KeyBinding::lockId),
            UUIDUtil.CODEC.fieldOf("key_id").forGetter(KeyBinding::keyId),
            Codec.INT.fieldOf("epoch").forGetter(KeyBinding::epoch)
    ).apply(instance, KeyBinding::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, KeyBinding> STREAM_CODEC = StreamCodec.of(
            (buffer, binding) -> {
                buffer.writeUUID(binding.lockId());
                buffer.writeUUID(binding.keyId());
                buffer.writeVarInt(binding.epoch());
            },
            buffer -> new KeyBinding(buffer.readUUID(), buffer.readUUID(), buffer.readVarInt())
    );

    public KeyBinding {
        lockId = Objects.requireNonNull(lockId, "lockId");
        keyId = Objects.requireNonNull(keyId, "keyId");
        if (epoch < 0) throw new IllegalArgumentException("epoch must be non-negative");
    }
}
