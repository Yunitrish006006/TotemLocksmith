package dev.totem.locksmith.menu;

import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.MemberRole;
import dev.totem.locksmith.persistence.LocksmithSavedData;
import dev.totem.locksmith.registry.LocksmithGameRules;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Opens a fresh server-authored management snapshot for one lock. */
public final class LocksmithManagementMenuOpener {
    private LocksmithManagementMenuOpener() {
    }

    public static boolean open(ServerPlayer player, UUID lockId) {
        LockRecord record = LocksmithSavedData.forServer(player.level().getServer())
                .get(lockId)
                .orElse(null);
        return record != null && open(player, record);
    }

    public static boolean open(ServerPlayer player, LockRecord record) {
        boolean owner = record.ownerId().equals(player.getUUID());
        boolean manager = record.member(player.getUUID())
                .map(entry -> entry.role() == MemberRole.MANAGER)
                .orElse(false);
        if (!owner && !manager) {
            return false;
        }

        LocksmithManagementOpenData snapshot = snapshot(player, record, owner, manager);
        ExtendedMenuProvider<LocksmithManagementOpenData> provider = new ExtendedMenuProvider<>() {
            @Override
            public LocksmithManagementOpenData getScreenOpeningData(ServerPlayer ignored) {
                return snapshot;
            }

            @Override
            public Component getDisplayName() {
                return Component.translatable("gui.totem.locksmith.management.title");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
                return new LocksmithManagementMenu(
                        LocksmithMenus.MANAGEMENT,
                        containerId,
                        inventory,
                        snapshot
                );
            }
        };
        return player.openMenu(provider).isPresent();
    }

    static LocksmithManagementOpenData snapshot(
            ServerPlayer player,
            LockRecord record,
            boolean owner,
            boolean manager
    ) {
        Set<UUID> excluded = new HashSet<>();
        excluded.add(record.ownerId());
        record.members().forEach(member -> excluded.add(member.playerId()));

        var members = record.members().stream()
                .map(member -> new LocksmithManagementOpenData.MemberView(
                        member.playerId(), member.lastKnownName(), member.role().ordinal()))
                .toList();
        var keys = record.keys().stream()
                .map(key -> new LocksmithManagementOpenData.KeyView(key.keyId(), key.label()))
                .toList();
        var candidates = player.level().getServer().getPlayerList().getPlayers().stream()
                .filter(candidate -> !excluded.contains(candidate.getUUID()))
                .sorted(Comparator.comparing(candidate -> candidate.getGameProfile().name(),
                        String.CASE_INSENSITIVE_ORDER))
                .limit(LocksmithManagementMenu.MAX_ROWS)
                .map(candidate -> new LocksmithManagementOpenData.PlayerView(
                        candidate.getUUID(), candidate.getGameProfile().name()))
                .toList();

        return new LocksmithManagementOpenData(
                record.id(),
                record.revision(),
                record.ownerName(),
                owner,
                manager,
                LocksmithGameRules.requirePhysicalKeys(player.serverLevel()),
                record.accessMode().ordinal(),
                record.automationMode().ordinal(),
                record.logicalContainerCount(),
                record.connectors().size(),
                members,
                keys,
                candidates
        );
    }
}
