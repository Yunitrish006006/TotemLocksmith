package dev.totem.locksmith.client;

import dev.totem.core.api.v1.client.observer.ObserverRemoteCursor;
import dev.totem.core.api.v1.client.observer.ObserverScreenContext;
import dev.totem.core.api.v1.client.observer.ObserverScreenHandle;
import dev.totem.core.api.v1.client.observer.ObserverScreenProvider;
import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.locksmith.menu.LocksmithManagementMenu;
import dev.totem.locksmith.menu.LocksmithManagementOpenData;
import dev.totem.locksmith.menu.LocksmithMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.Map;

/** Locksmith-owned production management-screen Observer factory. */
public final class LocksmithObserverScreenProvider implements ObserverScreenProvider {
    @Override public String familyId() { return "locksmith_management"; }
    @Override public int protocolVersion() { return 1; }
    @Override public Set<String> variants() { return Set.of(""); }

    @Override public Optional<ObserverScreenSnapshot> capture(Screen candidate, long sequence) {
        if (!(candidate instanceof LocksmithManagementScreen screen) || screen.totem$isObserverReadOnly()) return Optional.empty();
        LocksmithManagementOpenData state = screen.observerSnapshot();
        return Optional.of(new ObserverScreenSnapshot(familyId(), "", protocolVersion(), sequence,
                screen.getTitle(), List.of(), new int[0], Map.of("lock_id", state.lockId().toString()), encode(state)));
    }

    @Override public ObserverScreenHandle create(ObserverScreenContext context, ObserverScreenSnapshot snapshot) {
        if (!supports(snapshot)) throw new IllegalArgumentException("Incompatible Locksmith Observer snapshot");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) throw new IllegalStateException("Observer player is unavailable");
        LocksmithManagementOpenData view = observerView(decode(snapshot.ownerPayload()));
        Inventory detachedInventory = new Inventory(minecraft.player, new EntityEquipment());
        LocksmithManagementMenu menu = new LocksmithManagementMenu(LocksmithMenus.MANAGEMENT, -1,
                detachedInventory, view);
        LocksmithManagementScreen screen = new LocksmithManagementScreen(menu, detachedInventory,
                snapshot.title(), true, context.stopObserving());
        return new ObserverScreenHandle() {
            private long snapshotSequence = snapshot.sequence();
            private long cursorSequence = -1L;
            @Override public Screen screen() { return screen; }
            @Override public void applySnapshot(ObserverScreenSnapshot update) {
                if (!supports(update) || update.sequence() <= snapshotSequence) return;
                LocksmithManagementOpenData next = observerView(decode(update.ownerPayload()));
                if (screen.applyObserverSnapshot(next)) snapshotSequence = update.sequence();
            }
            @Override public void applyCursor(ObserverRemoteCursor cursor) {
                if (cursor.sequence() <= cursorSequence) return;
                cursorSequence = cursor.sequence();
                menu.setCarried(cursor.carriedStack());
            }
        };
    }

    private static LocksmithManagementOpenData observerView(LocksmithManagementOpenData source) {
        return new LocksmithManagementOpenData(source.lockId(), source.revision(),
                source.ownerName(), false, false, source.physicalKeysRequired(), source.accessModeOrdinal(),
                source.automationModeOrdinal(), source.logicalContainerCount(), source.connectorCount(),
                source.members(), source.keys(), source.candidates());
    }

    private static byte[] encode(LocksmithManagementOpenData state) {
        RegistryFriendlyByteBuf buffer = buffer(Unpooled.buffer());
        try {
            LocksmithManagementOpenData.STREAM_CODEC.encode(buffer, state);
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.getBytes(buffer.readerIndex(), bytes); return bytes;
        } finally { buffer.release(); }
    }
    private static LocksmithManagementOpenData decode(byte[] bytes) {
        RegistryFriendlyByteBuf buffer = buffer(Unpooled.wrappedBuffer(bytes));
        try {
            LocksmithManagementOpenData state = LocksmithManagementOpenData.STREAM_CODEC.decode(buffer);
            if (buffer.readableBytes() != 0) throw new IllegalArgumentException("Trailing Locksmith Observer bytes");
            return state;
        } finally { buffer.release(); }
    }

    private static RegistryFriendlyByteBuf buffer(io.netty.buffer.ByteBuf bytes) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) throw new IllegalStateException("Client registry access is unavailable");
        return new RegistryFriendlyByteBuf(bytes, minecraft.level.registryAccess());
    }
}
