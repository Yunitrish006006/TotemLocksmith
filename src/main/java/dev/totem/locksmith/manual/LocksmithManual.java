package dev.totem.locksmith.manual;

import dev.totem.core.api.v1.manual.TotemManualSection;
import dev.totem.core.api.v1.manual.TotemModuleManualSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** The Locksmith method guide is recorded from its first supported container. */
public final class LocksmithManual {
    private static final TotemManualSection SECTION = new TotemManualSection(
            Identifier.fromNamespaceAndPath("totem", "locksmith/manual"),
            600,
            "book.totem.locksmith_manual.title",
            List.of(
                    "book.totem.locksmith_manual.page.1",
                    "book.totem.locksmith_manual.page.2",
                    "book.totem.locksmith_manual.page.3",
                    "book.totem.locksmith_manual.page.4"
            )
    );

    private LocksmithManual() {
    }

    public static void register() {
        TotemModuleManualSource.register(
                SECTION,
                Identifier.fromNamespaceAndPath("deadrecall", "locksmith_manual"),
                state -> state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)
        );
    }
}
