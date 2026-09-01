package dev.totem.locksmith.registry;

import dev.totem.core.api.v1.gamerule.TotemGameRuleCategories;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;

/** Persistent per-world Locksmith access model switch. */
public final class LocksmithGameRules {
    /**
     * true = immersive: non-owner/non-manager players must present a valid Bound Key.
     * false = convenient: normal AccessMode rules (including FRIENDS) may grant access.
     */
    public static final GameRule<Boolean> REQUIRE_PHYSICAL_KEYS =
            GameRuleBuilder.forBoolean(true)
                    .category(TotemGameRuleCategories.TOTEM)
                    .buildAndRegister(Identifier.fromNamespaceAndPath(
                            "totem", "locksmith_require_physical_keys"));

    private LocksmithGameRules() {
    }

    public static void register() {
        // Class initialization registers the rule.
    }

    public static boolean requirePhysicalKeys(ServerLevel level) {
        return level.getGameRules().get(REQUIRE_PHYSICAL_KEYS);
    }
}
