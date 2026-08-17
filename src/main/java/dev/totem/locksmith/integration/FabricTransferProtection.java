package dev.totem.locksmith.integration;

import dev.totem.locksmith.domain.AccessOperation;
import dev.totem.locksmith.service.LocksmithAccessService;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** Guards direct Fabric Transfer API access that does not pass through vanilla Hopper methods. */
public final class FabricTransferProtection {
    private FabricTransferProtection() {
    }

    public static void register() {
        ItemStorage.SIDED.registerForBlockEntities(
                FabricTransferProtection::storage,
                BlockEntityTypes.CHEST,
                BlockEntityTypes.TRAPPED_CHEST,
                BlockEntityTypes.BARREL,
                BlockEntityTypes.HOPPER
        );
    }

    private static Storage<ItemVariant> storage(BlockEntity blockEntity, Direction direction) {
        if (!(blockEntity instanceof Container direct) || blockEntity.getLevel() == null) return null;
        Container container = direct;
        if (blockEntity instanceof ChestBlockEntity
                && blockEntity.getBlockState().getBlock() instanceof ChestBlock chestBlock) {
            Container combined = ChestBlock.getContainer(chestBlock, blockEntity.getBlockState(),
                    blockEntity.getLevel(), blockEntity.getBlockPos(), true);
            if (combined != null) container = combined;
        }
        Storage<ItemVariant> delegate = ContainerStorage.of(container, direction);
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) return delegate;
        return new GuardedStorage(delegate, level, blockEntity.getBlockPos());
    }

    private record GuardedStorage(
            Storage<ItemVariant> delegate,
            ServerLevel level,
            BlockPos position
    ) implements Storage<ItemVariant> {
        @Override
        public boolean supportsInsertion() {
            return allowed(AccessOperation.INSERT) && delegate.supportsInsertion();
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return allowed(AccessOperation.INSERT) ? delegate.insert(resource, maxAmount, transaction) : 0L;
        }

        @Override
        public boolean supportsExtraction() {
            return allowed(AccessOperation.EXTRACT) && delegate.supportsExtraction();
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return allowed(AccessOperation.EXTRACT) ? delegate.extract(resource, maxAmount, transaction) : 0L;
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {
            if (!allowed(AccessOperation.INSERT) && !allowed(AccessOperation.EXTRACT)) {
                return java.util.Collections.emptyIterator();
            }
            Iterator<StorageView<ItemVariant>> source = delegate.iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return source.hasNext(); }
                @Override public StorageView<ItemVariant> next() {
                    if (!source.hasNext()) throw new NoSuchElementException();
                    return new GuardedView(source.next(), GuardedStorage.this);
                }
            };
        }

        @Override
        public long getVersion() {
            return delegate.getVersion();
        }

        private boolean allowed(AccessOperation operation) {
            return LocksmithAccessService.allowAutomationAt(level, position, operation, null);
        }
    }

    private record GuardedView(
            StorageView<ItemVariant> delegate,
            GuardedStorage owner
    ) implements StorageView<ItemVariant> {
        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return owner.allowed(AccessOperation.EXTRACT)
                    ? delegate.extract(resource, maxAmount, transaction) : 0L;
        }

        @Override public boolean isResourceBlank() { return delegate.isResourceBlank(); }
        @Override public ItemVariant getResource() { return delegate.getResource(); }
        @Override public long getAmount() { return delegate.getAmount(); }
        @Override public long getCapacity() { return delegate.getCapacity(); }
        @Override public StorageView<ItemVariant> getUnderlyingView() { return delegate.getUnderlyingView(); }
    }
}
