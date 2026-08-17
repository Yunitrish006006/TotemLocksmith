package dev.totem.locksmith.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.totem.locksmith.component.KeyBinding;
import dev.totem.locksmith.component.LocksmithDataComponents;
import dev.totem.locksmith.config.LocksmithConfig;
import dev.totem.locksmith.domain.AccessMode;
import dev.totem.locksmith.domain.AutomationMode;
import dev.totem.locksmith.domain.KeyGrant;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.MemberEntry;
import dev.totem.locksmith.domain.MemberRole;
import dev.totem.locksmith.persistence.LocksmithSavedData;
import dev.totem.locksmith.registry.LocksmithItems;
import dev.totem.locksmith.service.LocksmithAuthority;
import dev.totem.locksmith.service.LocksmithAccessService;
import dev.totem.locksmith.service.ResolvedLock;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Server-resolved control plane; no command accepts a client-supplied lock or owner identity. */
public final class LocksmithCommands {
    private LocksmithCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = Commands.literal("locksmith");
            root.then(Commands.literal("inspect").executes(context -> inspect(context.getSource().getPlayerOrException())));
            root.then(Commands.literal("mode")
                    .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (AccessMode mode : AccessMode.values()) builder.suggest(mode.name().toLowerCase(Locale.ROOT));
                                return builder.buildFuture();
                            })
                            .executes(context -> setMode(context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "mode")))));
            root.then(Commands.literal("automation")
                    .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (AutomationMode mode : AutomationMode.values()) builder.suggest(mode.name().toLowerCase(Locale.ROOT));
                                return builder.buildFuture();
                            })
                            .executes(context -> setAutomation(context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "mode")))));
            root.then(Commands.literal("member")
                    .then(Commands.literal("add")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .then(Commands.argument("role", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                for (MemberRole role : MemberRole.values()) builder.suggest(role.name().toLowerCase(Locale.ROOT));
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> addMember(
                                                    context.getSource().getPlayerOrException(),
                                                    EntityArgument.getPlayer(context, "player"),
                                                    StringArgumentType.getString(context, "role"))))))
                    .then(Commands.literal("remove")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> removeMember(
                                            context.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(context, "player"))))));
            root.then(Commands.literal("key")
                    .then(Commands.literal("bind").executes(context -> bindKey(
                            context.getSource().getPlayerOrException(), "Key")))
                    .then(Commands.literal("revoke")
                            .then(Commands.argument("key", UuidArgument.uuid())
                                    .executes(context -> revokeKey(
                                            context.getSource().getPlayerOrException(),
                                            UuidArgument.getUuid(context, "key")))))
                    .then(Commands.literal("rotate").executes(context -> rotateKeys(
                            context.getSource().getPlayerOrException()))));
            root.then(Commands.literal("remove").executes(context -> removeLock(
                    context.getSource().getPlayerOrException())));
            root.then(Commands.literal("transfer")
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(context -> transfer(
                                    context.getSource().getPlayerOrException(),
                                    EntityArgument.getPlayer(context, "player")))));
            root.then(Commands.literal("reload")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                    .executes(context -> {
                        boolean loaded = LocksmithConfig.reload();
                        context.getSource().sendSuccess(() -> Component.translatable(loaded
                                ? "message.totem.locksmith.config_reloaded"
                                : "message.totem.locksmith.config_reload_failed"), true);
                        return loaded ? 1 : 0;
                    }));
            dispatcher.register(root);
        });
    }

    private static int inspect(ServerPlayer player) {
        return target(player).map(record -> {
            player.sendSystemMessage(Component.translatable("message.totem.locksmith.inspect",
                    record.logicalContainerCount(), record.connectors().size(), record.accessMode().name(),
                    record.automationMode().name(), record.revision()));
            return 1;
        }).orElseGet(() -> failure(player, "message.totem.locksmith.no_target"));
    }

    private static int setMode(ServerPlayer player, String raw) {
        AccessMode mode;
        try {
            mode = AccessMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return failure(player, "message.totem.locksmith.invalid_mode");
        }
        return mutateOwner(player, record -> record.withModes(mode, record.automationMode()));
    }

    private static int setAutomation(ServerPlayer player, String raw) {
        AutomationMode mode;
        try {
            mode = AutomationMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return failure(player, "message.totem.locksmith.invalid_mode");
        }
        return mutateOwner(player, record -> record.withModes(record.accessMode(), mode));
    }

    private static int addMember(ServerPlayer actor, ServerPlayer target, String rawRole) {
        MemberRole role;
        try {
            role = MemberRole.valueOf(rawRole.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return failure(actor, "message.totem.locksmith.invalid_role");
        }
        return mutateManager(actor, role == MemberRole.MANAGER, record -> {
            if (target.getUUID().equals(record.ownerId())) return record;
            List<MemberEntry> entries = new ArrayList<>(record.members());
            entries.removeIf(entry -> entry.playerId().equals(target.getUUID()));
            entries.add(new MemberEntry(target.getUUID(), target.getGameProfile().name(), role));
            return record.withMembers(entries);
        });
    }

    private static int removeMember(ServerPlayer actor, ServerPlayer target) {
        Optional<LockRecord> found = target(actor);
        if (found.isEmpty()) return failure(actor, "message.totem.locksmith.no_target");
        boolean ownerOnly = found.get().member(target.getUUID())
                .map(entry -> entry.role() == MemberRole.MANAGER).orElse(false);
        return mutateManager(actor, ownerOnly, record -> record.withMembers(record.members().stream()
                .filter(entry -> !entry.playerId().equals(target.getUUID())).toList()));
    }

    private static int bindKey(ServerPlayer player, String label) {
        Optional<LockRecord> found = target(player);
        if (found.isEmpty()) return failure(player, "message.totem.locksmith.no_target");
        LockRecord record = found.get();
        if (!mayManage(player, record, false)) return failure(player, "message.totem.locksmith.denied");
        InteractionHand hand = player.getMainHandItem().is(LocksmithItems.KEY_BLANK)
                ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().is(LocksmithItems.KEY_BLANK) ? InteractionHand.OFF_HAND : null;
        if (hand == null) return failure(player, "message.totem.locksmith.need_key_blank");
        UUID keyId = UUID.randomUUID();
        KeyGrant grant = new KeyGrant(keyId, label, record.keyEpoch());
        List<KeyGrant> keys = new ArrayList<>(record.keys());
        keys.add(grant);
        LockRecord replacement;
        try {
            replacement = record.withKeys(keys);
        } catch (IllegalArgumentException limit) {
            return failure(player, "message.totem.locksmith.key_limit");
        }
        if (!data(player).replace(record, replacement)) return failure(player, "message.totem.locksmith.stale");
        if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
        ItemStack key = new ItemStack(LocksmithItems.BOUND_KEY);
        key.set(LocksmithDataComponents.KEY_BINDING, new KeyBinding(record.id(), keyId, record.keyEpoch()));
        if (!player.getInventory().add(key)) player.drop(key, false);
        player.sendSystemMessage(Component.translatable("message.totem.locksmith.key_bound", keyId.toString()));
        return 1;
    }

    private static int revokeKey(ServerPlayer player, UUID keyId) {
        return mutateManager(player, false, record -> record.withKeys(record.keys().stream()
                .filter(key -> !key.keyId().equals(keyId)).toList()));
    }

    private static int rotateKeys(ServerPlayer player) {
        return mutateOwner(player, LockRecord::rotateKeys);
    }

    private static int removeLock(ServerPlayer player) {
        Optional<LockRecord> found = target(player);
        if (found.isEmpty()) return failure(player, "message.totem.locksmith.no_target");
        if (!LocksmithAuthority.removeLock(player, found.get())) {
            return failure(player, "message.totem.locksmith.denied");
        }
        player.sendSystemMessage(Component.translatable("message.totem.locksmith.removed"));
        return 1;
    }

    private static int transfer(ServerPlayer actor, ServerPlayer newOwner) {
        return mutateOwner(actor, record -> record.transfer(newOwner.getUUID(), newOwner.getGameProfile().name()));
    }

    private static int mutateOwner(ServerPlayer player, java.util.function.UnaryOperator<LockRecord> mutation) {
        return mutateManager(player, true, mutation);
    }

    private static int mutateManager(
            ServerPlayer player,
            boolean ownerOnly,
            java.util.function.UnaryOperator<LockRecord> mutation
    ) {
        Optional<LockRecord> found = target(player);
        if (found.isEmpty()) return failure(player, "message.totem.locksmith.no_target");
        LockRecord record = found.get();
        if (!mayManage(player, record, ownerOnly)) return failure(player, "message.totem.locksmith.denied");
        LockRecord replacement;
        try {
            replacement = mutation.apply(record);
        } catch (IllegalArgumentException invalid) {
            return failure(player, "message.totem.locksmith.limit");
        }
        if (replacement == record || replacement.equals(record)) return failure(player, "message.totem.locksmith.no_change");
        if (!data(player).replace(record, replacement)) return failure(player, "message.totem.locksmith.stale");
        player.sendSystemMessage(Component.translatable("message.totem.locksmith.updated", replacement.revision()));
        return 1;
    }

    private static boolean mayManage(ServerPlayer player, LockRecord record, boolean ownerOnly) {
        if (record.ownerId().equals(player.getUUID())) return true;
        return !ownerOnly && record.member(player.getUUID())
                .map(entry -> entry.role() == MemberRole.MANAGER).orElse(false);
    }

    private static Optional<LockRecord> target(ServerPlayer player) {
        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) return Optional.empty();
        ResolvedLock resolved = LocksmithAccessService.resolve((net.minecraft.server.level.ServerLevel) player.level(),
                blockHit.getBlockPos());
        return resolved.record();
    }

    private static LocksmithSavedData data(ServerPlayer player) {
        return LocksmithSavedData.forServer(player.level().getServer());
    }

    private static int failure(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key));
        return 0;
    }
}
