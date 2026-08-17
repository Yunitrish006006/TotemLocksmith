package dev.totem.locksmith.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.Objects;

/** Stable Dimension + BlockPos identity used by the world-wide lock index. */
public record LockLocation(String dimension, int x, int y, int z) implements Comparable<LockLocation> {
    public static final Codec<LockLocation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(LockLocation::dimension),
            Codec.INT.fieldOf("x").forGetter(LockLocation::x),
            Codec.INT.fieldOf("y").forGetter(LockLocation::y),
            Codec.INT.fieldOf("z").forGetter(LockLocation::z)
    ).apply(instance, LockLocation::new));
    private static final Comparator<LockLocation> ORDER = Comparator
            .comparing(LockLocation::dimension)
            .thenComparingInt(LockLocation::x)
            .thenComparingInt(LockLocation::y)
            .thenComparingInt(LockLocation::z);

    public LockLocation {
        dimension = Objects.requireNonNull(dimension, "dimension").trim();
        if (dimension.isEmpty() || dimension.length() > 128) {
            throw new IllegalArgumentException("invalid dimension identifier");
        }
    }

    public static LockLocation of(ServerLevel level, BlockPos pos) {
        return new LockLocation(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockPos blockPos() {
        return new BlockPos(x, y, z);
    }

    @Override
    public int compareTo(LockLocation other) {
        return ORDER.compare(this, other);
    }
}
