package dev.totem.locksmith.gametest;

import dev.totem.core.api.v1.social.FriendActionResult;
import dev.totem.core.api.v1.social.TotemFriendshipApi;
import dev.totem.locksmith.domain.AccessActor;
import dev.totem.locksmith.domain.AccessMode;
import dev.totem.locksmith.domain.AutomationMode;
import dev.totem.locksmith.domain.ContainerKind;
import dev.totem.locksmith.domain.LockLocation;
import dev.totem.locksmith.domain.LockRecord;
import dev.totem.locksmith.domain.LockState;
import dev.totem.locksmith.domain.LogicalContainerNode;
import dev.totem.locksmith.service.LocksmithAccessService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/** Ensures Locksmith FRIENDS mode consumes Core social state without Nexus installed. */
public final class LocksmithCoreFriendshipGameTest {
    @GameTest(maxTicks = 40)
    public void coreMutualFriendshipFeedsServerResolvedActor(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer friend = helper.makeMockServerPlayerInLevel();
        try {
            var server = helper.getLevel().getServer();
            require(helper,
                    TotemFriendshipApi.inviteOrAccept(server, owner.getUUID(), friend.getUUID())
                            == FriendActionResult.INVITED,
                    "Core did not create the first friendship invitation");
            require(helper,
                    TotemFriendshipApi.inviteOrAccept(server, friend.getUUID(), owner.getUUID())
                            == FriendActionResult.ACCEPTED,
                    "Core did not accept the reciprocal friendship invitation");

            LockLocation root = new LockLocation("minecraft:overworld", 1, 64, 2);
            LockRecord record = new LockRecord(
                    UUID.randomUUID(),
                    owner.getUUID(),
                    owner.getName().getString(),
                    root,
                    List.of(new LogicalContainerNode(ContainerKind.CHEST, List.of(root))),
                    List.of(),
                    AccessMode.FRIENDS,
                    AutomationMode.DENY,
                    List.of(),
                    List.of(),
                    0,
                    1L,
                    LockState.ACTIVE,
                    1
            );

            AccessActor actor = LocksmithAccessService.playerActor(friend, record);
            require(helper, actor.mutualFriend(),
                    "Locksmith did not resolve the Core friendship into AccessActor.mutualFriend");
            require(helper, TotemFriendshipApi.removeRelationship(server, owner.getUUID(), friend.getUUID()),
                    "Could not clean up Core friendship test relationship");
        } finally {
            owner.discard();
            friend.discard();
        }
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
