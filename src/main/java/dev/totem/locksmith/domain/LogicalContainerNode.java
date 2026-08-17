package dev.totem.locksmith.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** One logical fixed container; a double chest owns two sorted positions. */
public record LogicalContainerNode(ContainerKind kind, List<LockLocation> positions) {
    private static final Codec<ContainerKind> KIND_CODEC = Codec.STRING.xmap(
            value -> ContainerKind.valueOf(value.toUpperCase()),
            value -> value.name().toLowerCase()
    );
    public static final Codec<LogicalContainerNode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            KIND_CODEC.fieldOf("kind").forGetter(LogicalContainerNode::kind),
            LockLocation.CODEC.listOf().fieldOf("positions").forGetter(LogicalContainerNode::positions)
    ).apply(instance, LogicalContainerNode::new));

    public LogicalContainerNode {
        kind = Objects.requireNonNull(kind, "kind");
        positions = positions == null ? List.of() : positions.stream().distinct().sorted().toList();
        if (positions.isEmpty() || positions.size() > 2) {
            throw new IllegalArgumentException("logical containers require one or two positions");
        }
    }

    public LockLocation anchor() {
        return positions.stream().min(Comparator.naturalOrder()).orElseThrow();
    }

    public boolean contains(LockLocation location) {
        return positions.contains(location);
    }
}
