package org.ae2bc.fake_crafting_converter;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.util.ConfigInventory;
import appeng.util.ConfigMenuInventory;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.ae2bc.common.Utils;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.pattern.*;
import org.ae2bc.registry.BlockRegistry;
import org.ae2bc.storage.ReceiveInventory;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public final class FakeCraftingConverterBlockEntity extends AENetworkedInvBlockEntity {
    public static final ModelProperty<Short> FREQUENCY_PROPERTY = new ModelProperty<>();
    public static final ModelProperty<Boolean> ACTIVE_PROPERTY = new ModelProperty<>();
    public static final int COLUMN_COUNT = 9;
    public static final int MARKER_SLOT_COUNT = COLUMN_COUNT * 2;
    public static final int STOCK_SLOT_COUNT = COLUMN_COUNT * 2;
    public static final int SLOT_CAPACITY = 4096;
    private static final String MARKERS = "Markers";
    private static final String FREQUENCY = "P2mpFrequency";
    private static final String TASK_TICKETS = "TaskTickets";
    private static final String TASK_ROWS = "Rows";
    private static final String TASK_ROW = "Row";
    private static final String TASK_AMOUNT = "Amount";

    @Getter
    private final ConfigInventory markers = ConfigInventory.configTypes(MARKER_SLOT_COUNT)
            .supportedType(AEKeyType.items())
            .allowOverstacking(false)
            .changeListener(this::markForSave)
            .build();
    private final ConfigInventory contents = ConfigInventory.storage(STOCK_SLOT_COUNT)
            .supportedType(AEKeyType.items())
            .allowOverstacking(true)
            .changeListener(this::markForSave)
            .build();
    @Getter
    private final InternalInventory stock;
    private final long[] pendingOutputAmounts = new long[COLUMN_COUNT];
    private final Map<UUID, Map<Integer, Long>> taskTickets = new HashMap<>();
    private final Set<UUID> returningTasks = new HashSet<>();
    @Getter
    private final MEStorage storage = new ConverterStorage();
    @Getter
    private short frequency;
    private @Getter UUID converterId = UUID.randomUUID();
    private boolean clientActive;

    public FakeCraftingConverterBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.FAKE_CRAFTING_CONVERTER_ENTITY.get(), pos, state);
        this.contents.setCapacity(AEKeyType.items(), FakeCraftingConverterBlockEntity.SLOT_CAPACITY);
        // AE2 包装堆叠将数量存入组件，避免普通 ItemStack 的编码数量限制。
        this.stock = new ConfigMenuInventory(this.contents) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                // 所有实际插入必须经过任务入口或外部入口，不能直接写入原始库存。
                return false;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return FakeCraftingConverterBlockEntity.this.extractPlayerStock(slot, amount, simulate);
            }
        };
    }

    /* 将输入纸行映射到前九个标记槽。 */
    private static int inputMarkerSlot(int row) {
        return row;
    }

    /* 将输出纸行映射到后九个标记槽。 */
    private static int outputMarkerSlot(int row) {
        return COLUMN_COUNT + row;
    }

    public static int inputStockSlot(int row) {
        return row;
    }

    public static int outputStockSlot(int row) {
        return COLUMN_COUNT + row;
    }

    /* 重命名只修改自定义名称，保留物品类型和其他数据组件。 */
    static ItemStack renameTicket(ItemStack source, AEItemKey marker) {
        ItemStack renamed = source.copy();
        Component name = marker.get(DataComponents.CUSTOM_NAME);
        if (name == null) {
            renamed.remove(DataComponents.CUSTOM_NAME);
        } else {
            renamed.set(DataComponents.CUSTOM_NAME, name);
        }
        return renamed;
    }

    /* 仅统一 CUSTOM_NAME 后进行完整匹配，不忽略注册 ID、NBT 或其他组件。 */
    private static boolean sameItemExceptName(AEItemKey source, AEItemKey target) {
        return target.matches(FakeCraftingConverterBlockEntity.renameTicket(source.toStack(), target));
    }

    public static @Nullable FakeCraftingConverterBlockEntity findTaskOwner(IGrid grid, UUID taskId) {
        for (FakeCraftingConverterBlockEntity converter : grid.getMachines(FakeCraftingConverterBlockEntity.class)) {
            if (converter.hasTaskTickets(taskId)) {
                return converter;
            }
        }
        return null;
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, new BlockEntityNodeListener<>() {
            @Override
            public void onGridChanged(FakeCraftingConverterBlockEntity owner, IGridNode node) {
                owner.gridChanged();
            }
        }).setInWorldNode(true).setTagName("proxy");
    }

    /* 网络活动状态变化时刷新频率灯，同时保留原有任务索引通知。 */
    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        this.gridChanged();
        this.markForClientUpdate();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.stock;
    }

    /* 按正常堆叠完整生成库存掉落，避免大库存触发通用掉落数量截断。 */
    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        for (int slot = 0; slot < this.contents.size(); slot++) {
            GenericStack stack = this.contents.getStack(slot);
            if (stack == null || !(stack.what() instanceof AEItemKey key)) {
                continue;
            }
            long remaining = stack.amount();
            while (remaining > 0) {
                int amount = (int) Math.min(remaining, key.getMaxStackSize());
                drops.add(key.toStack(amount));
                remaining -= amount;
            }
        }
    }

    /* 同网络同频率只能绑定一台转换器，失败时保留原频率和全部票据。 */
    public boolean setFrequency(short frequency) {
        IGrid grid = this.getMainNode().getGrid();
        if (grid != null && Pp2mpGridService.get(grid).isFakeCraftingFrequencyUsed(frequency, this)) {
            return false;
        }
        if (this.frequency == frequency) {
            return true;
        }
        this.frequency = frequency;
        this.markForSave();
        this.gridChanged();
        return true;
    }

    /* 冲突状态随网络索引更新，不写入存档，也不阻断原任务结算。 */
    public boolean hasFrequencyConflict() {
        IGrid grid = this.getMainNode().getGrid();
        return grid != null && Pp2mpGridService.get(grid).isFakeCraftingFrequencyUsed(this.frequency, this);
    }

    public GenericInternalInventory getGenericInventory() {
        return (GenericInternalInventory) this.storage;
    }

    public long getPendingOutputAmount(int row) {
        return row < 0 || row >= COLUMN_COUNT ? 0 : this.pendingOutputAmounts[row];
    }

    /* 将频率、网络活动状态与标记信息同步给观察区块的客户端。 */
    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeShort(this.frequency);
        data.writeBoolean(this.getMainNode().isActive());
        for (int slot = 0; slot < MARKER_SLOT_COUNT; slot++) {
            GenericStack.writeBuffer(this.markers.getStack(slot), data);
        }
        for (long pending : this.pendingOutputAmounts) {
            data.writeVarLong(pending);
        }
    }

    /* 按写入顺序读取快照，仅在状态变化时请求模型刷新。 */
    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        short frequency = data.readShort();
        boolean active = data.readBoolean();
        changed |= this.frequency != frequency || this.clientActive != active;
        this.frequency = frequency;
        this.clientActive = active;
        for (int slot = 0; slot < MARKER_SLOT_COUNT; slot++) {
            GenericStack marker = GenericStack.readBuffer(data);
            if (!Objects.equals(this.markers.getStack(slot), marker)) {
                this.markers.setStack(slot, marker);
                changed = true;
            }
        }
        for (int row = 0; row < COLUMN_COUNT; row++) {
            long pending = data.readVarLong();
            changed |= this.pendingOutputAmounts[row] != pending;
            this.pendingOutputAmounts[row] = pending;
        }
        return changed;
    }

    /* 频率灯只读取同步后的客户端状态，保留 AE2 原有模型数据。 */
    @Override
    public @NonNull ModelData getModelData() {
        return super.getModelData().derive()
                .with(FakeCraftingConverterBlockEntity.FREQUENCY_PROPERTY, this.frequency)
                .with(FakeCraftingConverterBlockEntity.ACTIVE_PROPERTY, this.clientActive)
                .build();
    }

    /* 先标记上槽，再设置同列下槽；配对仅允许自定义名称不同。 */
    public boolean canSetMarker(int slot, @Nullable AEKey key) {
        if (slot < 0 || slot >= MARKER_SLOT_COUNT || (key != null && !(key instanceof AEItemKey))) {
            return false;
        }
        if (key == null) {
            return !this.hasTaskForMarkerSlot(slot);
        }
        if (this.hasTaskForMarkerSlot(slot) && !key.equals(this.markers.getKey(slot))) {
            return false;
        }
        if (this.hasDuplicateMarker(slot, key)) {
            return false;
        }
        AEItemKey itemKey = (AEItemKey) key;
        int row = slot < COLUMN_COUNT ? slot : slot - COLUMN_COUNT;
        int stockSlot = slot < COLUMN_COUNT ? FakeCraftingConverterBlockEntity.inputStockSlot(row)
                : FakeCraftingConverterBlockEntity.outputStockSlot(row);
        ItemStack current = this.stockItem(stockSlot);
        if (!current.isEmpty() && !itemKey.matches(current)) {
            return false;
        }
        int pairedSlot = slot < COLUMN_COUNT ? slot + COLUMN_COUNT : slot - COLUMN_COUNT;
        AEItemKey pairedMarker = this.markerKey(pairedSlot);
        if (slot >= COLUMN_COUNT && pairedMarker == null) {
            return false;
        }
        if (pairedMarker != null
                && !FakeCraftingConverterBlockEntity.sameItemExceptName(itemKey, pairedMarker)) {
            return false;
        }
        // 清除标记不会移走实物，因此另一槽仍有实物时也必须满足配对限制。
        ItemStack pairedStock = this.stockItem(pairedSlot);
        AEItemKey pairedStockKey = AEItemKey.of(pairedStock);
        return pairedStockKey == null || FakeCraftingConverterBlockEntity.sameItemExceptName(itemKey, pairedStockKey);
    }

    /* 上槽和下槽分别去重，比较包含名称及全部数据组件的完整资源键。 */
    private boolean hasDuplicateMarker(int slot, AEKey key) {
        int start = slot < COLUMN_COUNT ? 0 : COLUMN_COUNT;
        for (int other = start; other < start + COLUMN_COUNT; other++) {
            if (other != slot && key.equals(this.markers.getKey(other))) {
                return true;
            }
        }
        return false;
    }

    /* 存档读取不经过标记入口，已有重复配置必须在接收新票据前拒绝。 */
    private boolean hasDuplicateMarkers() {
        for (int slot = 0; slot < MARKER_SLOT_COUNT; slot++) {
            AEKey key = this.markers.getKey(slot);
            if (key != null && this.hasDuplicateMarker(slot, key)) {
                return true;
            }
        }
        return false;
    }

    /* 清除上槽时同时清除同列下槽标记，保留实物，并一次性通知配置变化。 */
    public void setMarker(int slot, @Nullable AEKey key) {
        if (!this.canSetMarker(slot, key)) {
            return;
        }
        this.markers.beginBatch();
        try {
            this.markers.setStack(slot, key == null ? null : new GenericStack(key, 0));
            if (key == null && slot < COLUMN_COUNT) {
                this.markers.setStack(FakeCraftingConverterBlockEntity.outputMarkerSlot(slot), null);
            }
        } finally {
            this.markers.endBatch();
        }
    }

    /* 任务处理中锁定对应行，避免纸张配置变更导致票据错配。 */
    private boolean hasTaskForMarkerSlot(int slot) {
        int row = slot < COLUMN_COUNT ? slot : slot - COLUMN_COUNT;
        return this.pendingOutputAmounts[row] > 0;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("ConverterId", this.converterId);
        tag.putShort(FREQUENCY, this.frequency);
        tag.put(MARKERS, this.markers.writeToTag(registries));
        ListTag tickets = new ListTag();
        for (Map.Entry<UUID, Map<Integer, Long>> task : this.taskTickets.entrySet()) {
            tickets.add(FakeCraftingConverterBlockEntity.writeTask(task.getKey(), task.getValue()));
        }
        CompoundTag taskData = new CompoundTag();
        taskData.put(TASK_TICKETS, tickets);
        tag.put("Task", taskData);
    }

    /* 保存单个任务的标识及各列票据数量。 */
    private static CompoundTag writeTask(UUID taskId, Map<Integer, Long> rows) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("Most", taskId.getMostSignificantBits());
        entry.putLong("Least", taskId.getLeastSignificantBits());
        ListTag rowTags = new ListTag();
        for (Map.Entry<Integer, Long> row : rows.entrySet()) {
            CompoundTag rowTag = new CompoundTag();
            rowTag.putInt(TASK_ROW, row.getKey());
            rowTag.putLong(TASK_AMOUNT, row.getValue());
            rowTags.add(rowTag);
        }
        entry.put(TASK_ROWS, rowTags);
        return entry;
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        this.converterId = tag.hasUUID("ConverterId") ? tag.getUUID("ConverterId") : UUID.randomUUID();
        this.frequency = tag.getShort(FREQUENCY);
        this.markers.readFromTag(tag.getList(MARKERS, Tag.TAG_COMPOUND), registries);
        this.taskTickets.clear();
        this.returningTasks.clear();
        Arrays.fill(this.pendingOutputAmounts, 0);
        ListTag tickets = tag.getCompound("Task").getList(TASK_TICKETS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tickets.size(); i++) {
            CompoundTag entry = tickets.getCompound(i);
            UUID taskId = new UUID(entry.getLong("Most"), entry.getLong("Least"));
            Map<Integer, Long> rows = new HashMap<>();
            ListTag rowTags = entry.getList(TASK_ROWS, Tag.TAG_COMPOUND);
            for (int rowIndex = 0; rowIndex < rowTags.size(); rowIndex++) {
                CompoundTag row = rowTags.getCompound(rowIndex);
                int slot = row.getInt(TASK_ROW);
                long amount = row.getLong(TASK_AMOUNT);
                if (slot >= 0 && slot < COLUMN_COUNT && amount > 0) {
                    rows.put(slot, amount);
                }
            }
            if (!rows.isEmpty()) {
                this.taskTickets.put(taskId, rows);
            }
        }
        // 票据只恢复占用关系；实物只能从库存 NBT 读取，不能按票据补造。
        for (Map<Integer, Long> rows : this.taskTickets.values()) {
            for (Map.Entry<Integer, Long> row : rows.entrySet()) {
                long pending = this.pendingOutputAmounts[row.getKey()];
                this.pendingOutputAmounts[row.getKey()] = Utils.saturatingAdd(pending, row.getValue());
            }
        }
    }

    /* 按本次样板的完整产物数量分配票据，必须覆盖样板要求的假主产物。 */
    public TicketPlan planTask(IPatternDetails pattern, KeyCounter[] inputs) {
        KeyCounter[] forwarded = new KeyCounter[inputs.length];
        for (int slot = 0; slot < inputs.length; slot++) {
            forwarded[slot] = new KeyCounter();
            if (inputs[slot] != null) {
                forwarded[slot].addAll(inputs[slot]);
            }
        }

        if (!MaterialOutputModeResolver.hasFakeInputs(pattern)) {
            return new TicketPlan(forwarded, Map.of(), true);
        }
        KeyCounter fakeInputs = this.findFakeInputs(pattern, inputs);
        if (fakeInputs.isEmpty()) {
            return new TicketPlan(forwarded, Map.of(), false);
        }

        KeyCounter expected = new KeyCounter();
        Set<AEKey> outputKeys = new HashSet<>();
        for (int row = 0; row < COLUMN_COUNT; row++) {
            AEItemKey input = this.markerKey(FakeCraftingConverterBlockEntity.inputMarkerSlot(row));
            if (input == null || fakeInputs.get(input) <= 0) {
                continue;
            }
            AEItemKey output = this.taskOutputKey(row);
            if (output != null) {
                outputKeys.add(output);
            }
        }
        if (!outputKeys.contains(pattern.getPrimaryOutput().what())) {
            return new TicketPlan(forwarded, Map.of(), false);
        }
        for (GenericStack output : pattern.getOutputs()) {
            if (output != null && outputKeys.contains(output.what())) {
                expected.add(output.what(), output.amount());
            }
        }
        return this.allocateTickets(forwarded, fakeInputs, expected);
    }

    /* 各配对列独立分配票据；数量或容量不足时整次拒绝，不改动实物。 */
    TicketPlan allocateTickets(KeyCounter[] forwarded, KeyCounter fakeInputs, KeyCounter expected) {
        if (this.hasDuplicateMarkers()) {
            return new TicketPlan(forwarded, Map.of(), false);
        }
        Map<Integer, Long> allocated = new LinkedHashMap<>();
        for (int row = 0; row < COLUMN_COUNT; row++) {
            AEItemKey input = this.markerKey(FakeCraftingConverterBlockEntity.inputMarkerSlot(row));
            if (input == null) {
                continue;
            }
            long amount = fakeInputs.get(input);
            if (amount <= 0) {
                continue;
            }
            AEItemKey output = this.taskOutputKey(row);
            if (output == null) {
                continue;
            }
            amount = Math.min(amount, expected.get(output));
            amount = Math.min(amount, this.availableSpace(
                    FakeCraftingConverterBlockEntity.outputStockSlot(row), output));
            if (amount > 0) {
                allocated.put(row, amount);
                fakeInputs.remove(input, amount);
                expected.remove(output, amount);
            }
        }
        fakeInputs.removeZeros();
        expected.removeZeros();
        if (allocated.isEmpty() || !fakeInputs.isEmpty() || !expected.isEmpty()) {
            return new TicketPlan(forwarded, Map.of(), false);
        }
        return new TicketPlan(forwarded, Map.copyOf(allocated), true);
    }

    /* 解析当前样板中被编码为假合成模式的处理输入。 */
    private KeyCounter findFakeInputs(
            IPatternDetails pattern, KeyCounter[] inputs
    ) {
        if (!(pattern instanceof Pp2mpPatternDetails details)
                || !(details.getDelegate() instanceof AEProcessingPattern processingPattern)
                || this.getLevel() == null) {
            return new KeyCounter();
        }
        List<Pp2mpPatternSlotMapper.SlotInput> mapped = Pp2mpPatternSlotMapper.map(
                processingPattern, inputs);
        if (mapped == null) {
            return new KeyCounter();
        }
        MaterialOutputAttr config = MaterialOutputModeResolver.attr(pattern);
        KeyCounter result = new KeyCounter();
        for (Pp2mpPatternSlotMapper.SlotInput input : mapped) {
            if (config.mode(input.slot()) == MaterialOutputMode.FAKE_CRAFTING_CONVERTER) {
                if (!(input.stack().what() instanceof AEItemKey itemKey)) {
                    return new KeyCounter();
                }
                result.add(itemKey, input.stack().amount());
            }
        }
        return result;
    }

    /* 接收任务中的假合成输入，并将对应输出纸实体化存入该列下槽。 */
    public boolean acceptTaskInputs(UUID taskId, TicketPlan plan) {
        Map<Integer, Long> rows = plan.rows();
        if (!plan.valid() || rows.isEmpty() || this.taskTickets.containsKey(taskId)
                || this.hasFrequencyConflict() || this.hasDuplicateMarkers()) {
            return false;
        }

        Map<Integer, ItemStack> outputs = new HashMap<>();
        for (Map.Entry<Integer, Long> row : rows.entrySet()) {
            AEItemKey output = this.taskOutputKey(row.getKey());
            if (output == null || row.getValue() <= 0 || row.getValue() > this.availableSpace(
                    FakeCraftingConverterBlockEntity.outputStockSlot(row.getKey()), output)) {
                return false;
            }
            outputs.put(row.getKey(), output.toStack(row.getValue().intValue()));
        }

        this.taskTickets.put(taskId, new HashMap<>(rows));
        for (Map.Entry<Integer, ItemStack> output : outputs.entrySet()) {
            this.pendingOutputAmounts[output.getKey()] += output.getValue().getCount();
            this.restoreStock(FakeCraftingConverterBlockEntity.outputStockSlot(output.getKey()), output.getValue());
        }
        this.markForSave();
        return true;
    }

    public void cancelTaskTickets(UUID taskId) {
        this.cancelTaskTickets(taskId, true);
    }

    /* 拒单仅撤销暂存；已接单的取消只转换仍然实际持有的票据。 */
    public void cancelTaskTickets(UUID taskId, boolean restoreInputs) {
        if (this.returningTasks.contains(taskId)) {
            return;
        }
        Map<Integer, Long> rows = this.taskTickets.remove(taskId);
        if (rows != null) {
            for (Map.Entry<Integer, Long> row : rows.entrySet()) {
                int stockSlot = FakeCraftingConverterBlockEntity.outputStockSlot(row.getKey());
                AEItemKey output = this.taskOutputKey(row.getKey());
                ItemStack held = this.takeStock(stockSlot, output, row.getValue());
                this.pendingOutputAmounts[row.getKey()] = Math.max(
                        0, this.pendingOutputAmounts[row.getKey()] - row.getValue()
                );
                if (restoreInputs && output != null && !held.isEmpty()) {
                    long restored = this.insertExternal(row.getKey(), output,
                            held.getCount(), Actionable.MODULATE);
                    held.shrink((int) restored);
                    // 上槽满时保留尚未转换的实物，可由玩家取回；不能丢弃或补造。
                    this.restoreStock(stockSlot, held);
                }
            }
            this.markForSave();
        }
    }

    public List<GenericStack> getTaskOutputs(UUID taskId) {
        Map<Integer, Long> rows = this.taskTickets.get(taskId);
        if (rows == null) {
            return List.of();
        }
        List<GenericStack> result = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : rows.entrySet()) {
            AEItemKey key = this.markerKey(FakeCraftingConverterBlockEntity.outputMarkerSlot(entry.getKey()));
            if (key != null) {
                result.add(new GenericStack(key, entry.getValue()));
            }
        }
        return result;
    }

    /* 任务真实产物齐备后，从下槽取出输出纸并经样板供应器返回库存回传。 */
    public boolean returnTaskOutputs(UUID taskId, ReceiveInventory receiveInventory) {
        Map<Integer, Long> rows = this.taskTickets.get(taskId);
        if (rows == null) {
            return true;
        }
        if (!this.returningTasks.add(taskId)) {
            return false;
        }
        try {
            for (int row = 0; row < COLUMN_COUNT; row++) {
                long amount = rows.getOrDefault(row, 0L);
                if (amount <= 0) {
                    continue;
                }
                int stockSlot = FakeCraftingConverterBlockEntity.outputStockSlot(row);
                AEItemKey output = this.taskOutputKey(row);
                if (output == null) {
                    continue;
                }
                ItemStack held = this.takeStock(stockSlot, output,
                        Math.min(amount, this.pendingOutputAmounts[row]));
                if (held.isEmpty()) {
                    continue;
                }
                int ticketRow = row;
                boolean[] settled = {false};
                try {
                    receiveInventory.insertUntracked(output, held.getCount(), inserted -> {
                        held.shrink((int) inserted);
                        this.restoreStock(stockSlot, held);
                        this.pendingOutputAmounts[ticketRow] -= inserted;
                        long remaining = amount - inserted;
                        if (remaining == 0) {
                            rows.remove(ticketRow);
                        } else {
                            rows.put(ticketRow, remaining);
                        }
                        if (rows.isEmpty()) {
                            this.taskTickets.remove(taskId);
                        }
                        settled[0] = true;
                        this.markForSave();
                    });
                } finally {
                    // 插入未完成时归还本次取出的实物；刷新发生在结算之后，不能重复恢复。
                    if (!settled[0]) {
                        this.restoreStock(stockSlot, held);
                    }
                }
            }
        } finally {
            this.returningTasks.remove(taskId);
        }
        return !this.taskTickets.containsKey(taskId);
    }

    /* 只从现存库存取出实物，不按任务账目创建物品。 */
    private ItemStack takeStock(int slot, @Nullable AEItemKey key, long amount) {
        ItemStack current = this.stockItem(slot);
        if (key == null || amount <= 0 || current.isEmpty() || !key.matches(current)) {
            return ItemStack.EMPTY;
        }
        int taken = (int) Math.min(amount, current.getCount());
        ItemStack held = current.copyWithCount(taken);
        this.contents.setStack(slot, taken == current.getCount() ? null
                : new GenericStack(key, current.getCount() - taken));
        return held;
    }

    /* 仅在操作实物时构造临时堆叠，存档和菜单同步始终使用 AE2 包装数量。 */
    private ItemStack stockItem(int slot) {
        GenericStack stack = this.contents.getStack(slot);
        return stack != null && stack.what() instanceof AEItemKey key
                ? key.toStack((int) stack.amount()) : ItemStack.EMPTY;
    }

    /* 玩家和物品管道每次最多取出正常堆叠，任务占用的下槽禁止提取。 */
    private ItemStack extractPlayerStock(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot >= COLUMN_COUNT && this.pendingOutputAmounts[slot - COLUMN_COUNT] > 0) {
            return ItemStack.EMPTY;
        }
        GenericStack stack = this.contents.getStack(slot);
        if (stack == null || !(stack.what() instanceof AEItemKey key)) {
            return ItemStack.EMPTY;
        }
        int extracted = (int) Math.min(stack.amount(), Math.min(amount, key.getMaxStackSize()));
        return simulate ? key.toStack(extracted) : this.takeStock(slot, key, extracted);
    }

    /* 将暂存实物放回原槽，调用方必须事先保留其容量。 */
    private void restoreStock(int slot, ItemStack held) {
        if (held.isEmpty()) {
            return;
        }
        ItemStack current = this.stockItem(slot);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, held)) {
            throw new IllegalStateException("Converter ticket slot changed during transfer");
        }
        AEItemKey key = AEItemKey.of(held);
        if (key == null || held.getCount() + current.getCount() > FakeCraftingConverterBlockEntity.SLOT_CAPACITY) {
            throw new IllegalStateException("Converter ticket slot capacity exceeded");
        }
        this.contents.setStack(slot, new GenericStack(key, held.getCount() + current.getCount()));
    }

    private @Nullable AEItemKey taskOutputKey(int row) {
        AEItemKey input = this.markerKey(FakeCraftingConverterBlockEntity.inputMarkerSlot(row));
        AEItemKey output = this.markerKey(FakeCraftingConverterBlockEntity.outputMarkerSlot(row));
        if (input == null || output == null) {
            return null;
        }
        AEItemKey renamed = AEItemKey.of(FakeCraftingConverterBlockEntity.renameTicket(input.toStack(), output));
        return output.equals(renamed) ? renamed : null;
    }

    private int availableSpace(int slot, AEItemKey key) {
        AEKey current = this.contents.getKey(slot);
        if (current != null && !key.equals(current)) {
            return 0;
        }
        // 返回期间下槽已取出的实物仍然占用容量，防止回调接单挤占回滚位置。
        long amount = this.contents.getAmount(slot);
        long occupied = slot < COLUMN_COUNT ? amount
                : Math.max(amount, this.pendingOutputAmounts[slot - COLUMN_COUNT]);
        return (int) Math.max(0, FakeCraftingConverterBlockEntity.SLOT_CAPACITY - occupied);
    }

    /* 外部存入先改为上槽标记名称，再按改名后的键判断堆叠和容量。 */
    public long insertExternal(int row, AEKey key, long amount, Actionable mode) {
        if (row < 0 || row >= COLUMN_COUNT || !(key instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }
        AEItemKey input = this.markerKey(FakeCraftingConverterBlockEntity.inputMarkerSlot(row));
        AEItemKey output = this.markerKey(FakeCraftingConverterBlockEntity.outputMarkerSlot(row));
        if (input == null || (!key.equals(input) && !key.equals(output))) {
            return 0;
        }
        ItemStack renamed = FakeCraftingConverterBlockEntity.renameTicket(itemKey.toStack(), input);
        AEItemKey renamedKey = AEItemKey.of(renamed);
        if (!input.equals(renamedKey)) {
            return 0;
        }
        int stockSlot = FakeCraftingConverterBlockEntity.inputStockSlot(row);
        int inserted = (int) Math.min(amount, this.availableSpace(stockSlot, input));
        if (mode == Actionable.MODULATE && inserted > 0) {
            this.restoreStock(stockSlot, renamed.copyWithCount(inserted));
        }
        return inserted;
    }

    public boolean hasTaskTickets(UUID taskId) {
        return this.taskTickets.containsKey(taskId);
    }

    /* 只移除本任务已经交给转换器的数量，不误删同键的普通材料。 */
    public KeyCounter[] filterTaskInputs(UUID taskId, KeyCounter[] inputs) {
        Map<Integer, Long> rows = this.taskTickets.get(taskId);
        if (rows == null) {
            return inputs;
        }
        KeyCounter tickets = new KeyCounter();
        for (Map.Entry<Integer, Long> row : rows.entrySet()) {
            AEItemKey key = this.markerKey(FakeCraftingConverterBlockEntity.inputMarkerSlot(row.getKey()));
            if (key != null) {
                tickets.add(key, row.getValue());
            }
        }
        KeyCounter[] filtered = new KeyCounter[inputs.length];
        for (int slot = 0; slot < inputs.length; slot++) {
            filtered[slot] = new KeyCounter();
            if (inputs[slot] == null) {
                continue;
            }
            for (Map.Entry<AEKey, Long> input : inputs[slot]) {
                long removed = Math.min(tickets.get(input.getKey()), input.getValue());
                tickets.remove(input.getKey(), removed);
                if (input.getValue() > removed) {
                    filtered[slot].add(input.getKey(), input.getValue() - removed);
                }
            }
        }
        return filtered;
    }

    private @Nullable AEItemKey markerKey(int slot) {
        GenericStack marker = this.markers.getStack(slot);
        return marker != null && marker.what() instanceof AEItemKey itemKey ? itemKey : null;
    }

    private void markForSave() {
        this.setChanged();
        Level level = this.getLevel();
        if (level != null && !level.isClientSide()) {
            this.markForClientUpdate();
        }
    }

    private void gridChanged() {
        IGrid grid = this.getMainNode().getGrid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
    }

    public record TicketPlan(KeyCounter[] forwardedInputs, Map<Integer, Long> rows, boolean valid) {
        public boolean hasTickets() {
            return !this.rows.isEmpty();
        }
    }

    private final class ConverterStorage implements MEStorage, GenericInternalInventory {
        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!(key instanceof AEItemKey) || amount <= 0) {
                return 0;
            }
            long accepted = 0;
            for (int row = 0; row < COLUMN_COUNT && accepted < amount; row++) {
                accepted += this.insert(row, key, amount - accepted, mode);
            }
            return accepted;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!(key instanceof AEItemKey) || amount <= 0) {
                return 0;
            }
            long extracted = 0;
            for (int row = 0; row < COLUMN_COUNT && extracted < amount; row++) {
                extracted += this.extract(row, key, amount - extracted, mode);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (int row = 0; row < COLUMN_COUNT; row++) {
                GenericStack stack = this.getStack(row);
                if (stack != null) {
                    out.add(stack.what(), stack.amount());
                }
            }
        }

        @Override
        public Component getDescription() {
            return Component.translatable("block.ae2_batchcraft.fake_crafting_converter");
        }

        @Override
        public int size() {
            return COLUMN_COUNT;
        }

        @Override
        public @Nullable GenericStack getStack(int slot) {
            AEKey key = this.getKey(slot);
            return key == null ? null : new GenericStack(key, this.getAmount(slot));
        }

        @Override
        public @Nullable AEKey getKey(int slot) {
            if (slot < 0 || slot >= COLUMN_COUNT) {
                return null;
            }
            return FakeCraftingConverterBlockEntity.this.contents.getKey(slot);
        }

        @Override
        public long getAmount(int slot) {
            return slot < 0 || slot >= COLUMN_COUNT || this.getKey(slot) == null
                    ? 0
                    : FakeCraftingConverterBlockEntity.this.contents.getAmount(slot);
        }

        @Override
        public long getMaxAmount(AEKey key) {
            return key instanceof AEItemKey ? FakeCraftingConverterBlockEntity.SLOT_CAPACITY : 0;
        }

        @Override
        public long getCapacity(AEKeyType type) {
            return type == AEKeyType.items() ? FakeCraftingConverterBlockEntity.SLOT_CAPACITY : 0;
        }

        @Override
        public boolean canInsert() {
            return true;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public void setStack(int slot, @Nullable GenericStack stack) {
        }

        @Override
        public boolean isSupportedType(AEKeyType type) {
            return type == AEKeyType.items();
        }

        @Override
        public boolean isAllowedIn(int slot, AEKey key) {
            return this.insert(slot, key, 1, Actionable.SIMULATE) > 0;
        }

        @Override
        public long insert(int slot, AEKey key, long amount, Actionable mode) {
            return FakeCraftingConverterBlockEntity.this.insertExternal(slot, key, amount, mode);
        }

        @Override
        public long extract(int slot, AEKey key, long amount, Actionable mode) {
            if (slot < 0 || slot >= COLUMN_COUNT || !(key instanceof AEItemKey itemKey)
                    || !key.equals(this.getKey(slot)) || amount <= 0) {
                return 0;
            }
            if (mode == Actionable.SIMULATE) {
                return Math.min(amount, this.getAmount(slot));
            }
            return FakeCraftingConverterBlockEntity.this.takeStock(slot, itemKey, amount).getCount();
        }

        @Override
        public void beginBatch() {
        }

        @Override
        public void endBatch() {
        }

        @Override
        public void endBatchSuppressed() {
        }

        @Override
        public void onChange() {
        }

        @Override
        public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
            for (int row = 0; row < COLUMN_COUNT; row++) {
                if (this.isAllowedIn(row, key)) {
                    return true;
                }
            }
            return false;
        }
    }
}
