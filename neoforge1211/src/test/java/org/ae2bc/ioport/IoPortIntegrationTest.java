package org.ae2bc.ioport;

import appeng.api.config.Actionable;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.api.storage.cells.CellState;
import appeng.blockentity.storage.IOPortBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.GridNode;
import appeng.me.ManagedGridNode;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.IOPortMenu;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.SettingsFrom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.ae2bc.core.ioport.CellTransferMode;
import org.ae2bc.registry.BlockRegistry;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.mockito.MockMakers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class IoPortIntegrationTest {
    @Test
    void powerPrecheckDoesNotExtractFromAReadOnlyNetwork() throws ReflectiveOperationException {
        FillFixture fixture = new FillFixture();
        fixture.powered.set(false);
        Assertions.assertEquals(TickRateModulation.IDLE, fixture.port.tickingRequest(fixture.node, 1));
        Assertions.assertEquals(64, fixture.stock.get());
        Assertions.assertNull(IoPortIntegrationTest.pending(fixture.port));
        Mockito.verify(fixture.network, Mockito.never()).extract(
                Mockito.any(), Mockito.anyLong(), Mockito.eq(Actionable.MODULATE), Mockito.any());
    }

    @Test
    void rejectedRefundSurvivesReloadAndCompletesOnlyOnce() throws ReflectiveOperationException {
        FillFixture fixture = new FillFixture();
        fixture.losePowerOnExtraction.set(true);
        fixture.port.tickingRequest(fixture.node, 1);
        Assertions.assertEquals(0, fixture.stock.get());
        Assertions.assertEquals(64, Objects.requireNonNull(IoPortIntegrationTest.pending(fixture.port)).amount());
        fixture.port.tickingRequest(fixture.node, 1);
        Mockito.verify(fixture.network, Mockito.times(1)).extract(
                Mockito.any(), Mockito.anyLong(), Mockito.eq(Actionable.MODULATE), Mockito.any());
        Assertions.assertFalse(fixture.port.moveCell(0));

        CompoundTag saved = IoPortIntegrationTest.save(fixture.port);
        Ae2bcIoPortBlockEntity restored = IoPortIntegrationTest.port();
        restored.loadTag(saved, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        GridNode restoredNode = IoPortIntegrationTest.connect(restored, fixture.grid);
        fixture.powered.set(true);
        restored.tickingRequest(restoredNode, 1);
        Assertions.assertNull(IoPortIntegrationTest.pending(restored));
        Assertions.assertEquals(64, IoPortIntegrationTest.cell(
                restored.getInternalInventory().getStackInSlot(0)).getAvailableStacks().get(fixture.key));
        restored.tickingRequest(restoredNode, 1);
        Assertions.assertEquals(64, IoPortIntegrationTest.cell(
                restored.getInternalInventory().getStackInSlot(0)).getAvailableStacks().get(fixture.key));
        Assertions.assertEquals(0, fixture.stock.get());
    }

    @Test
    void partiallyAcceptedCellExtractionKeepsItsRemainderAcrossReload() throws ReflectiveOperationException {
        Ae2bcIoPortBlockEntity original = IoPortIntegrationTest.port();
        original.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        AEKey key = AEItemKey.of(Items.STONE);
        AtomicLong stock = new AtomicLong(64);
        StorageCell cell = Mockito.mock(StorageCell.class);
        Mockito.when(cell.getStatus()).thenReturn(CellState.EMPTY);
        Mockito.when(cell.getAvailableStacks()).thenAnswer(call -> {
            KeyCounter available = new KeyCounter();
            available.add(key, stock.get());
            return available;
        });
        Mockito.when(cell.extract(Mockito.eq(key), Mockito.anyLong(), Mockito.eq(Actionable.MODULATE), Mockito.any()))
                .thenAnswer(call -> {
                    long amount = Math.min(stock.get(), call.getArgument(1, Long.class));
                    stock.addAndGet(-amount);
                    return amount;
                });
        AtomicLong received = new AtomicLong();
        AtomicBoolean acceptingRemainder = new AtomicBoolean();
        MEStorage network = Mockito.mock(MEStorage.class);
        Mockito.when(network.insert(Mockito.eq(key), Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> {
                    long requested = call.getArgument(1);
                    if (call.getArgument(2) == Actionable.SIMULATE) {
                        return requested;
                    }
                    long accepted = acceptingRemainder.get() ? requested : Math.min(requested, 19 - received.get());
                    received.addAndGet(accepted);
                    return accepted;
                });
        IGrid grid = IoPortIntegrationTest.poweredGrid(network);
        GridNode node = IoPortIntegrationTest.connect(original, grid);
        try (MockedStatic<StorageCells> storage = Mockito.mockStatic(
                StorageCells.class, Mockito.withSettings().mockMaker(MockMakers.INLINE))) {
            storage.when(() -> StorageCells.getCellInventory(Mockito.any(ItemStack.class), Mockito.isNull()))
                    .thenReturn(cell);
            original.tickingRequest(node, 1);
            Assertions.assertEquals(0, stock.get());
            Assertions.assertEquals(19, received.get());
            Assertions.assertEquals(45, Objects.requireNonNull(IoPortIntegrationTest.pending(original)).amount());
            CompoundTag saved = IoPortIntegrationTest.save(original);
            Assertions.assertEquals(-1, saved.getInt("pendingCell"));
            Ae2bcIoPortBlockEntity restored = IoPortIntegrationTest.port();
            restored.loadTag(saved, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            GridNode restoredNode = IoPortIntegrationTest.connect(restored, grid);
            restored.tickingRequest(restoredNode, 1);
            Assertions.assertEquals(19, received.get());
            acceptingRemainder.set(true);
            restored.tickingRequest(restoredNode, 1);
            restored.tickingRequest(restoredNode, 1);
            Assertions.assertEquals(64, received.get());
            Assertions.assertNull(IoPortIntegrationTest.pending(restored));
            Mockito.verify(cell, Mockito.times(1)).extract(
                    Mockito.eq(key), Mockito.anyLong(), Mockito.eq(Actionable.MODULATE), Mockito.any());
        }
    }

    @Test
    void recoveryRemainsRunnableWithoutAnInputCellAndReturnsToNetwork() throws ReflectiveOperationException {
        FillFixture fixture = new FillFixture();
        fixture.losePowerOnExtraction.set(true);
        fixture.port.tickingRequest(fixture.node, 1);
        fixture.port.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
        fixture.refundsAccepted.set(true);
        Assertions.assertEquals(TickRateModulation.SLEEP, fixture.port.tickingRequest(fixture.node, 1));
        Assertions.assertNull(IoPortIntegrationTest.pending(fixture.port));
        Assertions.assertEquals(64, fixture.stock.get());
        fixture.port.tickingRequest(fixture.node, 1);
        Assertions.assertEquals(64, fixture.stock.get());
    }

    @Test
    void failureAfterExtractionRetainsTheHeldResources() throws ReflectiveOperationException {
        FillFixture fixture = new FillFixture();
        Mockito.when(fixture.grid.getEnergyService().extractAEPower(Mockito.anyDouble(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> {
                    if (fixture.stock.get() == 0) {
                        throw new IllegalStateException("Target unavailable after extraction");
                    }
                    return call.getArgument(0, Double.class);
                });
        Assertions.assertThrows(IllegalStateException.class, () -> fixture.port.tickingRequest(fixture.node, 1));
        Assertions.assertEquals(0, fixture.stock.get());
        Assertions.assertEquals(64, Objects.requireNonNull(IoPortIntegrationTest.pending(fixture.port)).amount());
    }

    @Test
    void pendingResourceIsDroppedOnRemovalAndClearedWithTheBlock() throws ReflectiveOperationException {
        FillFixture fixture = new FillFixture();
        fixture.losePowerOnExtraction.set(true);
        fixture.port.tickingRequest(fixture.node, 1);
        List<ItemStack> drops = new ArrayList<>();
        Level level = Mockito.mock(Level.class);
        fixture.port.addAdditionalDrops(level, BlockPos.ZERO, drops);
        Assertions.assertEquals(64, drops.stream().filter(stack -> stack.is(Items.STONE))
                .mapToInt(ItemStack::getCount).sum());
        fixture.port.clearContent();
        drops.clear();
        fixture.port.addAdditionalDrops(level, BlockPos.ZERO, drops);
        Assertions.assertNull(IoPortIntegrationTest.pending(fixture.port));
        Assertions.assertEquals(0, drops.stream().filter(stack -> stack.is(Items.STONE))
                .mapToInt(ItemStack::getCount).sum());
    }

    @Test
    void pendingFluidTravelsWithTheDismantledBlockAndRestoresThroughVanillaPlacement() throws ReflectiveOperationException {
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        long amount = 59L * water.getAmountPerOperation();
        CompoundTag tag = new CompoundTag();
        tag.put("pendingTransfer", GenericStack.writeTag(registries, new GenericStack(water, amount)));
        tag.putInt("pendingCell", -1);
        Level level = Mockito.mock(Level.class);
        Mockito.when(level.registryAccess()).thenReturn(registries);
        Mockito.when(level.getServer()).thenReturn(Mockito.mock(MinecraftServer.class));
        Ae2bcIoPortBlockEntity original = IoPortIntegrationTest.port();
        original.setLevel(level);
        original.loadTag(tag, registries);
        ItemStack dropped = new ItemStack(ItemRegistry.IO_PORT.get());
        dropped.applyComponents(original.exportSettings(SettingsFrom.DISMANTLE_ITEM, null));
        Assertions.assertTrue(dropped.has(DataComponents.BLOCK_ENTITY_DATA));
        DataComponentMap card = original.exportSettings(SettingsFrom.MEMORY_CARD, null);
        Assertions.assertFalse(card.has(DataComponents.BLOCK_ENTITY_DATA));
        original.clearContent();
        Assertions.assertNull(IoPortIntegrationTest.pending(original));

        Ae2bcIoPortBlockEntity restored = IoPortIntegrationTest.port();
        restored.setLevel(level);
        Mockito.when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(restored);
        Assertions.assertTrue(BlockItem.updateCustomBlockEntityTag(level, null, BlockPos.ZERO, dropped));
        Assertions.assertEquals(new GenericStack(water, amount), IoPortIntegrationTest.pending(restored));
        MEStorage network = Mockito.mock(MEStorage.class);
        AtomicLong received = new AtomicLong();
        Mockito.when(network.insert(Mockito.eq(water), Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> {
                    long requested = call.getArgument(1);
                    if (call.getArgument(2) == Actionable.MODULATE) {
                        received.addAndGet(requested);
                    }
                    return requested;
                });
        GridNode node = IoPortIntegrationTest.connect(restored, IoPortIntegrationTest.poweredGrid(network));
        Assertions.assertEquals(TickRateModulation.SLEEP, restored.tickingRequest(node, 1));
        Assertions.assertEquals(amount, received.get());
        Assertions.assertNull(IoPortIntegrationTest.pending(restored));
        Assertions.assertFalse(restored.exportSettings(SettingsFrom.DISMANTLE_ITEM, null)
                .has(DataComponents.BLOCK_ENTITY_DATA));
    }

    @Test
    void blockedCandidatesShareOneVisitBudgetAcrossCellsAndRotate() throws ReflectiveOperationException {
        Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        port.getConfigManager().putSetting(Settings.OPERATION_MODE, OperationMode.FILL);
        port.getConfigManager().putSetting(Settings.FULLNESS_MODE, FullnessMode.HALF);
        KeyCounter available = IoPortIntegrationTest.scanInventory();
        IGrid grid = IoPortIntegrationTest.poweredGrid(Mockito.mock(MEStorage.class));
        Mockito.when(grid.getStorageService().getCachedInventory()).thenReturn(available);
        GridNode node = IoPortIntegrationTest.connect(port, grid);
        StorageCell[] cells = new StorageCell[Ae2bcIoPortBlockEntity.CELL_SLOTS];
        int[] attempts = new int[cells.length];
        for (int slot = 0; slot < cells.length; slot++) {
            int index = slot;
            cells[slot] = Mockito.mock(StorageCell.class);
            Mockito.when(cells[slot].getStatus()).thenReturn(CellState.EMPTY);
            Mockito.when(cells[slot].insert(Mockito.any(), Mockito.anyLong(),
                    Mockito.eq(Actionable.SIMULATE), Mockito.any())).thenAnswer(call -> {
                attempts[index]++;
                return 0L;
            });
            ItemStack stack = AEItems.ITEM_CELL_1K.stack();
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(Integer.toString(slot)));
            port.getInternalInventory().setItemDirect(slot, stack);
        }
        try (MockedStatic<StorageCells> storage = Mockito.mockStatic(
                StorageCells.class, Mockito.withSettings().mockMaker(MockMakers.INLINE))) {
            storage.when(() -> StorageCells.getCellInventory(Mockito.any(ItemStack.class), Mockito.isNull()))
                    .thenAnswer(call -> {
                        ItemStack stack = call.getArgument(0);
                        return cells[Integer.parseInt(Objects.requireNonNull(stack.get(DataComponents.CUSTOM_NAME)).getString())];
                    });
            for (int tick = 0; tick < cells.length; tick++) {
                port.tickingRequest(node, 1);
                Assertions.assertEquals(256 * (tick + 1), Arrays.stream(attempts).sum());
            }
            Assertions.assertTrue(Arrays.stream(attempts).allMatch(count -> count == 256));
            for (int slot = 0; slot < cells.length; slot++) {
                Assertions.assertFalse(port.getInternalInventory().getStackInSlot(slot).isEmpty());
            }
            for (int tick = 0; tick < cells.length * 2; tick++) {
                port.tickingRequest(node, 1);
            }
            for (int slot = 0; slot < cells.length; slot++) {
                Assertions.assertTrue(port.getInternalInventory().getStackInSlot(slot).isEmpty());
            }
        }
    }

    @Test
    void candidateCursorReachesTheEndAndRefreshesNewKeys() throws ReflectiveOperationException {
        Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        port.getConfigManager().putSetting(Settings.OPERATION_MODE, OperationMode.FILL);
        port.getConfigManager().putSetting(Settings.FULLNESS_MODE, FullnessMode.FULL);
        KeyCounter available = IoPortIntegrationTest.scanInventory();
        AEKey last = null;
        for (AEKey key : available.keySet()) {
            last = key;
        }
        AEKey acceptedKey = Objects.requireNonNull(last);
        MEStorage network = Mockito.mock(MEStorage.class);
        Mockito.when(network.extract(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> {
                    AEKey key = call.getArgument(0);
                    long amount = available.get(key);
                    available.remove(key);
                    return amount;
                });
        IGrid grid = IoPortIntegrationTest.poweredGrid(network);
        Mockito.when(grid.getStorageService().getCachedInventory()).thenReturn(available);
        GridNode node = IoPortIntegrationTest.connect(port, grid);
        port.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        StorageCell cell = Mockito.mock(StorageCell.class);
        Mockito.when(cell.getStatus()).thenReturn(CellState.EMPTY);
        AtomicLong received = new AtomicLong();
        Mockito.when(cell.insert(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> {
                    AEKey key = call.getArgument(0);
                    long amount = key.equals(acceptedKey) || key.equals(AEItemKey.of(Items.DIAMOND))
                            ? call.getArgument(1, Long.class) : 0;
                    if (call.getArgument(2) == Actionable.MODULATE) {
                        received.addAndGet(amount);
                    }
                    return amount;
                });
        try (MockedStatic<StorageCells> storage = Mockito.mockStatic(
                StorageCells.class, Mockito.withSettings().mockMaker(MockMakers.INLINE))) {
            storage.when(() -> StorageCells.getCellInventory(Mockito.any(ItemStack.class), Mockito.isNull()))
                    .thenReturn(cell);
            for (int tick = 0; tick < 3; tick++) {
                port.tickingRequest(node, 1);
            }
            Assertions.assertEquals(1, received.get());
            available.add(AEItemKey.of(Items.DIAMOND), 1);
            for (int tick = 0; tick < 3; tick++) {
                port.tickingRequest(node, 1);
            }
            Assertions.assertEquals(2, received.get());
        }
    }

    private static KeyCounter scanInventory() {
        KeyCounter result = new KeyCounter();
        for (int index = 0; index < 600; index++) {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("scan " + index));
            result.add(AEItemKey.of(stack), 1);
        }
        return result;
    }

    private static CompoundTag save(Ae2bcIoPortBlockEntity port) {
        CompoundTag tag = new CompoundTag();
        port.saveAdditional(tag, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        return tag;
    }

    private static GenericStack pending(Ae2bcIoPortBlockEntity port) {
        return GenericStack.readTag(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                IoPortIntegrationTest.save(port).getCompound("pendingTransfer"));
    }

    private static final class FillFixture {
        private final Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        private final AEKey key = AEItemKey.of(Items.STONE);
        private final AtomicLong stock = new AtomicLong(64);
        private final AtomicBoolean powered = new AtomicBoolean(true);
        private final AtomicBoolean losePowerOnExtraction = new AtomicBoolean();
        private final AtomicBoolean refundsAccepted = new AtomicBoolean();
        private final MEStorage network = Mockito.mock(MEStorage.class);
        private final IGrid grid = IoPortIntegrationTest.poweredGrid(this.network);
        private final GridNode node;

        private FillFixture() throws ReflectiveOperationException {
            this.port.getConfigManager().putSetting(Settings.OPERATION_MODE, OperationMode.FILL);
            this.port.getConfigManager().putSetting(Settings.FULLNESS_MODE, FullnessMode.FULL);
            this.port.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            this.node = IoPortIntegrationTest.connect(this.port, this.grid);
            Mockito.when(this.grid.getStorageService().getCachedInventory()).thenAnswer(call -> {
                KeyCounter available = new KeyCounter();
                available.add(this.key, this.stock.get());
                return available;
            });
            Mockito.when(this.grid.getEnergyService().extractAEPower(Mockito.anyDouble(), Mockito.any(), Mockito.any()))
                    .thenAnswer(call -> this.powered.get() ? call.getArgument(0, Double.class) : 0.0);
            Mockito.when(this.network.extract(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any()))
                    .thenAnswer(call -> {
                        long amount = Math.min(this.stock.get(), call.getArgument(1, Long.class));
                        if (call.getArgument(2) == Actionable.MODULATE) {
                            this.stock.addAndGet(-amount);
                            if (this.losePowerOnExtraction.get()) {
                                this.powered.set(false);
                            }
                        }
                        return amount;
                    });
            Mockito.when(this.network.insert(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any()))
                    .thenAnswer(call -> {
                        long amount = this.refundsAccepted.get() ? call.getArgument(1, Long.class) : 0;
                        if (call.getArgument(2) == Actionable.MODULATE) {
                            this.stock.addAndGet(amount);
                        }
                        return amount;
                    });
        }
    }

    private static Ae2bcIoPortBlockEntity port() {
        return new Ae2bcIoPortBlockEntity(BlockPos.ZERO, BlockRegistry.IO_PORT.get().defaultBlockState());
    }

    private static Player player(Level level) {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getCommandSenderWorld()).thenReturn(level);
        Mockito.when(player.level()).thenReturn(level);
        Mockito.when(player.getAbilities()).thenReturn(new Abilities());
        Mockito.when(player.registryAccess()).thenReturn(
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Inventory inventory = new Inventory(player);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    private static GridNode connect(Ae2bcIoPortBlockEntity port, IGrid grid) throws ReflectiveOperationException {
        GridNode node = Mockito.mock(GridNode.class);
        Mockito.when(node.getGrid()).thenReturn(grid);
        Mockito.when(node.isActive()).thenReturn(true);
        Field field = ManagedGridNode.class.getDeclaredField("node");
        field.setAccessible(true);
        field.set(port.getMainNode(), node);
        return node;
    }

    private static StorageCell cell(ItemStack stack) {
        return Objects.requireNonNull(StorageCells.getCellInventory(stack, null));
    }

    private static IGrid poweredGrid(MEStorage network) {
        IGrid grid = Mockito.mock(IGrid.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(grid.getStorageService().getInventory()).thenReturn(network);
        Mockito.when(grid.getEnergyService().extractAEPower(Mockito.anyDouble(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
        return grid;
    }

    @Test
    void independentBlockHasSixInputSixOutputAndFiveUpgradeSlots() {
        Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        Assertions.assertFalse(IOPortBlockEntity.class.isInstance(port));
        Assertions.assertEquals(BlockRegistry.IO_PORT_ENTITY.get(), port.getType());
        Assertions.assertEquals(ItemRegistry.IO_PORT.get().getDescription(), port.getDisplayName());
        Assertions.assertEquals(12, port.getInternalInventory().size());
        Assertions.assertEquals(5, port.getUpgrades().size());
        GridNode node = Mockito.mock(GridNode.class);
        Assertions.assertEquals(1, port.getTickingRequest(node).minTickRate());
        Assertions.assertEquals(1, port.getTickingRequest(node).initialTickRate());
        Player player = IoPortIntegrationTest.player(Mockito.mock(Level.class));
        Ae2bcIoPortMenu menu = new Ae2bcIoPortMenu(1, player.getInventory(), port);
        Assertions.assertEquals(6, menu.getSlots(SlotSemantics.MACHINE_INPUT).size());
        Assertions.assertEquals(6, menu.getSlots(SlotSemantics.MACHINE_OUTPUT).size());
        Assertions.assertEquals(5, menu.getSlots(SlotSemantics.UPGRADE).size());
        Assertions.assertEquals(36, menu.getSlots(SlotSemantics.PLAYER_INVENTORY).size()
                + menu.getSlots(SlotSemantics.PLAYER_HOTBAR).size());
    }

    @Test
    void originalPortAndMenuAreNoLongerInjected() {
        Assertions.assertTrue(Arrays.stream(IOPortBlockEntity.class.getDeclaredFields())
                .noneMatch(field -> field.getName().startsWith("ae2bc$")));
        Assertions.assertTrue(Arrays.stream(IOPortMenu.class.getDeclaredFields())
                .noneMatch(field -> field.getName().startsWith("ae2bc$")));
        IOPortBlockEntity original = new IOPortBlockEntity(
                AEBlockEntities.IO_PORT.get(), BlockPos.ZERO, AEBlocks.IO_PORT.block().defaultBlockState());
        Assertions.assertEquals(3, original.getUpgrades().size());
    }

    @Test
    void completionRoutesExternallyWithoutMovingExistingOutput() {
        Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        Level level = Mockito.mock(Level.class);
        port.setLevel(level);
        AppEngInternalInventory external = new AppEngInternalInventory(1);
        Direction front = port.getFront();
        Mockito.when(level.isLoaded(BlockPos.ZERO.relative(front))).thenReturn(true);
        Mockito.when(level.getCapability(Capabilities.ItemHandler.BLOCK,
                BlockPos.ZERO.relative(front), front.getOpposite())).thenReturn(external.toItemHandler());
        port.setTransferData(new IoPortTransferData(CellTransferMode.EXTERNAL, 0).toggle(RelativeSide.FRONT));
        port.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        port.getInternalInventory().setItemDirect(6, AEItems.ITEM_CELL_4K.stack());
        Assertions.assertTrue(port.moveCell(0));
        Assertions.assertTrue(port.getInternalInventory().getStackInSlot(0).isEmpty());
        Assertions.assertEquals(AEItems.ITEM_CELL_1K.asItem(), external.getStackInSlot(0).getItem());
        Assertions.assertEquals(AEItems.ITEM_CELL_4K.asItem(), port.getInternalInventory().getStackInSlot(6).getItem());
    }

    @Test
    void saveLoadPreservesCellsUpgradesAndSettings() {
        Ae2bcIoPortBlockEntity original = IoPortIntegrationTest.port();
        IoPortTransferData data = new IoPortTransferData(CellTransferMode.PREFER_EXTERNAL, 0)
                .toggle(RelativeSide.TOP).toggle(RelativeSide.LEFT);
        original.setTransferData(data);
        original.getConfigManager().putSetting(Settings.OPERATION_MODE, OperationMode.FILL);
        original.getConfigManager().putSetting(Settings.FULLNESS_MODE, FullnessMode.FULL);
        original.getUpgrades().setItemDirect(0, AEItems.SPEED_CARD.stack());
        ItemStack stack = AEItems.ITEM_CELL_1K.stack();
        StorageCell cell = IoPortIntegrationTest.cell(stack);
        cell.insert(AEItemKey.of(Items.STONE), 128, Actionable.MODULATE, IActionSource.empty());
        cell.persist();
        original.getInternalInventory().setItemDirect(0, stack);
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag tag = new CompoundTag();
        original.saveAdditional(tag, registries);
        Ae2bcIoPortBlockEntity restored = IoPortIntegrationTest.port();
        restored.loadTag(tag, registries);
        Assertions.assertEquals(data, restored.getTransferData());
        Assertions.assertEquals(OperationMode.FILL, restored.getConfigManager().getSetting(Settings.OPERATION_MODE));
        Assertions.assertEquals(FullnessMode.FULL, restored.getConfigManager().getSetting(Settings.FULLNESS_MODE));
        Assertions.assertTrue(ItemStack.matches(stack, restored.getInternalInventory().getStackInSlot(0)));
        Assertions.assertEquals(1, restored.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD));
    }

    @Test
    void blockedPortRetriesAndHonorsPowerRedstoneAndCompletion() throws ReflectiveOperationException {
        Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        Level level = Mockito.mock(Level.class);
        port.setLevel(level);
        IGrid grid = Mockito.mock(IGrid.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(grid.getStorageService().getCachedInventory()).thenReturn(new KeyCounter());
        GridNode node = IoPortIntegrationTest.connect(port, grid);
        port.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        Mockito.when(node.isActive()).thenReturn(false);
        Assertions.assertEquals(TickRateModulation.IDLE, port.tickingRequest(node, 1));
        Mockito.when(node.isActive()).thenReturn(true);
        port.getUpgrades().setItemDirect(0, AEItems.REDSTONE_CARD.stack());
        port.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.HIGH_SIGNAL);
        Assertions.assertEquals(TickRateModulation.SLEEP, port.tickingRequest(node, 1));
        port.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        port.getConfigManager().putSetting(Settings.FULLNESS_MODE, FullnessMode.FULL);
        Assertions.assertEquals(TickRateModulation.IDLE, port.tickingRequest(node, 1));
        port.getConfigManager().putSetting(Settings.FULLNESS_MODE, FullnessMode.EMPTY);
        port.setTransferData(new IoPortTransferData(CellTransferMode.EXTERNAL, 0));
        Assertions.assertEquals(TickRateModulation.IDLE, port.tickingRequest(node, 1));
        Assertions.assertFalse(port.getInternalInventory().getStackInSlot(0).isEmpty());
        port.setTransferData(IoPortTransferData.DEFAULT);
        Assertions.assertEquals(TickRateModulation.SLEEP, port.tickingRequest(node, 1));
        Assertions.assertTrue(port.getInternalInventory().getStackInSlot(0).isEmpty());
        Assertions.assertEquals(AEItems.ITEM_CELL_1K.asItem(), port.getInternalInventory().getStackInSlot(6).getItem());
    }

    /* 使用真实存储元件验证预算、资源转移、数据落盘及五张加速卡。 */
    @Test
    void actualCellTransfersUseSharedBudgetAndPersist() throws ReflectiveOperationException {
        Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        IGrid grid = Mockito.mock(IGrid.class, Mockito.RETURNS_DEEP_STUBS);
        MEStorage network = Mockito.mock(MEStorage.class);
        Mockito.when(grid.getStorageService().getInventory()).thenReturn(network);
        Mockito.when(network.insert(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(1, Long.class));
        Mockito.when(grid.getEnergyService().extractAEPower(Mockito.anyDouble(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
        GridNode node = IoPortIntegrationTest.connect(port, grid);
        AEItemKey key = AEItemKey.of(Items.STONE);
        for (int slot = 0; slot < 2; slot++) {
            ItemStack stack = AEItems.ITEM_CELL_1K.stack();
            StorageCell cell = IoPortIntegrationTest.cell(stack);
            cell.insert(key, 4096, Actionable.MODULATE, IActionSource.empty());
            cell.persist();
            port.getInternalInventory().setItemDirect(slot, stack);
        }
        Assertions.assertEquals(TickRateModulation.URGENT, port.tickingRequest(node, 1));
        Assertions.assertEquals(2048, IoPortIntegrationTest.cell(
                port.getInternalInventory().getStackInSlot(0)).getAvailableStacks().get(key));
        Assertions.assertEquals(4096, IoPortIntegrationTest.cell(
                port.getInternalInventory().getStackInSlot(1)).getAvailableStacks().get(key));
        for (int count = 1; count <= 5; count++) {
            port.getUpgrades().setItemDirect(count - 1, AEItems.SPEED_CARD.stack());
            Assertions.assertEquals(2048L << count, port.getOperationBudget());
        }
        port.tickingRequest(node, 1);
        Assertions.assertTrue(port.getInternalInventory().getStackInSlot(0).isEmpty());
        Assertions.assertTrue(port.getInternalInventory().getStackInSlot(1).isEmpty());
    }

    /* 真实 63 种资源元件记录目标探测次数，确保每种资源实际只回网一次。 */
    @Test
    void manyResourceCellReturnsEveryTypeWithoutRescanningDepletedPrefixes() throws ReflectiveOperationException {
        Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        AtomicInteger simulations = new AtomicInteger();
        AtomicInteger insertions = new AtomicInteger();
        AtomicLong received = new AtomicLong();
        MEStorage network = new MEStorage() {
            @Override
            public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                if (mode == Actionable.SIMULATE) {
                    simulations.incrementAndGet();
                } else {
                    insertions.incrementAndGet();
                    received.addAndGet(amount);
                }
                return amount;
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }
        };
        IGrid grid = IoPortIntegrationTest.poweredGrid(network);
        GridNode node = IoPortIntegrationTest.connect(port, grid);
        ItemStack cellStack = AEItems.ITEM_CELL_64K.stack();
        StorageCell cell = IoPortIntegrationTest.cell(cellStack);
        for (int index = 0; index < 63; index++) {
            ItemStack item = new ItemStack(Items.PAPER);
            item.set(DataComponents.CUSTOM_NAME, Component.literal("io workload " + index));
            Assertions.assertEquals(1, cell.insert(AEItemKey.of(item), 1, Actionable.MODULATE, IActionSource.empty()));
        }
        cell.persist();
        port.getInternalInventory().setItemDirect(0, cellStack);
        port.tickingRequest(node, 1);
        Assertions.assertEquals(63, received.get());
        Assertions.assertEquals(63, insertions.get());
        Assertions.assertEquals(126, simulations.get());
        Assertions.assertTrue(port.getInternalInventory().getStackInSlot(0).isEmpty());
        System.out.printf("HOTPATH io types=63 target_simulations=%d actual_insertions=%d%n",
                simulations.get(), insertions.get());
    }

    @Test
    void partialPerCallAcceptanceRetriesTheSameKeyAndRestoresTheRemainder() throws ReflectiveOperationException {
        Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        AtomicLong received = new AtomicLong();
        MEStorage network = Mockito.mock(MEStorage.class);
        Mockito.when(network.insert(Mockito.any(AEKey.class), Mockito.anyLong(),
                Mockito.any(Actionable.class), Mockito.any(IActionSource.class))).thenAnswer(call -> {
            long requested = call.getArgument(1);
            if (call.getArgument(2) == Actionable.SIMULATE) {
                return requested;
            }
            long inserted = Math.min(requested, Math.min(5, 19 - received.get()));
            received.addAndGet(inserted);
            return inserted;
        });
        GridNode node = IoPortIntegrationTest.connect(port, IoPortIntegrationTest.poweredGrid(network));
        ItemStack cellStack = AEItems.ITEM_CELL_1K.stack();
        AEKey key = AEItemKey.of(Items.STONE);
        StorageCell cell = IoPortIntegrationTest.cell(cellStack);
        cell.insert(key, 64, Actionable.MODULATE, IActionSource.empty());
        cell.persist();
        port.getInternalInventory().setItemDirect(0, cellStack);
        Assertions.assertEquals(TickRateModulation.URGENT, port.tickingRequest(node, 1));
        Assertions.assertEquals(19, received.get());
        Assertions.assertEquals(45, IoPortIntegrationTest.cell(
                port.getInternalInventory().getStackInSlot(0)).getAvailableStacks().get(key));
        Assertions.assertEquals(TickRateModulation.IDLE, port.tickingRequest(node, 1));
        Assertions.assertEquals(19, received.get());
    }

    @Test
    void fillingUsesStableKeysWhenNetworkExtractionRemovesLiveCachedEntries() throws ReflectiveOperationException {
        Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        port.getConfigManager().putSetting(Settings.OPERATION_MODE, OperationMode.FILL);
        port.getConfigManager().putSetting(Settings.FULLNESS_MODE, FullnessMode.FULL);
        KeyCounter live = new KeyCounter();
        for (int index = 0; index < 10; index++) {
            ItemStack item = new ItemStack(Items.PAPER);
            item.set(DataComponents.CUSTOM_NAME, Component.literal("live " + index));
            live.add(AEItemKey.of(item), 1);
        }
        MEStorage network = Mockito.mock(MEStorage.class);
        Mockito.when(network.extract(Mockito.any(AEKey.class), Mockito.anyLong(),
                Mockito.any(Actionable.class), Mockito.any(IActionSource.class))).thenAnswer(call -> {
            AEKey key = call.getArgument(0);
            long amount = Math.min(call.getArgument(1, Long.class), live.get(key));
            if (call.getArgument(2) == Actionable.MODULATE) {
                live.remove(key);
            }
            return amount;
        });
        IGrid grid = IoPortIntegrationTest.poweredGrid(network);
        Mockito.when(grid.getStorageService().getCachedInventory()).thenReturn(live);
        GridNode node = IoPortIntegrationTest.connect(port, grid);
        port.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        Assertions.assertEquals(TickRateModulation.URGENT, port.tickingRequest(node, 1));
        Assertions.assertTrue(live.isEmpty());
        KeyCounter stored = IoPortIntegrationTest.cell(
                port.getInternalInventory().getStackInSlot(0)).getAvailableStacks();
        Assertions.assertEquals(10, stored.size());
        for (long amount : stored.keySet().stream().mapToLong(stored::get).toArray()) {
            Assertions.assertEquals(1, amount);
        }
    }

    @Test
    void fluidFillUsesResourceUnitsAndCannotTransferWithoutEnergy() throws ReflectiveOperationException {
        Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        port.getConfigManager().putSetting(Settings.OPERATION_MODE, OperationMode.FILL);
        port.getConfigManager().putSetting(Settings.FULLNESS_MODE, FullnessMode.FULL);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        long initial = 4096L * water.getAmountPerOperation();
        StorageCell network = IoPortIntegrationTest.cell(AEItems.FLUID_CELL_64K.stack());
        Assertions.assertEquals(initial, network.insert(water, initial, Actionable.MODULATE, IActionSource.empty()));
        IGrid grid = Mockito.mock(IGrid.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(grid.getStorageService().getInventory()).thenReturn(network);
        Mockito.when(grid.getStorageService().getCachedInventory()).thenAnswer(ignored -> network.getAvailableStacks());
        GridNode node = IoPortIntegrationTest.connect(port, grid);
        port.getInternalInventory().setItemDirect(0, AEItems.FLUID_CELL_64K.stack());
        Assertions.assertEquals(TickRateModulation.IDLE, port.tickingRequest(node, 1));
        Assertions.assertEquals(initial, network.getAvailableStacks().get(water));
        Assertions.assertEquals(0, IoPortIntegrationTest.cell(
                port.getInternalInventory().getStackInSlot(0)).getAvailableStacks().get(water));
        Mockito.when(grid.getEnergyService().extractAEPower(Mockito.anyDouble(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
        Assertions.assertEquals(TickRateModulation.URGENT, port.tickingRequest(node, 1));
        long moved = 2048L * water.getAmountPerOperation();
        Assertions.assertEquals(initial - moved, network.getAvailableStacks().get(water));
        Assertions.assertEquals(moved, IoPortIntegrationTest.cell(
                port.getInternalInventory().getStackInSlot(0)).getAvailableStacks().get(water));
    }

    @Test
    void menuActionsOnlyAffectTheOpenValidMenu() {
        Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
        Level level = Mockito.mock(Level.class);
        port.setLevel(level);
        Mockito.when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(port);
        Player player = IoPortIntegrationTest.player(level);
        Ae2bcIoPortMenu menu = new Ae2bcIoPortMenu(23, player.getInventory(), port);
        menu.cycleTransferMode();
        Assertions.assertEquals(IoPortTransferData.DEFAULT, menu.getTransferData());
        player.containerMenu = menu;
        menu.receiveClientAction("cycle_cell_transfer", null);
        Assertions.assertEquals(CellTransferMode.EXTERNAL, menu.getTransferData().mode());
        menu.receiveClientAction("toggle_cell_output_side", "\"LEFT\"");
        Assertions.assertTrue(menu.getTransferData().enabled(RelativeSide.LEFT));
        menu.broadcastChanges();
        menu.receiveClientAction("clear_cell_output_sides", null);
        Assertions.assertEquals(0, menu.getTransferData().sides());
        menu.setValidMenu(false);
        menu.cycleTransferMode();
        Assertions.assertEquals(CellTransferMode.EXTERNAL, menu.getTransferData().mode());
    }

    @Test
    void clientSnapshotOnlyUpdatesTheMatchingOpenMenu() {
        Level level = Mockito.mock(Level.class);
        Mockito.when(level.isClientSide()).thenReturn(true);
        Player player = IoPortIntegrationTest.player(level);
        Ae2bcIoPortMenu menu = new Ae2bcIoPortMenu(23, player.getInventory(), IoPortIntegrationTest.port());
        player.containerMenu = menu;
        IoPortTransferData data = new IoPortTransferData(CellTransferMode.PREFER_EXTERNAL, 7);
        new IoPortTransferPacket(22, data, FullnessMode.FULL, OperationMode.FILL).handleOnClient(player);
        Assertions.assertEquals(IoPortTransferData.DEFAULT, menu.getTransferData());
        new IoPortTransferPacket(23, data, FullnessMode.FULL, OperationMode.FILL).handleOnClient(player);
        Assertions.assertEquals(data, menu.getTransferData());
        Assertions.assertEquals(FullnessMode.FULL, menu.getFullMode());
        Assertions.assertEquals(OperationMode.FILL, menu.getOperationMode());
    }

    @Test
    void everyFaceAcceptsCellsWithoutExposingInputsForExtractionOrOverflowingIntoOutputs() {
        for (Direction side : Direction.values()) {
            Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
            IItemHandler inventory = Objects.requireNonNull(port.getExposedItemHandler(side));
            Assertions.assertTrue(ItemHandlerHelper.insertItemStacked(inventory, AEItems.ITEM_CELL_1K.stack(), true).isEmpty());
            Assertions.assertTrue(port.getInternalInventory().isEmpty());
            for (int slot = 0; slot < Ae2bcIoPortBlockEntity.CELL_SLOTS; slot++) {
                Assertions.assertTrue(ItemHandlerHelper.insertItemStacked(inventory, AEItems.ITEM_CELL_1K.stack(), false).isEmpty());
                Assertions.assertEquals(AEItems.ITEM_CELL_1K.asItem(), port.getInternalInventory().getStackInSlot(slot).getItem());
            }
            Assertions.assertTrue(ItemStack.matches(AEItems.ITEM_CELL_4K.stack(),
                    ItemHandlerHelper.insertItemStacked(inventory, AEItems.ITEM_CELL_4K.stack(), false)));
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                Assertions.assertTrue(inventory.extractItem(slot, 1, false).isEmpty());
            }
            for (int slot = 0; slot < Ae2bcIoPortBlockEntity.CELL_SLOTS; slot++) {
                Assertions.assertEquals(1, port.getInternalInventory().getStackInSlot(slot).getCount());
                Assertions.assertTrue(port.getInternalInventory().getStackInSlot(slot + Ae2bcIoPortBlockEntity.CELL_SLOTS).isEmpty());
            }
        }
    }

    @Test
    void outputSlotsRemainExtractOnlyAndAccessibleOnlyFromSideFaces() {
        for (Direction side : Direction.values()) {
            Ae2bcIoPortBlockEntity port = IoPortIntegrationTest.port();
            IItemHandler inventory = Objects.requireNonNull(port.getExposedItemHandler(side));
            boolean topOrBottom = side == port.getTop() || side == port.getTop().getOpposite();
            for (int slot = 0; slot < Ae2bcIoPortBlockEntity.CELL_SLOTS; slot++) {
                int outputSlot = slot + Ae2bcIoPortBlockEntity.CELL_SLOTS;
                port.getInternalInventory().setItemDirect(outputSlot, AEItems.ITEM_CELL_4K.stack());
                ItemStack extracted = inventory.extractItem(slot, 1, false);
                if (topOrBottom) {
                    Assertions.assertTrue(extracted.isEmpty());
                    Assertions.assertEquals(AEItems.ITEM_CELL_4K.asItem(), port.getInternalInventory().getStackInSlot(outputSlot).getItem());
                } else {
                    Assertions.assertEquals(AEItems.ITEM_CELL_4K.asItem(), extracted.getItem());
                    Assertions.assertFalse(inventory.isItemValid(slot, AEItems.ITEM_CELL_1K.stack()));
                    Assertions.assertTrue(ItemStack.matches(AEItems.ITEM_CELL_1K.stack(),
                            inventory.insertItem(slot, AEItems.ITEM_CELL_1K.stack(), false)));
                    Assertions.assertTrue(port.getInternalInventory().getStackInSlot(outputSlot).isEmpty());
                }
            }
        }
    }
}
