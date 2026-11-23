package org.ae2bc.pattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.guisync.PacketWritable;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.encoding.EncodingMode;
import appeng.util.ConfigInventory;
import lombok.Getter;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.constant.Pp2mpEncodingTermKeys;
import org.ae2bc.core.pattern.DirectionHelper;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.core.pattern.PatternBatchCount;
import org.ae2bc.registry.component.ComponentRegistry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Pp2mpEncodingTermMenu extends PatternEncodingTermMenu {
    public static final MenuType<Pp2mpEncodingTermMenu> TYPE = MenuTypeBuilder
            .create(Pp2mpEncodingTermMenu::new, IPatternTerminalMenuHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pp2mp_encoding_term"));
    public static final SlotSemantic REAL_OUTPUT_MARKER = SlotSemantics.register("AE2BC_REAL_OUTPUT_MARKER", false);
    private final ConfigInventory realOutputMarker = ConfigInventory.configStacks(1).allowOverstacking(true).build();
    private final @Getter FakeSlot realOutputMarkerSlot;
    private MaterialOutputAttr outputAttr;
    @GuiSync(30012)
    private OutputAttrSync syncedOutputAttr;

    @Getter
    @GuiSync(30010)
    private long batchCount;
    @GuiSync(30011)
    private long maximumBatchCount = PatternBatchCount.DEFAULT;

    public Pp2mpEncodingTermMenu(int id, Inventory inventory, IPatternTerminalMenuHost host) {
        super(TYPE, id, inventory, host, true);
        this.mode = EncodingMode.PROCESSING;
        this.registerClientAction(Pp2mpEncodingTermKeys.PATTERN_OUTPUT_ATTR, int[].class, this::setOutputConfigFromClient);
        this.registerClientAction(Pp2mpEncodingTermKeys.BATCH_COUNT, Long.class, this::setBatchCountFromClient);
        this.registerClientAction(Pp2mpEncodingTermKeys.RESET_RECIPE_CONFIG, this::resetRecipeConfiguration);
        ((RestrictedInputSlot) this.getSlots(SlotSemantics.BLANK_PATTERN).getFirst()).setAllowEdit(false);
        ((RestrictedInputSlot) this.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst()).setAllowEdit(false);
        this.realOutputMarkerSlot = new FakeSlot(this.realOutputMarker.createMenuWrapper(), 0);
        this.addSlot(this.realOutputMarkerSlot, Pp2mpEncodingTermMenu.REAL_OUTPUT_MARKER);
        this.realOutputMarkerSlot.setEmptyTooltip(() -> List.of(
                Component.translatable("gui.ae2_batchcraft.pattern.real_output_marker"),
                Component.translatable("gui.ae2_batchcraft.pattern.real_output_marker.hint")));

        ItemStack existing = host instanceof Pp2mpPatternMenuHost patternHost ? patternHost.getItemStack() : ItemStack.EMPTY;
        MaterialOutputAttr config = existing.get(ComponentRegistry.MATERIAL_OUTPUT_CONFIG);
        this.outputAttr = config == null ? MaterialOutputAttr.EMPTY : config;
        Long existingBatchCount = existing.get(ComponentRegistry.PATTERN_BATCH_COUNT);
        this.batchCount = existingBatchCount == null ? PatternBatchCount.DEFAULT : existingBatchCount;
        this.realOutputMarker.setStack(0, existing.get(ComponentRegistry.REAL_OUTPUT_MARKER));
        ItemStack vanillaPattern = Pp2mpPatternItem.toVanillaPattern(existing);
        if (!vanillaPattern.isEmpty()) {
            host.getLogic().getEncodedPatternInv().setItemDirect(0, vanillaPattern);
        }
        this.syncedOutputAttr = new OutputAttrSync(this.outputAttr);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            this.normalizeOutputModes();
            if (this.outputAttr != null) {
                this.syncedOutputAttr = new OutputAttrSync(this.outputAttr);
            }
            this.maximumBatchCount = this.calcMaxBatchCount();
            this.batchCount = PatternBatchCount.validate(this.batchCount, this.maximumBatchCount).value();
        }
        super.broadcastChanges();
    }

    @Override
    public void onServerDataSync(@NonNull ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        if (updatedFields.contains((short) 30012)) {
            this.outputAttr = this.syncedOutputAttr.value();
        }
    }

    /* 槽位材料变化后立即清除不兼容方式，空槽仅保留普通、掉落和放置。 */
    @Override
    public void onSlotChange(@NonNull Slot slot) {
        super.onSlotChange(slot);
        if (this.isServerSide() && this.outputAttr != null) {
            int index = this.getSlots(SlotSemantics.PROCESSING_INPUTS).indexOf(slot);
            if (index >= 0 && !this.supportsOutputMode(index, this.outputAttr.mode(index))) {
                this.outputAttr = this.outputAttr.withMode(index, MaterialOutputMode.NORMAL);
            }
        }
    }

    /* 编码和同步均校验完整槽位，兼容直接更新库存及旧样板遗留的非法配置。 */
    private void normalizeOutputModes() {
        if (this.outputAttr == null) {
            return;
        }
        List<Slot> inputs = this.getSlots(SlotSemantics.PROCESSING_INPUTS);
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (!this.supportsOutputMode(slot, this.outputAttr.mode(slot))) {
                this.outputAttr = this.outputAttr.withMode(slot, MaterialOutputMode.NORMAL);
            }
        }
    }

    @Override
    public void setMode(EncodingMode mode) {
        if (mode != EncodingMode.PROCESSING) {
            return;
        }
        super.setMode(EncodingMode.PROCESSING);
    }

    /* 快捷加载新配方前清除旧材料配置与批次数量，并通过菜单动作同步服务端。 */
    public void resetRecipeConfiguration() {
        this.outputAttr = MaterialOutputAttr.EMPTY;
        this.batchCount = PatternBatchCount.DEFAULT;
        this.realOutputMarker.clear();
        if (this.isClientSide()) {
            this.sendClientAction(Pp2mpEncodingTermKeys.RESET_RECIPE_CONFIG);
        }
    }

    @Override
    public void clear() {
        this.resetRecipeConfiguration();
        super.clear();
    }

    /* 独立虚拟槽复用 AE2 的数量编辑与流体标记，不参与普通产物轮换。 */
    @Override
    public boolean isProcessingPatternSlot(@Nullable Slot slot) {
        return slot != null && (slot == this.realOutputMarkerSlot
                ? this.hasFakeInputs() : super.isProcessingPatternSlot(slot));
    }

    public boolean hasFakeInputs() {
        if (this.outputAttr == null || this.mode != EncodingMode.PROCESSING) {
            return false;
        }
        List<Slot> inputs = this.getSlots(SlotSemantics.PROCESSING_INPUTS);
        for (int index = 0; index < inputs.size(); index++) {
            if (inputs.get(index).hasItem()
                    && this.outputAttr.mode(index) == MaterialOutputMode.FAKE_CRAFTING_CONVERTER) {
                return true;
            }
        }
        return false;
    }

    public @Nullable GenericStack getRealOutputMarker() {
        return this.realOutputMarker.getStack(0);
    }

    @Override
    public void setFilter(int slotIndex, @NonNull ItemStack item) {
        if (slotIndex == this.realOutputMarkerSlot.index && !this.hasFakeInputs()) {
            return;
        }
        super.setFilter(slotIndex, item);
    }

    @Override
    public void doAction(@NonNull ServerPlayer player, @NonNull InventoryAction action, int slot, long id) {
        if (slot == this.realOutputMarkerSlot.index && !this.hasFakeInputs()) {
            return;
        }
        super.doAction(player, action, slot, id);
    }

    /* 先构建并校验候选样板，全部通过后才扣减空白样板或覆盖原样板。 */
    @Override
    public void encode() {
        if (this.isClientSide()) {
            super.encode();
            return;
        }

        if (!(this.getHost() instanceof Pp2mpPatternMenuHost host)) {
            return;
        }
        this.normalizeOutputModes();
        List<GenericStack> inputs = Pp2mpEncodingTermMenu.stacks(host.getLogic().getEncodedInputInv());
        List<GenericStack> outputs = Pp2mpEncodingTermMenu.stacks(host.getLogic().getEncodedOutputInv());
        String error = this.validateEncoding(inputs, outputs);
        if (error != null) {
            this.showEncodingError(error);
            return;
        }
        ItemStack hostedBeforeEncode = host.getItemStack();
        if (hostedBeforeEncode.isEmpty()) {
            return;
        }
        boolean wasBlank = !Pp2mpPatternItem.hasPatternData(hostedBeforeEncode);
        int originalCount = hostedBeforeEncode.getCount();
        ItemStack result;
        try {
            ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
            result = Pp2mpPatternItem.encodePattern(encoded, this.outputAttr);
            GenericStack marker = this.hasFakeInputs() ? this.getRealOutputMarker() : null;
            if (marker != null) {
                result.set(ComponentRegistry.REAL_OUTPUT_MARKER, marker);
            }
            if (this.batchCount != PatternBatchCount.DEFAULT) {
                result.set(ComponentRegistry.PATTERN_BATCH_COUNT, this.batchCount);
            }
            AEItemKey definition = AEItemKey.of(result);
            if (definition == null) {
                this.showEncodingError("invalid");
                return;
            }
            Pp2mpTaskPlan.singleDefinition(new Pp2mpPatternDetails(definition, this.getPlayer().level()));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            this.showEncodingError("invalid");
            return;
        }

        Slot outputSlot = this.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst();
        if (!wasBlank || originalCount == 1) {
            ItemStack hosted = host.getItemStack();
            hosted.setCount(1);
            hosted.applyComponents(result.getComponentsPatch());
            MaterialOutputAttr encodedConfig = result.get(ComponentRegistry.MATERIAL_OUTPUT_CONFIG);
            if (encodedConfig == null) {
                hosted.remove(ComponentRegistry.MATERIAL_OUTPUT_CONFIG);
            } else {
                hosted.set(ComponentRegistry.MATERIAL_OUTPUT_CONFIG, encodedConfig);
            }
            if (this.batchCount == PatternBatchCount.DEFAULT) {
                hosted.remove(ComponentRegistry.PATTERN_BATCH_COUNT);
            } else {
                hosted.set(ComponentRegistry.PATTERN_BATCH_COUNT, this.batchCount);
            }
            if (!result.has(ComponentRegistry.REAL_OUTPUT_MARKER)) {
                hosted.remove(ComponentRegistry.REAL_OUTPUT_MARKER);
            }
            outputSlot.set(ItemStack.EMPTY);
            return;
        }

        host.getItemStack().shrink(1);
        outputSlot.set(ItemStack.EMPTY);

        ItemStack remainder = result.copy();
        this.getPlayer().getInventory().add(remainder);
        if (!remainder.isEmpty()) {
            this.getPlayer().drop(remainder, false);
        }
    }

    private static List<GenericStack> stacks(ConfigInventory inventory) {
        List<GenericStack> stacks = new ArrayList<>(inventory.size());
        for (int slot = 0; slot < inventory.size(); slot++) {
            stacks.add(inventory.getStack(slot));
        }
        return stacks;
    }

    /* 不查询离线编码时不可用的转换器、机器或网络，仅校验样板本身。 */
    private @Nullable String validateEncoding(List<GenericStack> inputs, List<GenericStack> outputs) {
        if (inputs.stream().allMatch(stack -> stack == null)) {
            return "missing_input";
        }
        GenericStack firstOutput = outputs.getFirst();
        if (firstOutput == null) {
            return this.hasFakeInputs() ? "missing_fake_output" : "missing_output";
        }
        if (inputs.stream().anyMatch(stack -> stack != null && stack.amount() <= 0)
                || outputs.stream().anyMatch(stack -> stack != null && stack.amount() <= 0)) {
            return "invalid";
        }
        if (this.hasFakeInputs()) {
            GenericStack marker = this.getRealOutputMarker();
            if (marker == null) {
                return "missing_marker";
            }
            if (marker.amount() <= 0) {
                return "invalid_marker";
            }
            if (marker.what().equals(firstOutput.what())) {
                return "marker_is_fake_output";
            }
        }
        return this.validateBatchCount(this.batchCount).result() == PatternBatchCount.Result.VALID
                ? null : "invalid_batch";
    }

    private void showEncodingError(String error) {
        this.getPlayer().displayClientMessage(
                Component.translatable("message.ae2_batchcraft.pattern.encode." + error)
                        .withStyle(ChatFormatting.RED), false);
    }

    public MaterialOutputMode mode(int slot) {
        return this.outputAttr.mode(slot);
    }

    public long getMaximumBatchCount() {
        return Math.max(PatternBatchCount.DEFAULT, this.maximumBatchCount);
    }

    public void setBatchCount(long requested) {
        PatternBatchCount.Validation validation = this.validateBatchCount(requested);
        this.batchCount = validation.value();
        if (this.isClientSide()) {
            this.sendClientAction(Pp2mpEncodingTermKeys.BATCH_COUNT, this.batchCount);
        }
    }

    private void setBatchCountFromClient(Long requested) {
        if (this.isClientSide() || requested == null || this.mode != EncodingMode.PROCESSING) {
            return;
        }
        this.maximumBatchCount = this.calcMaxBatchCount();
        this.batchCount = PatternBatchCount.validate(requested, this.maximumBatchCount).value();
    }

    private PatternBatchCount.Validation validateBatchCount(long requested) {
        return PatternBatchCount.validate(requested, this.calcMaxBatchCount());
    }

    /* 直接读取编码库存数量，避免槽位显示栈转换和临时数组复制。 */
    private long calcMaxBatchCount() {
        if (this.mode != EncodingMode.PROCESSING || !(this.getHost() instanceof IPatternTerminalMenuHost host)) {
            return PatternBatchCount.DEFAULT;
        }
        ConfigInventory inputs = host.getLogic().getEncodedInputInv();
        ConfigInventory outputs = host.getLogic().getEncodedOutputInv();
        long[] amounts = new long[inputs.size() + outputs.size() + 1];
        for (int slot = 0; slot < inputs.size(); slot++) {
            amounts[slot] = inputs.getAmount(slot);
        }
        for (int slot = 0; slot < outputs.size(); slot++) {
            amounts[inputs.size() + slot] = outputs.getAmount(slot);
        }
        GenericStack marker = this.hasFakeInputs() ? this.getRealOutputMarker() : null;
        if (marker != null && marker.amount() > 0) {
            amounts[amounts.length - 1] = marker.amount();
        }
        return PatternBatchCount.maximum(amounts);
    }

    public void setOutputMode(int slot, MaterialOutputMode mode) {
        Direction direction = mode == MaterialOutputMode.FAKE_CRAFTING_CONVERTER
                ? null
                : this.outputAttr.direction(slot);
        this.setOutputAttr(slot, direction, mode);
    }

    public void outputDirection(int slot, @Nullable Direction direction) {
        if (this.mode(slot) == MaterialOutputMode.FAKE_CRAFTING_CONVERTER) {
            return;
        }
        this.setOutputAttr(slot, direction, this.outputAttr.mode(slot));
    }

    public @Nullable Direction outputDirection(int slot) {
        return this.outputAttr.direction(slot);
    }

    public boolean supportsOutputMode(int slot, MaterialOutputMode mode) {
        if (mode == null || DirectionHelper.invalidSlot(slot)
                || slot >= this.getSlots(SlotSemantics.PROCESSING_INPUTS).size()) {
            return false;
        }
        GenericStack input = GenericStack.fromItemStack(
                this.getSlots(SlotSemantics.PROCESSING_INPUTS).get(slot).getItem());
        return input == null ? mode != MaterialOutputMode.FAKE_CRAFTING_CONVERTER
                : this.modeSupports(mode, input);
    }

    public void setOutputAttr(int slot, @Nullable Direction direction, MaterialOutputMode mode) {
        if (!this.supportsOutputMode(slot, mode)) {
            return;
        }
        Direction safeDirection = mode == MaterialOutputMode.FAKE_CRAFTING_CONVERTER ? null : direction;
        this.outputAttr = this.outputAttr.withDirection(slot, safeDirection).withMode(slot, mode);
        if (this.isClientSide()) {
            this.sendClientAction(Pp2mpEncodingTermKeys.PATTERN_OUTPUT_ATTR, new int[]{
                    slot,
                    safeDirection == null ? 0 : safeDirection.ordinal() + 1,
                    mode.id()
            });
        }
    }

    private void setOutputConfigFromClient(int[] action) {
        if (this.isClientSide() || action == null || action.length != 3 || this.mode != EncodingMode.PROCESSING
                || DirectionHelper.invalidSlot(action[0])
                || action[0] >= this.getSlots(SlotSemantics.PROCESSING_INPUTS).size()) {
            return;
        }
        MaterialOutputMode mode = MaterialOutputMode.fromId(action[2]);
        if (mode.id() != action[2] || action[1] < 0 || action[1] > Direction.values().length) {
            return;
        }
        Direction direction = action[1] == 0 ? null : Direction.values()[action[1] - 1];
        this.setOutputAttr(action[0], direction, mode);
    }

    private boolean modeSupports(MaterialOutputMode mode, GenericStack input) {
        return switch (mode) {
            case NORMAL -> true;
            case DROP, FAKE_CRAFTING_CONVERTER -> input.what() instanceof AEItemKey;
            case PLACE ->
                    input.what() instanceof AEFluidKey || (input.what() instanceof AEItemKey itemKey && itemKey.getItem() instanceof BlockItem);
        };
    }

    /* AE2 的自定义同步字段要求使用 record，直接复用现有配置编解码器。 */
    public record OutputAttrSync(MaterialOutputAttr value) implements PacketWritable {
        public OutputAttrSync(RegistryFriendlyByteBuf data) {
            this(MaterialOutputAttr.STREAM_CODEC.decode(data));
        }

        @Override
        public void writeToPacket(@NonNull RegistryFriendlyByteBuf data) {
            MaterialOutputAttr.STREAM_CODEC.encode(data, this.value);
        }
    }
}
