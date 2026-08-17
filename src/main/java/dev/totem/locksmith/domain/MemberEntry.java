package dev.totem.locksmith.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.UUID;

public record MemberEntry(UUID playerId, String lastKnownName, MemberRole role) {
    private static final Codec<MemberRole> ROLE_CODEC = Codec.STRING.xmap(
            value -> MemberRole.valueOf(value.toUpperCase()),
            value -> value.name().toLowerCase()
    );
    public static final Codec<MemberEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player_id").forGetter(MemberEntry::playerId),
            Codec.STRING.optionalFieldOf("last_known_name", "Unknown").forGetter(MemberEntry::lastKnownName),
            ROLE_CODEC.fieldOf("role").forGetter(MemberEntry::role)
    ).apply(instance, MemberEntry::new));

    public MemberEntry {
        playerId = Objects.requireNonNull(playerId, "playerId");
        role = Objects.requireNonNull(role, "role");
        lastKnownName = SanitizedText.displayName(lastKnownName);
    }
}
