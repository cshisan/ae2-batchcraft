/*
 * 元件处理语义参考 Applied Energistics 2 的 IOPortBlockEntity。
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 * 相关实现按 LGPL-3.0-or-later 分发，来源与许可证见 META-INF/licenses/AE2-NOTICE.txt。
 */
package org.ae2bc.ioport;

import appeng.api.config.Actionable;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.StorageHelper;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.me.helpers.MachineSource;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.AEItemFilters;
import appeng.util.SettingsFrom;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.ae2bc.registry.BlockRegistry;
import org.ae2bc.common.Utils;
import org.ae2bc.core.dispatch.WorkBudget;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Ae2bcIoPortBlockEntity extends AENetworkedInvBlockEntity implements IUpgradeableObject, IConfigurableObject, IGridTickable {
    public static final int CELL_SLOTS = 6;
    private static final int UPGRADE_SLOTS = 5;
    private static final int MAX_VISITS = 256;
    private static final int SNAPSHOT_MAX_AGE = 200;
    private final AppEngInternalInventory inputCells = new AppEngInternalInventory(this, CELL_SLOTS);
    private final AppEngInternalInventory outputCells = new AppEngInternalInventory(this, CELL_SLOTS);
    private final InternalInventory cells = new CombinedInternalInventory(this.inputCells, this.outputCells);
    private final InternalInventory input = new FilteredInternalInventory(this.inputCells, AEItemFilters.INSERT_ONLY);
    private final InternalInventory sideInventory = new CombinedInternalInventory(
            new FilteredInternalInventory(this.outputCells, AEItemFilters.EXTRACT_ONLY), this.input);
    private final IConfigManager config;
    private final IUpgradeInventory upgrades;
    private final IActionSource source = new MachineSource(this);
    @Getter
    private IoPortTransferData transferData = IoPortTransferData.DEFAULT;
    private boolean active;
    private @Nullable GenericStack pendingTransfer;
    private int pendingCell = -1;
    private boolean transferring;
    private List<AEKey> networkKeys = List.of();
    private @Nullable IGrid snapshotGrid;
    private int snapshotAge;
    private boolean refreshNetworkKeys = true;
    private final int[] keyCursors = new int[CELL_SLOTS];
    private int cellCursor;

    public Ae2bcIoPortBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.IO_PORT_ENTITY.get(), pos, state);
        this.getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).addService(IGridTickable.class, this);
        this.config = IConfigManager.builder(this::onConfigChanged)
                .registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE)
                .registerSetting(Settings.FULLNESS_MODE, FullnessMode.EMPTY)
                .registerSetting(Settings.OPERATION_MODE, OperationMode.EMPTY)
                .build();
        this.upgrades = UpgradeInventories.forMachine(BlockRegistry.IO_PORT.get(), UPGRADE_SLOTS, this::onConfigChanged);
    }

    public void setTransferData(IoPortTransferData data) {
        if (!this.transferData.equals(data)) {
            this.transferData = data;
            this.onConfigChanged();
        }
    }

    /* 设置和升级变更均持久化，并重新评估休眠状态。 */
    private void onConfigChanged() {
        this.clearCandidates();
        this.saveChanges();
        this.updateTask();
    }

    private void updateTask() {
        this.getMainNode().ifPresent((grid, node) -> {
            if (this.hasWork()) {
                grid.getTickManager().wakeDevice(node);
            } else {
                grid.getTickManager().sleepDevice(node);
            }
        });
    }

    public void updateRedstoneState() {
        this.updateTask();
    }

    private boolean hasWork() {
        return (this.pendingTransfer != null || !this.inputCells.isEmpty()) && this.isEnabled();
    }

    private boolean isEnabled() {
        if (!this.upgrades.isInstalled(AEItems.REDSTONE_CARD)) {
            return true;
        }
        RedstoneMode mode = this.config.getSetting(Settings.REDSTONE_CONTROLLED);
        if (mode == RedstoneMode.IGNORE) {
            return true;
        }
        Level currentLevel = this.getLevel();
        boolean signal = currentLevel != null && currentLevel.hasNeighborSignal(this.getBlockPos());
        return (mode == RedstoneMode.HIGH_SIGNAL) == signal;
    }

    public boolean isActive() {
        Level currentLevel = this.getLevel();
        return currentLevel != null && !currentLevel.isClientSide() ? this.getMainNode().isOnline() : this.active;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        this.clearCandidates();
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            this.markForUpdate();
        }
        this.updateTask();
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.config;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.cells;
    }

    @Override
    public @Nullable InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(ISegmentedInventory.UPGRADES)) {
            return this.upgrades;
        }
        if (id.equals(ISegmentedInventory.CELLS)) {
            return this.cells;
        }
        return super.getSubInventory(id);
    }

    /* 各面均可插入输入槽，仅侧面可提取输出槽。 */
    @Override
    protected InternalInventory getExposedInventoryForSide(Direction side) {
        return side == this.getTop() || side == this.getTop().getOpposite() ? this.input : this.sideInventory;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        if (inventory == this.inputCells) {
            if (this.inputCells.isEmpty()) {
                this.clearCandidates();
            } else {
                this.keyCursors[slot] = 0;
                this.refreshNetworkKeys = true;
            }
        }
        this.updateTask();
    }

    @Override
    public AECableType getCableConnectionType(Direction side) {
        return AECableType.SMART;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, Math.max(1, TickRates.IOPort.getMax()), !this.hasWork(), 1);
    }

    /* 每轮共享处理预算；堵塞时保留元件并继续低频重试。 */
    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.transferring) {
            return TickRateModulation.IDLE;
        }
        this.transferring = true;
        try {
            return this.tick(ticksSinceLastCall);
        } finally {
            this.transferring = false;
        }
    }

    /* 余量独占恢复轮次，失败访问也消耗全机共享预算。 */
    private TickRateModulation tick(int ticksSinceLastCall) {
        if (!this.hasWork()) {
            this.clearCandidates();
            return TickRateModulation.SLEEP;
        }
        IGrid grid = this.getMainNode().getGrid();
        if (!this.getMainNode().isActive() || grid == null) {
            return TickRateModulation.IDLE;
        }
        if (this.pendingTransfer != null) {
            boolean recovered = this.retryPendingTransfer(grid);
            return !this.hasWork() ? TickRateModulation.SLEEP
                    : recovered ? TickRateModulation.URGENT : TickRateModulation.IDLE;
        }
        boolean empty = this.config.getSetting(Settings.OPERATION_MODE) == OperationMode.EMPTY;
        this.snapshotAge = (int) Math.min(SNAPSHOT_MAX_AGE,
                (long) this.snapshotAge + Math.max(0, ticksSinceLastCall));
        KeyCounter networkInventory = empty ? null : grid.getStorageService().getCachedInventory();
        if (networkInventory != null && (this.snapshotGrid != grid
                || this.refreshNetworkKeys || this.snapshotAge >= SNAPSHOT_MAX_AGE)) {
            this.networkKeys = Ae2bcIoPortBlockEntity.keys(networkInventory);
            this.snapshotGrid = grid;
            this.snapshotAge = 0;
            this.refreshNetworkKeys = false;
        }
        long budget = this.getOperationBudget();
        WorkBudget visits = new WorkBudget(MAX_VISITS);
        boolean progressed = false;
        int start = this.cellCursor;
        for (int offset = 0; offset < CELL_SLOTS; offset++) {
            int slot = (start + offset) % CELL_SLOTS;
            this.cellCursor = (slot + 1) % CELL_SLOTS;
            ItemStack stack = this.inputCells.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            StorageCell cell = StorageCells.getCellInventory(stack, null);
            if (cell == null) {
                progressed |= this.moveCell(slot);
                continue;
            }
            TransferResult result = this.transferContents(grid, cell, slot, budget, visits, networkInventory);
            long remaining = result.remainingOperations();
            if (remaining != budget) {
                progressed = true;
            }
            budget = remaining;
            if (this.pendingTransfer != null) {
                break;
            }
            if (budget > 0 && result.completedScan() && this.matchesFullnessMode(cell)) {
                progressed |= this.moveCell(slot);
            }
            if (budget <= 0 || visits.remaining() == 0) {
                break;
            }
        }
        if (!this.hasWork()) {
            this.clearCandidates();
            return TickRateModulation.SLEEP;
        }
        return progressed ? TickRateModulation.URGENT : TickRateModulation.IDLE;
    }

    long getOperationBudget() {
        return 2048L << this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD);
    }

    boolean matchesFullnessMode(StorageCell cell) {
        return switch (this.config.getSetting(Settings.FULLNESS_MODE)) {
            case HALF -> true;
            case EMPTY -> cell.getStatus() == CellState.EMPTY;
            case FULL -> cell.getStatus() == CellState.FULL;
        };
    }

    /* 只缓存候选键，数量始终读取当前库存；耗能预检后才接管来源资源。 */
    private TransferResult transferContents(
            IGrid grid, StorageCell cell, int slot, long budget, WorkBudget visits,
            @Nullable KeyCounter networkInventory
    ) {
        boolean empty = networkInventory == null;
        MEStorage network = grid.getStorageService().getInventory();
        MEStorage from = empty ? cell : network;
        MEStorage to = empty ? network : cell;
        KeyCounter available = networkInventory == null ? cell.getAvailableStacks() : networkInventory;
        List<AEKey> keys = empty ? Ae2bcIoPortBlockEntity.keys(available) : this.networkKeys;
        int start = keys.isEmpty() ? 0 : Math.floorMod(this.keyCursors[slot], keys.size());
        int visited = 0;
        boolean completedScan = keys.isEmpty();
        boolean changed = false;
        try {
            while (visited < keys.size() && budget > 0 && visits.tryUse()) {
                int index = (start + visited++) % keys.size();
                this.keyCursors[slot] = (index + 1) % keys.size();
                if (index + 1 == keys.size()) {
                    completedScan = true;
                    if (!empty) {
                        this.refreshNetworkKeys = true;
                    }
                }
                AEKey key = keys.get(index);
                long remaining = available.get(key);
                while (budget > 0 && remaining > 0) {
                    long requested = Math.min(remaining,
                            Utils.saturatingMultiply(budget, Math.max(1L, key.getAmountPerOperation())));
                    long possible = StorageHelper.poweredInsert(
                            grid.getEnergyService(), to, key, requested, this.source, Actionable.SIMULATE);
                    if (possible <= 0) {
                        break;
                    }
                    long extracted = from.extract(key, possible, Actionable.MODULATE, this.source);
                    if (extracted <= 0) {
                        break;
                    }
                    changed = true;
                    this.pendingCell = empty ? -1 : slot;
                    this.pendingTransfer = new GenericStack(key, extracted);
                    long inserted = Math.clamp(StorageHelper.poweredInsert(
                            grid.getEnergyService(), to, key, extracted, this.source), 0, extracted);
                    this.reducePending(inserted);
                    if (this.pendingTransfer != null) {
                        this.reducePending(from.insert(
                                key, this.pendingTransfer.amount(), Actionable.MODULATE, this.source));
                    }
                    if (inserted <= 0) {
                        break;
                    }
                    budget -= Utils.operationsFor(inserted, Math.max(1L, key.getAmountPerOperation()));
                    remaining -= inserted;
                    if (this.pendingTransfer != null || remaining <= 0 || budget <= 0 || !visits.tryUse()) {
                        break;
                    }
                }
                if (this.pendingTransfer != null) {
                    break;
                }
            }
            if (!empty && (keys.isEmpty() || visited == keys.size())) {
                this.refreshNetworkKeys = true;
            }
            return new TransferResult(budget, completedScan);
        } finally {
            if (changed) {
                cell.persist();
                this.saveChanges();
            }
        }
    }

    private record TransferResult(long remainingOperations, boolean completedScan) {
    }

    /* 失败余量继续原交付；填充元件被取走或拒收时，也允许退回来源网络。 */
    private boolean retryPendingTransfer(IGrid grid) {
        GenericStack pending = this.pendingTransfer;
        if (pending == null) {
            return false;
        }
        MEStorage network = grid.getStorageService().getInventory();
        StorageCell cell = this.pendingCell < 0 ? null
                : StorageCells.getCellInventory(this.inputCells.getStackInSlot(this.pendingCell), null);
        long requested = Math.min(pending.amount(), Utils.saturatingMultiply(
                this.getOperationBudget(), Math.max(1L, pending.what().getAmountPerOperation())));
        try {
            if (this.pendingCell < 0 || cell != null) {
                MEStorage target = cell == null ? network : cell;
                this.reducePending(StorageHelper.poweredInsert(
                        grid.getEnergyService(), target, pending.what(), requested, this.source));
            }
            if (this.pendingCell >= 0 && this.pendingTransfer != null) {
                this.reducePending(network.insert(
                        pending.what(), this.pendingTransfer.amount(), Actionable.MODULATE, this.source));
            }
        } finally {
            if (this.pendingTransfer != pending) {
                if (cell != null) {
                    cell.persist();
                }
                this.saveChanges();
            }
        }
        return this.pendingTransfer == null || this.pendingTransfer.amount() < pending.amount();
    }

    private void reducePending(long accepted) {
        GenericStack pending = this.pendingTransfer;
        if (pending != null && accepted > 0) {
            long remaining = pending.amount() - Math.clamp(accepted, 0, pending.amount());
            this.pendingTransfer = remaining == 0 ? null : new GenericStack(pending.what(), remaining);
        }
    }

    private static List<AEKey> keys(KeyCounter available) {
        List<AEKey> keys = new ArrayList<>(available.size());
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (entry.getLongValue() > 0) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    private void clearCandidates() {
        this.networkKeys = List.of();
        this.snapshotGrid = null;
        this.snapshotAge = 0;
        this.refreshNetworkKeys = true;
        Arrays.fill(this.keyCursors, 0);
    }

    /* 只搬运已满足完成条件的元件，输出槽原有元件不参与自动外送。 */
    boolean moveCell(int slot) {
        if (this.pendingTransfer != null && this.pendingCell == slot) {
            return false;
        }
        return IoPortTransfer.move(this.inputCells, slot, this.outputCells, this.transferData.mode(), this::insertExternal);
    }

    private ItemStack insertExternal(ItemStack stack) {
        Level currentLevel = this.getLevel();
        if (currentLevel == null || currentLevel.isClientSide()) {
            return stack;
        }
        return IoPortTransfer.insertExternal(currentLevel, this.getBlockPos(), this.getOrientation(), this.transferData, stack);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.config.writeToNBT(tag, registries);
        this.upgrades.writeToNBT(tag, "upgrades", registries);
        this.transferData.save(tag);
        if (this.pendingTransfer != null) {
            tag.put("pendingTransfer", GenericStack.writeTag(registries, this.pendingTransfer));
            tag.putInt("pendingCell", this.pendingCell);
        } else {
            tag.remove("pendingTransfer");
            tag.remove("pendingCell");
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        this.config.readFromNBT(tag, registries);
        this.upgrades.readFromNBT(tag, "upgrades", registries);
        this.transferData = IoPortTransferData.load(tag);
        this.pendingTransfer = GenericStack.readTag(registries, tag.getCompound("pendingTransfer"));
        if (this.pendingTransfer != null && this.pendingTransfer.amount() <= 0) {
            this.pendingTransfer = null;
        }
        this.pendingCell = Math.clamp(tag.getInt("pendingCell"), -1, CELL_SLOTS - 1);
        this.clearCandidates();
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf buffer) {
        super.writeToStream(buffer);
        buffer.writeBoolean(this.isActive());
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf buffer) {
        boolean changed = super.readFromStream(buffer);
        boolean nextActive = buffer.readBoolean();
        changed |= nextActive != this.active;
        this.active = nextActive;
        return changed;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack upgrade : this.upgrades) {
            drops.add(upgrade);
        }
        if (this.pendingTransfer != null && this.pendingTransfer.what() instanceof AEItemKey) {
            this.pendingTransfer.what().addDrops(this.pendingTransfer.amount(), drops, level, pos);
        }
    }

    /* 非物品资源不走 AE2 的销毁型掉落路径，随拆下的方块保存；内存卡不复制余量。 */
    @Override
    public void exportSettings(
            @NonNull SettingsFrom mode, DataComponentMap.@NonNull Builder builder, @Nullable Player player
    ) {
        super.exportSettings(mode, builder, player);
        Level currentLevel = this.getLevel();
        if (mode == SettingsFrom.DISMANTLE_ITEM && this.pendingTransfer != null
                && !(this.pendingTransfer.what() instanceof AEItemKey) && currentLevel != null) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", BlockRegistry.IO_PORT_ENTITY.getId().toString());
            tag.put("pendingTransfer", GenericStack.writeTag(currentLevel.registryAccess(), this.pendingTransfer));
            tag.putInt("pendingCell", this.pendingCell);
            builder.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.upgrades.clear();
        this.pendingTransfer = null;
        this.clearCandidates();
    }
}
