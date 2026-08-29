package dev.totem.locksmith.client;

import dev.totem.core.api.v1.client.observer.ObserverRemoteCursor;
import dev.totem.core.api.v1.client.observer.ObserverScreenContext;
import dev.totem.core.api.v1.client.observer.ObserverScreenHandle;
import dev.totem.core.api.v1.client.observer.ObserverScreenProvider;
import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.locksmith.menu.LocksmithManagementMenu;
import dev.totem.locksmith.menu.LocksmithManagementOpenData;
import dev.totem.locksmith.menu.LocksmithMenus;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Owner-local runtime proof for the production Locksmith management Observer screen. */
@SuppressWarnings("UnstableApiUsage")
public final class LocksmithObserverProviderClientGameTest implements FabricClientGameTest {
    private static final UUID LOCK = UUID.fromString("00000000-0000-0000-0000-000000000043");

    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            context.getInput().resizeWindow(1280, 720);
            LocksmithObserverScreenProvider provider = context.computeOnClient(client -> {
                boolean registered = FabricLoader.getInstance()
                        .getEntrypoints(ObserverScreenProvider.ENTRYPOINT, ObserverScreenProvider.class).stream()
                        .anyMatch(LocksmithObserverScreenProvider.class::isInstance);
                if (!registered) throw new AssertionError("Locksmith Observer provider entrypoint is missing");
                return new LocksmithObserverScreenProvider();
            });
            ObserverScreenSnapshot initial = capture(context, provider, source(context, 7, "Owner"), 1);
            ObserverScreenSnapshot update = capture(context, provider, source(context, 8, "Remote Owner"), 2);
            AtomicInteger stops = new AtomicInteger();
            ObserverScreenHandle handle = context.computeOnClient(client -> provider.create(
                    new ObserverScreenContext(UUID.randomUUID(), "Target", stops::incrementAndGet), initial));
            context.runOnClient(client -> client.setScreenAndShow(handle.screen()));
            context.waitForScreen(LocksmithManagementScreen.class);
            context.runOnClient(client -> {
                LocksmithManagementScreen screen = (LocksmithManagementScreen) handle.screen();
                require(screen.totem$isObserverReadOnly(), "Locksmith screen did not enter Observer mode");
                require(screen.observerSnapshot().revision() == 7,
                        "Initial Locksmith snapshot was not applied");
                handle.applySnapshot(foreign(update, "locksmith_management", "wrong", 1, 90));
                handle.applySnapshot(foreign(update, "locksmith_management", "", 2, 91));
                handle.applySnapshot(foreign(update, "foreign", "", 1, 92));
                handle.applySnapshot(update);
                handle.applySnapshot(initial);
                require(screen.observerSnapshot().revision() == 8
                                && "Remote Owner".equals(screen.observerSnapshot().ownerName()),
                        "Exact monotonic Locksmith snapshot policy failed");
                ItemStack carried = new ItemStack(Items.DIAMOND, 2);
                handle.applyCursor(new ObserverRemoteCursor(2, 143, 112, 286, 224, carried));
                handle.applyCursor(new ObserverRemoteCursor(1, 0, 0, 286, 224, ItemStack.EMPTY));
                require(ItemStack.matches(carried, screen.getMenu().getCarried()),
                        "Stale Locksmith cursor replaced the carried stack");
                ObserverPacketProbe.reset();
                require(screen.mouseClicked(new MouseButtonEvent(1, 1,
                                new MouseButtonInfo(0, 0)), false), "Observer mouse input was not consumed");
                require(screen.keyPressed(new KeyEvent(65, 0, 0)),
                        "Observer keyboard input was not consumed");
                require(ObserverPacketProbe.sends() == 0, "Locksmith Observer input attempted a packet");
            });
            context.waitTicks(2);
            context.takeScreenshot("locksmith-observer-owner-production-screen");
            context.runOnClient(client -> {
                ObserverPacketProbe.reset();
                require(handle.screen().keyPressed(new KeyEvent(256, 0, 0)), "Escape was not consumed");
                require(stops.get() == 1, "Escape did not request stop-observing exactly once");
                require(ObserverPacketProbe.sends() == 0, "Closing Observer mode attempted a packet");
                client.setScreenAndShow(null);
            });
            context.waitForScreen(null);
        }
    }

    private static LocksmithManagementScreen source(ClientGameTestContext context, long revision, String owner) {
        return context.computeOnClient(client -> {
            LocksmithManagementOpenData data = new LocksmithManagementOpenData(LOCK, revision, owner,
                    true, true, true, 1, 1, 2, 3, List.of(), List.of(), List.of());
            LocksmithManagementMenu menu = new LocksmithManagementMenu(LocksmithMenus.MANAGEMENT,
                    (int) revision, client.player.getInventory(), data);
            return new LocksmithManagementScreen(menu, client.player.getInventory(),
                    Component.literal("Locksmith"));
        });
    }

    private static ObserverScreenSnapshot capture(ClientGameTestContext context,
                                                  ObserverScreenProvider provider,
                                                  LocksmithManagementScreen screen, long sequence) {
        return context.computeOnClient(client -> provider.capture(screen, sequence).orElseThrow());
    }

    private static ObserverScreenSnapshot foreign(ObserverScreenSnapshot source, String family,
                                                   String variant, int protocol, long sequence) {
        return new ObserverScreenSnapshot(family, variant, protocol, sequence, source.title(), source.slots(),
                source.data(), source.metadata(), source.ownerPayload());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
