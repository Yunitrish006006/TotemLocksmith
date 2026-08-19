package dev.totem.locksmith.menu;

import dev.totem.locksmith.component.KeyBinding;
import dev.totem.locksmith.component.LocksmithDataComponents;
import dev.totem.locksmith.domain.AccessMode;
import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.MemberEntry;
import dev.totem.locksmith.domain.MemberRole;
import dev.totem.locksmith.persistence.LocksmithSavedData;
import dev.totem.locksmith.registry.LocksmithItems;
import dev.totem.locksmith.service.LocksmithAuthority;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for menu-button mutations; the client never fabricates lock state. */
public final class LocksmithManagementMenuGameTest {
    private static final BlockPos CHEST = new BlockPos(2, 2, 2);

    @GameTest(maxTicks = 40)
    public void ownerCanChangeAccessModeFromManagementMenu(GameTestHelper helper) {
        Fixture fixture = createLock(helper);
        try {
            LocksmithManagementMenu menu = menuFor(fixture.owner(), fixture.record(), true, false);
            require(helper, menu.clickMenuButton(
                    fixture.owner(),
                    LocksmithManagementMenu.ACCESS_BASE + AccessMode.FRIENDS.ordinal()),
                    "Owner access-mode button was rejected");
            LockRecord updated = data(helper).get(fixture.record().id()).orElseThrow();
            require(helper, updated.accessMode() == AccessMode.FRIENDS,
                    "Access-mode button did not persist FRIENDS to SavedData");
        } finally {
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void managerCannotChangeOwnerOnlyAccessMode(GameTestHelper helper) {
        Fixture fixture = createLock(helper);
        ServerPlayer manager = helper.makeMockServerPlayerInLevel();
        try {
            List<MemberEntry> members = new ArrayList<>(fixture.record().members());
            members.add(new MemberEntry(manager.getUUID(), manager.getGameProfile().name(), MemberRole.MANAGER));
            LockRecord withManager = fixture.record().withMembers(members);
            require(helper, data(helper).replace(fixture.record(), withManager),
                    "Could not seed Manager membership");

            LocksmithManagementMenu menu = menuFor(manager, withManager, false, true);
            require(helper, !menu.clickMenuButton(
                            manager,
                            LocksmithManagementMenu.ACCESS_BASE + AccessMode.PUBLIC.ordinal()),
                    "Manager bypassed owner-only access-mode control");
            LockRecord unchanged = data(helper).get(withManager.id()).orElseThrow();
            require(helper, unchanged.accessMode() == withManager.accessMode(),
                    "Rejected Manager action still changed the access mode");
        } finally {
            manager.closeContainer();
            manager.discard();
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void issueKeyConsumesBlankAndCreatesMatchingBoundKey(GameTestHelper helper) {
        Fixture fixture = createLock(helper);
        try {
            fixture.owner().getAbilities().instabuild = false;
            fixture.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LocksmithItems.KEY_BLANK));
            LocksmithManagementMenu menu = menuFor(fixture.owner(), fixture.record(), true, false);
            require(helper, menu.clickMenuButton(fixture.owner(), LocksmithManagementMenu.BIND_KEY),
                    "Issue Key button was rejected");

            LockRecord updated = data(helper).get(fixture.record().id()).orElseThrow();
            require(helper, updated.keys().size() == 1,
                    "Issue Key did not add exactly one key grant");
            require(helper, fixture.owner().getMainHandItem().isEmpty(),
                    "Issue Key did not consume the Key Blank");

            ItemStack bound = findBoundKey(fixture.owner());
            require(helper, !bound.isEmpty(), "Issue Key did not place a Bound Key in inventory");
            KeyBinding binding = bound.get(LocksmithDataComponents.KEY_BINDING);
            require(helper, binding != null
                            && binding.lockId().equals(updated.id())
                            && binding.keyId().equals(updated.keys().getFirst().keyId())
                            && binding.epoch() == updated.keyEpoch(),
                    "Issued Bound Key metadata did not match the persisted grant");
        } finally {
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void ownerCanAddOnlinePlayerWithoutCommand(GameTestHelper helper) {
        Fixture fixture = createLock(helper);
        ServerPlayer candidate = helper.makeMockServerPlayerInLevel();
        try {
            LocksmithManagementOpenData snapshot = LocksmithManagementMenuOpener.snapshot(
                    fixture.owner(), fixture.record(), true, false);
            int index = -1;
            for (int i = 0; i < snapshot.candidates().size(); i++) {
                if (snapshot.candidates().get(i).playerId().equals(candidate.getUUID())) {
                    index = i;
                    break;
                }
            }
            require(helper, index >= 0, "Online candidate was absent from management snapshot");
            LocksmithManagementMenu menu = new LocksmithManagementMenu(
                    LocksmithMenus.MANAGEMENT, 4, fixture.owner().getInventory(), snapshot);
            require(helper, menu.clickMenuButton(
                            fixture.owner(), LocksmithManagementMenu.CANDIDATE_ADD_BASE + index),
                    "Add-player button was rejected");
            LockRecord updated = data(helper).get(fixture.record().id()).orElseThrow();
            require(helper, updated.member(candidate.getUUID())
                            .map(member -> member.role() == MemberRole.USER)
                            .orElse(false),
                    "Online candidate was not persisted as a USER member");
        } finally {
            candidate.closeContainer();
            candidate.discard();
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    private static Fixture createLock(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LocksmithItems.PADLOCK));
        require(helper, LocksmithAuthority.applyPadlock(
                        owner, helper.getLevel(), helper.absolutePos(CHEST), InteractionHand.MAIN_HAND),
                "Could not create test lock");
        LockRecord record = data(helper)
                .findAt(LockLocation.of(helper.getLevel(), helper.absolutePos(CHEST)))
                .orElseThrow();
        return new Fixture(owner, record);
    }

    private static LocksmithManagementMenu menuFor(
            ServerPlayer actor,
            LockRecord record,
            boolean owner,
            boolean manager
    ) {
        return new LocksmithManagementMenu(
                LocksmithMenus.MANAGEMENT,
                3,
                actor.getInventory(),
                LocksmithManagementMenuOpener.snapshot(actor, record, owner, manager)
        );
    }

    private static ItemStack findBoundKey(ServerPlayer player) {
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(LocksmithItems.BOUND_KEY)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static LocksmithSavedData data(GameTestHelper helper) {
        return LocksmithSavedData.forServer(helper.getLevel().getServer());
    }

    private static void cleanup(GameTestHelper helper, Fixture fixture) {
        fixture.owner().closeContainer();
        data(helper).remove(fixture.record().id());
        fixture.owner().discard();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    private record Fixture(ServerPlayer owner, LockRecord record) { }
}
