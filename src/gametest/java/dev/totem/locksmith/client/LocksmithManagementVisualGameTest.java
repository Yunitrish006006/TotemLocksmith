package dev.totem.locksmith.client;

import dev.totem.locksmith.domain.AccessMode;
import dev.totem.locksmith.domain.AutomationMode;
import dev.totem.locksmith.domain.MemberRole;
import dev.totem.locksmith.menu.LocksmithManagementMenu;
import dev.totem.locksmith.menu.LocksmithManagementOpenData;
import dev.totem.locksmith.menu.LocksmithMenus;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Captures the real 26.2 Locksmith management screen in Traditional Chinese. */
@SuppressWarnings({"UnstableApiUsage", "unchecked", "rawtypes"})
public final class LocksmithManagementVisualGameTest implements FabricClientGameTest {
    private static final UUID LOCK_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        selectTraditionalChinese(context);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            LocksmithManagementScreen screen = context.computeOnClient(client -> {
                if (client.player == null) {
                    throw new IllegalStateException("Client GameTest did not provide a player");
                }
                LocksmithManagementMenu menu = new LocksmithManagementMenu(
                        LocksmithMenus.MANAGEMENT,
                        0,
                        client.player.getInventory(),
                        snapshot()
                );
                LocksmithManagementScreen created = new LocksmithManagementScreen(
                        menu,
                        client.player.getInventory(),
                        Component.translatable("gui.totem.locksmith.management.title")
                );
                client.setScreenAndShow(created);
                return created;
            });
            context.waitForScreen(LocksmithManagementScreen.class);
            context.waitTicks(3);
            context.takeScreenshot("locksmith-management-zh-tw-access");

            context.runOnClient(client -> selectTab(screen, "MEMBERS"));
            context.waitTicks(2);
            context.takeScreenshot("locksmith-management-zh-tw-members");

            context.runOnClient(client -> selectTab(screen, "KEYS"));
            context.waitTicks(2);
            context.takeScreenshot("locksmith-management-zh-tw-keys");

            context.runOnClient(client -> client.setScreenAndShow(null));
        }
    }

    private static void selectTab(LocksmithManagementScreen screen, String name) {
        try {
            Field field = LocksmithManagementScreen.class.getDeclaredField("tab");
            field.setAccessible(true);
            Class<? extends Enum> type = (Class<? extends Enum>) field.getType();
            field.set(screen, Enum.valueOf(type, name));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not select Locksmith visual-test tab " + name, exception);
        }
    }

    private static void selectTraditionalChinese(ClientGameTestContext context) {
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(client -> {
            client.options.languageCode = "zh_tw";
            client.getLanguageManager().setSelected("zh_tw");
            reload.set(client.reloadResourcePacks());
        });
        context.waitFor(client -> reload.get() != null && reload.get().isDone());
        String translated = context.computeOnClient(client ->
                Component.translatable("gui.totem.locksmith.management.title").getString());
        if (!"鎖具管理".equals(translated)) {
            throw new IllegalStateException("Traditional Chinese Locksmith GUI resources were not loaded: " + translated);
        }
    }

    private static LocksmithManagementOpenData snapshot() {
        return new LocksmithManagementOpenData(
                LOCK_ID,
                7L,
                "Yunitrish006006",
                true,
                false,
                true,
                AccessMode.PRIVATE.ordinal(),
                AutomationMode.TRUSTED.ordinal(),
                3,
                2,
                List.of(
                        new LocksmithManagementOpenData.MemberView(
                                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                                "Alex",
                                MemberRole.MANAGER.ordinal()),
                        new LocksmithManagementOpenData.MemberView(
                                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                                "Steve",
                                MemberRole.USER.ordinal()),
                        new LocksmithManagementOpenData.MemberView(
                                UUID.fromString("20000000-0000-0000-0000-000000000003"),
                                "BlockedPlayer",
                                MemberRole.BLOCKED.ordinal())
                ),
                List.of(
                        new LocksmithManagementOpenData.KeyView(
                                UUID.fromString("30000000-0000-0000-0000-000000000001"), "倉庫鑰匙"),
                        new LocksmithManagementOpenData.KeyView(
                                UUID.fromString("30000000-0000-0000-0000-000000000002"), "採礦隊鑰匙")
                ),
                List.of(
                        new LocksmithManagementOpenData.PlayerView(
                                UUID.fromString("40000000-0000-0000-0000-000000000001"), "Builder"),
                        new LocksmithManagementOpenData.PlayerView(
                                UUID.fromString("40000000-0000-0000-0000-000000000002"), "Miner")
                )
        );
    }
}
