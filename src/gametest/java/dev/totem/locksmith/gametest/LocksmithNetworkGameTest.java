package dev.totem.locksmith.gametest;

import dev.totem.core.api.v1.event.LockedContainerNetworkBrokenEvent;
import dev.totem.core.api.v1.event.TotemEventBus;
import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.persistence.LockMarkerAttachments;
import dev.totem.locksmith.persistence.LocksmithSavedData;
import dev.totem.locksmith.registry.LocksmithItems;
import dev.totem.locksmith.service.LockBreakResult;
import dev.totem.locksmith.service.LocksmithAccessService;
import dev.totem.locksmith.service.LocksmithAuthority;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LocksmithNetworkGameTest {
    private static final BlockPos ROOT_CHEST = new BlockPos(1, 3, 1);
    private static final BlockPos HOPPER = new BlockPos(1, 2, 1);
    private static final BlockPos DESTINATION = new BlockPos(2, 2, 1);

    @GameTest(maxTicks = 40)
    public void canonicalItemsAndRecipesLoad(GameTestHelper helper) {
        require(helper, LocksmithItems.PADLOCK.builtInRegistryHolder().isBound(), "Padlock was not registered");
        require(helper, LocksmithItems.KEY_BLANK.builtInRegistryHolder().isBound(), "Key Blank was not registered");
        require(helper, LocksmithItems.BOUND_KEY.builtInRegistryHolder().isBound(), "Bound Key was not registered");
        require(helper, helper.getLevel().recipeAccess().byKey(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE,
                net.minecraft.resources.Identifier.parse("totem:locksmith/padlock"))).isPresent(),
                "Padlock recipe was not loaded");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void onePadlockProtectsFixedHopperNetwork(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        require(helper, fixture.record().logicalContainerCount() == 2, "Expected two logical containers");
        require(helper, fixture.record().connectors().size() == 1, "Expected one Hopper connector");
        require(helper, fixture.owner().getMainHandItem().isEmpty(), "Padlock was not consumed exactly once");
        for (LockLocation location : fixture.record().allPositions()) {
            BlockEntity blockEntity = helper.getLevel().getBlockEntity(location.blockPos());
            require(helper, blockEntity != null && LockMarkerAttachments.read(blockEntity)
                    .filter(fixture.record().id()::equals).isPresent(), "Missing shared Lock UUID marker");
        }
        cleanup(fixture);
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void middleHopperSplitKeepsOnlyOriginalRootSide(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        BlockPos hopper = helper.absolutePos(HOPPER);
        BlockState state = helper.getLevel().getBlockState(hopper);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(hopper);
        helper.getLevel().removeBlock(hopper, false);
        LockBreakResult result = LocksmithAuthority.finalizeBrokenMember(
                helper.getLevel(), fixture.owner(), hopper, state, blockEntity).orElseThrow();
        require(helper, !result.lockRemoved(), "Middle Hopper incorrectly removed the lock");
        require(helper, result.detachedUnlockedContainers() == 1, "Detached container count was not one");
        LockRecord remaining = result.after().orElseThrow();
        require(helper, remaining.logicalContainerCount() == 1, "Root component did not retain exactly one container");
        require(helper, LocksmithAccessService.resolve(helper.getLevel(), helper.absolutePos(ROOT_CHEST)).record().isPresent(),
                "Original root side became unlocked");
        require(helper, LocksmithAccessService.resolve(helper.getLevel(), helper.absolutePos(DESTINATION)).status()
                        == dev.totem.locksmith.service.ResolvedLock.Status.UNLOCKED,
                "Detached destination did not unlock immediately");
        cleanup(new Fixture(fixture.owner(), remaining));
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void nonOwnerConnectorBreakPublishesExactlyOneCommittedEvent(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        List<LockedContainerNetworkBrokenEvent> events = new ArrayList<>();
        try (TotemEventBus.Subscription ignored = TotemEventBus.subscribe(
                LockedContainerNetworkBrokenEvent.class, events::add)) {
            BlockPos hopper = helper.absolutePos(HOPPER);
            BlockState state = helper.getLevel().getBlockState(hopper);
            BlockEntity blockEntity = helper.getLevel().getBlockEntity(hopper);
            helper.getLevel().removeBlock(hopper, false);
            LocksmithAuthority.finalizeBrokenMember(helper.getLevel(), stranger, hopper, state, blockEntity);
        }
        require(helper, events.size() == 1, "Non-owner break did not publish exactly one event");
        LockedContainerNetworkBrokenEvent event = events.getFirst();
        require(helper, event.remainingLockedContainers() == 1 && event.detachedUnlockedContainers() == 1,
                "Discord event used incorrect committed component counts");
        require(helper, !event.lockRemoved() && !event.rootMoved(), "Middle connector used invalid flags");
        LocksmithSavedData.forServer(helper.getLevel().getServer()).remove(fixture.record().id());
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void breakingRootPromotesDeterministicContainerSuccessor(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        BlockPos root = helper.absolutePos(ROOT_CHEST);
        BlockState state = helper.getLevel().getBlockState(root);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(root);
        helper.getLevel().removeBlock(root, false);
        LockBreakResult result = LocksmithAuthority.finalizeBrokenMember(
                helper.getLevel(), fixture.owner(), root, state, blockEntity).orElseThrow();
        LockRecord remaining = result.after().orElseThrow();
        require(helper, result.rootMoved(), "Root removal did not report successor movement");
        require(helper, remaining.rootContainer().blockPos().equals(helper.absolutePos(DESTINATION)),
                "Destination was not chosen as the deterministic successor");
        cleanup(new Fixture(fixture.owner(), remaining));
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void denyBlocksBoundaryAutomationButAllowsSameLockInternalTransfer(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var root = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(helper.absolutePos(ROOT_CHEST));
        var hopper = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(helper.absolutePos(HOPPER));
        helper.setBlock(new BlockPos(4, 2, 1), Blocks.CHEST);
        var unlocked = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(4, 2, 1)));
        require(helper, LocksmithAccessService.allowAutomationTransfer(root, hopper),
                "Internal same-lock transfer was denied in DENY mode");
        require(helper, !LocksmithAccessService.allowAutomationTransfer(root, unlocked),
                "Boundary extraction escaped a DENY network");
        cleanup(fixture);
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void ownerPlacementExtendsLockWithoutAnotherPadlock(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        helper.setBlock(ROOT_CHEST, Blocks.CHEST);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LocksmithItems.PADLOCK));
        require(helper, LocksmithAuthority.applyPadlock(owner, helper.getLevel(),
                helper.absolutePos(ROOT_CHEST), InteractionHand.MAIN_HAND), "Initial Padlock failed");

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HOPPER));
        var result = placeAgainst(owner, helper.absolutePos(ROOT_CHEST), Direction.DOWN);
        require(helper, result.consumesAction(), "Owner Hopper placement was rejected");
        require(helper, owner.getMainHandItem().isEmpty(), "Placed Hopper was not consumed once");
        LockRecord record = LocksmithSavedData.forServer(helper.getLevel().getServer())
                .findAt(LockLocation.of(helper.getLevel(), helper.absolutePos(HOPPER))).orElseThrow();
        require(helper, record.connectors().size() == 1, "Owner Hopper did not join the existing Lock UUID");
        require(helper, record.logicalContainerCount() == 1, "Hopper expansion changed container count");
        cleanup(new Fixture(owner, record));
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void nonOwnerPlacementCannotExtendLockedNetwork(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        helper.setBlock(ROOT_CHEST, Blocks.CHEST);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LocksmithItems.PADLOCK));
        require(helper, LocksmithAuthority.applyPadlock(owner, helper.getLevel(),
                helper.absolutePos(ROOT_CHEST), InteractionHand.MAIN_HAND), "Initial Padlock failed");
        LockRecord record = LocksmithSavedData.forServer(helper.getLevel().getServer())
                .findAt(LockLocation.of(helper.getLevel(), helper.absolutePos(ROOT_CHEST))).orElseThrow();

        ServerPlayer stranger = survivalPlayer(helper);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HOPPER));
        var result = placeAgainst(stranger, helper.absolutePos(ROOT_CHEST), Direction.DOWN);
        require(helper, !result.consumesAction(), "Non-owner Hopper placement unexpectedly succeeded");
        require(helper, helper.getLevel().getBlockState(helper.absolutePos(HOPPER)).isAir(),
                "Rejected placement still mutated the world");
        require(helper, stranger.getMainHandItem().getCount() == 1, "Rejected placement consumed the Hopper");
        cleanup(new Fixture(owner, record));
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void placementCannotBridgeTwoDifferentLocks(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        helper.setBlock(ROOT_CHEST, Blocks.CHEST);
        helper.setBlock(DESTINATION, Blocks.CHEST);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LocksmithItems.PADLOCK, 2));
        require(helper, LocksmithAuthority.applyPadlock(owner, helper.getLevel(),
                helper.absolutePos(ROOT_CHEST), InteractionHand.MAIN_HAND), "First Padlock failed");
        LockRecord first = LocksmithSavedData.forServer(helper.getLevel().getServer())
                .findAt(LockLocation.of(helper.getLevel(), helper.absolutePos(ROOT_CHEST))).orElseThrow();
        require(helper, LocksmithAuthority.applyPadlock(owner, helper.getLevel(),
                helper.absolutePos(DESTINATION), InteractionHand.MAIN_HAND), "Second Padlock failed");
        LockRecord second = LocksmithSavedData.forServer(helper.getLevel().getServer())
                .findAt(LockLocation.of(helper.getLevel(), helper.absolutePos(DESTINATION))).orElseThrow();

        BlockState hopper = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST);
        var guard = LocksmithAuthority.preparePlacement(owner, helper.getLevel(),
                helper.absolutePos(HOPPER), hopper, InteractionHand.MAIN_HAND, new ItemStack(Items.HOPPER));
        require(helper, guard.relevant() && !guard.allowed() && "different_locks".equals(guard.reason()),
                "Bridge placement did not reject two Lock UUIDs");
        cleanup(new Fixture(owner, first));
        cleanup(new Fixture(owner, second));
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void doubleChestIsOneLogicalContainerAndKeepsOneLock(GameTestHelper helper) {
        BlockState firstState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockPos partnerRelative = ROOT_CHEST.relative(ChestBlock.getConnectedDirection(firstState));
        BlockState partnerState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT);
        helper.setBlock(ROOT_CHEST, firstState);
        helper.setBlock(partnerRelative, partnerState);
        ServerPlayer owner = survivalPlayer(helper);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LocksmithItems.PADLOCK));
        require(helper, LocksmithAuthority.applyPadlock(owner, helper.getLevel(),
                helper.absolutePos(ROOT_CHEST), InteractionHand.MAIN_HAND), "Double-chest Padlock failed");
        LockRecord before = LocksmithSavedData.forServer(helper.getLevel().getServer())
                .findAt(LockLocation.of(helper.getLevel(), helper.absolutePos(ROOT_CHEST))).orElseThrow();
        require(helper, before.logicalContainerCount() == 1
                        && before.containers().getFirst().positions().size() == 2,
                "Double chest was not stored as one logical container");

        BlockPos root = before.rootContainer().blockPos();
        BlockPos other = before.containers().getFirst().positions().stream()
                .map(LockLocation::blockPos).filter(position -> !position.equals(root)).findFirst().orElseThrow();
        BlockState removedState = helper.getLevel().getBlockState(other);
        BlockEntity removedEntity = helper.getLevel().getBlockEntity(other);
        helper.getLevel().removeBlock(other, false);
        LockBreakResult result = LocksmithAuthority.finalizeBrokenMember(
                helper.getLevel(), owner, other, removedState, removedEntity).orElseThrow();
        LockRecord after = result.after().orElseThrow();
        require(helper, !result.lockRemoved() && !result.rootMoved(),
                "Removing the non-root chest half moved or removed the lock");
        require(helper, after.logicalContainerCount() == 1 && after.positionCount() == 1,
                "Surviving chest half did not retain exactly one lock marker");
        cleanup(new Fixture(owner, after));
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void defaultExplosionProtectionKeepsLockedContainer(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        helper.setBlock(ROOT_CHEST, Blocks.CHEST);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LocksmithItems.PADLOCK));
        require(helper, LocksmithAuthority.applyPadlock(owner, helper.getLevel(),
                helper.absolutePos(ROOT_CHEST), InteractionHand.MAIN_HAND), "Initial Padlock failed");
        LockRecord record = LocksmithSavedData.forServer(helper.getLevel().getServer())
                .findAt(LockLocation.of(helper.getLevel(), helper.absolutePos(ROOT_CHEST))).orElseThrow();
        BlockPos absolute = helper.absolutePos(ROOT_CHEST);
        helper.getLevel().explode(null, absolute.getX() + 0.5D, absolute.getY() + 0.5D,
                absolute.getZ() + 0.5D, 6.0F, Level.ExplosionInteraction.TNT);
        require(helper, helper.getLevel().getBlockState(absolute).is(Blocks.CHEST),
                "Default explosion protection allowed a locked chest to be destroyed");
        cleanup(new Fixture(owner, record));
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        helper.setBlock(ROOT_CHEST, Blocks.CHEST);
        helper.setBlock(HOPPER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(DESTINATION, Blocks.CHEST);
        ServerPlayer owner = survivalPlayer(helper);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LocksmithItems.PADLOCK));
        require(helper, LocksmithAuthority.applyPadlock(owner, helper.getLevel(),
                helper.absolutePos(ROOT_CHEST), InteractionHand.MAIN_HAND), "Applying Padlock failed");
        LockRecord record = LocksmithSavedData.forServer(helper.getLevel().getServer())
                .findAt(LockLocation.of(helper.getLevel(), helper.absolutePos(ROOT_CHEST))).orElseThrow();
        return new Fixture(owner, record);
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // GameTest mock players retain their creative inventory ability after the
        // game-mode change, unlike a normal connected survival player.
        player.getAbilities().instabuild = false;
        return player;
    }

    private static net.minecraft.world.InteractionResult placeAgainst(
            ServerPlayer player,
            BlockPos clicked,
            Direction face
    ) {
        Vec3 hitLocation = Vec3.atCenterOf(clicked).add(
                face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
        BlockHitResult hit = new BlockHitResult(hitLocation, face, clicked, false);
        BlockPlaceContext context = new BlockPlaceContext(
                player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
        return ((BlockItem) player.getMainHandItem().getItem()).place(context);
    }

    private static void cleanup(Fixture fixture) {
        LocksmithSavedData.forServer(fixture.owner().level().getServer()).remove(fixture.record().id());
        ServerLevel level = (ServerLevel) fixture.owner().level();
        fixture.record().allPositions().forEach(location -> {
            BlockEntity blockEntity = level.getBlockEntity(location.blockPos());
            if (blockEntity != null) LockMarkerAttachments.clear(blockEntity);
        });
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    private record Fixture(ServerPlayer owner, LockRecord record) {
    }
}
