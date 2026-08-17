package dev.totem.locksmith.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.UUID;

public record KeyGrant(UUID keyId, String label, int epoch) {
    public static final Codec<KeyGrant> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("key_id").forGetter(KeyGrant::keyId),
            Codec.STRING.optionalFieldOf("label", "Key").forGetter(KeyGrant::label),
            Codec.INT.fieldOf("epoch").forGetter(KeyGrant::epoch)
    ).apply(instance, KeyGrant::new));

    public KeyGrant {
        keyId = Objects.requireNonNull(keyId, "keyId");
        label = SanitizedText.label(label);
        if (epoch < 0) throw new IllegalArgumentException("epoch must be non-negative");
    }
}
