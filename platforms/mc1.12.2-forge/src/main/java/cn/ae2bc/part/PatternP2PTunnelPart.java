package cn.ae2bc.part;

import cn.ae2bc.logic.InterfaceReturnFlusher;
import cn.ae2bc.logic.EndpointProductExtractionSettings;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.pattern.PatternEncodingState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import io.netty.buffer.ByteBuf;

import appeng.api.config.PowerUnits;
import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.parts.PartModel;
import appeng.me.GridAccessException;
import appeng.parts.p2p.PartP2PTunnel;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.core.schedule.ExtractionDeadlineGate;
import cn.ae2bc.core.energy.EnergyEndpoint;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.pattern.InputDirectionCodec;
import cn.ae2bc.pattern.MaterialOutputConfigCodec;
import cn.ae2bc.pattern.MaterialOutputForm;
import cn.ae2bc.pattern.PatternInputSlotAllocator;
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.logic.ReturnMode;
import cn.ae2bc.logic.ReturnBatchTracker;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.registry.ModContent;

/** rv6 P2P endpoint. The CableBus ICraftingMachine bridge is intentionally separate from this logic. */
public final class PatternP2PTunnelPart extends PartP2PTunnel<PatternP2PTunnelPart>
        implements ICraftingMachine, IGridTickable, IItemHandler, EnergyEndpoint {
    private static final String RETURN_BATCH_MODE = "Ae2bcReturnBatchMode";
    private static final String RETURN_BATCH_PATTERN = "Ae2bcReturnBatchPattern";
    private static final String RETURN_BATCH_TASK_COUNT = "Ae2bcReturnBatchTaskCount";
    private static final String RETURN_BATCH_OUTPUTS = "Ae2bcReturnBatchOutputs";
    private static final String RETURN_BATCH_PRIMARY = "Ae2bcReturnBatchPrimary";
    private static final String RETURN_BATCH_EXPECTED_PRIMARY = "Ae2bcReturnBatchExpectedPrimary";
    private final boolean output;
    private EndpointProductExtractionSettings productExtractionSettings =
            EndpointProductExtractionSettings.DEFAULT;
    private long unitConfigurationRevision;
    private ReturnMode returnMode = ReturnMode.UNBLOCKED;
    private boolean breakRecovery = true;
    private RedstoneOutputMode redstoneMode = RedstoneOutputMode.SINGLE_TRIGGER;
    private int redstoneStrength = 15;
    private int pulseWidthTicks = 2;
    private int pulsePeriodTicks = 20;
    private boolean syncInputSettings = true;
    private final ExtractionDeadlineGate extractionDeadline = new ExtractionDeadlineGate();
    private final ExtractionDeadlineGate returnRecoveryDeadline = new ExtractionDeadlineGate();
    private ItemStack blockedReturnProbe = ItemStack.EMPTY;
    private int roundRobinCursor;
    private final ReturnBatchTracker<StackKey, StackKey> returnBatch =
            new ReturnBatchTracker<StackKey, StackKey>();
    public static final net.minecraft.util.ResourceLocation INPUT_MODEL_ID = new net.minecraft.util.ResourceLocation(
            "ae2_batchcraft", "part/p2p/pattern_p2p_tunnel_input");
    public static final net.minecraft.util.ResourceLocation OUTPUT_MODEL_ID = new net.minecraft.util.ResourceLocation(
            "ae2_batchcraft", "part/p2p/pattern_p2p_tunnel_output");
    private static final net.minecraft.util.ResourceLocation STATUS_OFF_MODEL_ID =
            new net.minecraft.util.ResourceLocation("appliedenergistics2", "part/p2p/p2p_tunnel_status_off");
    private static final net.minecraft.util.ResourceLocation STATUS_ON_MODEL_ID =
            new net.minecraft.util.ResourceLocation("appliedenergistics2", "part/p2p/p2p_tunnel_status_on");
    private static final net.minecraft.util.ResourceLocation STATUS_HAS_CHANNEL_MODEL_ID =
            new net.minecraft.util.ResourceLocation("appliedenergistics2", "part/p2p/p2p_tunnel_status_has_channel");
    private static final net.minecraft.util.ResourceLocation FREQUENCY_MODEL_ID =
            new net.minecraft.util.ResourceLocation("appliedenergistics2", "part/builtin/p2p_tunnel_frequency");
    private static final IPartModel INPUT_MODEL_OFF = new PartModel(
            STATUS_OFF_MODEL_ID, FREQUENCY_MODEL_ID, INPUT_MODEL_ID);
    private static final IPartModel INPUT_MODEL_ON = new PartModel(
            STATUS_ON_MODEL_ID, FREQUENCY_MODEL_ID, INPUT_MODEL_ID);
    private static final IPartModel INPUT_MODEL_HAS_CHANNEL = new PartModel(
            STATUS_HAS_CHANNEL_MODEL_ID, FREQUENCY_MODEL_ID, INPUT_MODEL_ID);
    private static final IPartModel OUTPUT_MODEL_OFF = new PartModel(
            STATUS_OFF_MODEL_ID, FREQUENCY_MODEL_ID, OUTPUT_MODEL_ID);
    private static final IPartModel OUTPUT_MODEL_ON = new PartModel(
            STATUS_ON_MODEL_ID, FREQUENCY_MODEL_ID, OUTPUT_MODEL_ID);
    private static final IPartModel OUTPUT_MODEL_HAS_CHANNEL = new PartModel(
            STATUS_HAS_CHANNEL_MODEL_ID, FREQUENCY_MODEL_ID, OUTPUT_MODEL_ID);

    public PatternP2PTunnelPart(ItemStack stack, boolean output) {
        super(stack);
        this.output = output;
        if (output) getProxy().setFlags();
    }

    @Override public boolean isOutput() { return output; }
    @Override
    public boolean onPartActivate(net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.util.EnumHand hand, net.minecraft.util.math.Vec3d hit) {
        if (handleMemoryCard(player, hand, false)) return true;
        // AE2 otherwise attunes this custom endpoint into a built-in P2P tunnel.
        if (appeng.api.AEApi.instance().registries().p2pTunnel()
                .getTunnelTypeByItem(player.getHeldItem(hand)) != null) return false;
        if (super.onPartActivate(player, hand, hit)) {
            return true;
        }
        if (!player.world.isRemote) {
            player.openGui(Ae2bcMod.INSTANCE,
                    Ae2bcMod.GUI_EXTRACTION_BASE + getSide().getFacing().ordinal(),
                    player.world, getTile().getPos().getX(), getTile().getPos().getY(), getTile().getPos().getZ());
        }
        return true;
    }

    @Override
    public boolean onPartShiftActivate(net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.util.EnumHand hand, net.minecraft.util.math.Vec3d hit) {
        return handleMemoryCard(player, hand, true) || super.onPartShiftActivate(player, hand, hit);
    }

    /** Loads old-version tunnel frequencies in place so an output item is never rebuilt as input. */
    private boolean handleMemoryCard(net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.util.EnumHand hand, boolean save) {
        ItemStack held = player.getHeldItem(hand);
        if (hand != net.minecraft.util.EnumHand.MAIN_HAND
                || !(held.getItem() instanceof IMemoryCard)) return false;
        if (player.world.isRemote) return true;

        IMemoryCard card = (IMemoryCard) held.getItem();
        try {
            if (save && !output) {
                short frequency = getFrequency();
                boolean generated = frequency == 0;
                if (generated) frequency = getProxy().getP2P().newFrequency();
                getProxy().getP2P().updateFreq(this, frequency);
                NBTTagCompound data = ModContent.INPUT.getDefaultInstance()
                        .writeToNBT(new NBTTagCompound());
                data.setShort("freq", frequency);
                card.setMemoryCardContents(held,
                        "item.ae2_batchcraft.pattern_p2p_tunnel_input", data);
                card.notifyUser(player, generated
                        ? MemoryCardMessages.SETTINGS_RESET : MemoryCardMessages.SETTINGS_SAVED);
                return true;
            }
            if (!save && output) {
                NBTTagCompound data = card.getData(held);
                if (!data.hasKey("freq") || !isSupportedMemoryCardSource(data)) {
                    card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
                    return true;
                }
                getProxy().getP2P().updateFreq(this, data.getShort("freq"));
                onTunnelNetworkChange();
                saveChanges();
                card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
                return true;
            }
        } catch (GridAccessException ignored) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }
        card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
        return true;
    }

    private static boolean isSupportedMemoryCardSource(NBTTagCompound data) {
        if (data.hasUniqueId("PatternP2PUnitId")) return true;
        ItemStack source = new ItemStack(data);
        return !source.isEmpty()
                && (source.getItem() == ModContent.INPUT || source.getItem() == ModContent.UNIT_MANAGER);
    }
    public static List<IPartModel> getModels() {
        return Arrays.asList(INPUT_MODEL_OFF, INPUT_MODEL_ON, INPUT_MODEL_HAS_CHANNEL,
                OUTPUT_MODEL_OFF, OUTPUT_MODEL_ON, OUTPUT_MODEL_HAS_CHANNEL);
    }
    @Override public IPartModel getStaticModels() {
        IPartModel off = output ? OUTPUT_MODEL_OFF : INPUT_MODEL_OFF;
        IPartModel on = output ? OUTPUT_MODEL_ON : INPUT_MODEL_ON;
        IPartModel hasChannel = output ? OUTPUT_MODEL_HAS_CHANNEL : INPUT_MODEL_HAS_CHANNEL;
        if (isPowered() && isActive()) return hasChannel;
        return isPowered() ? on : off;
    }
    @Override public void getBoxes(IPartCollisionHelper helper) { helper.addBox(2, 2, 2, 14, 14, 14); }
    @Override public boolean acceptsPlans() {
        return !output && isActive() && (!outputs().isEmpty() || hasUnitManagerForFrequency());
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails details, InventoryCrafting table, EnumFacing direction) {
        if (!acceptsPlans() || details == null || details.isCraftable() || table == null) return false;
        List<PatternP2PTunnelPart> outputs = outputs();
        List<PatternP2PUnitManagerPart> managers = unitManagersForFrequency();
        int endpointCount = outputs.size() + managers.size();
        if (endpointCount == 0) return false;
        long[] packed = PatternEncodingState.read(details.getPattern());
        long[] directions = MaterialOutputConfigCodec.directionWords(packed);
        List<RoutedInput> routedInputs = reconstructPatternInputs(details, table);
        if (routedInputs == null) routedInputs = collectAutomaticInputs(table);
        ManagerDispatch managerDispatch = managers.isEmpty()
                ? null : createManagerDispatch(details, routedInputs, packed);
        int inputCount = countInputs(table);
        final int outputCount = outputs.size();
        final List<RoutedInput> selectedInputs = routedInputs;
        int acceptedIndex = cn.ae2bc.core.dispatch.RoundRobinSelector.select(
                roundRobinCursor, endpointCount, candidateIndex -> {
            if (candidateIndex < outputCount) {
                return tryAcceptOutput(outputs.get(candidateIndex), details, table, selectedInputs, directions);
            }
            return managerDispatch != null && tryAcceptManager(
                    managers.get(candidateIndex - outputCount), managerDispatch, table);
        });
        if (acceptedIndex < 0) return false;
        int moved = inputCount - countInputs(table);
        if (moved <= 0) return false;
        roundRobinCursor = cn.ae2bc.core.dispatch.RoundRobinPolicy.advance(
                acceptedIndex, endpointCount);
        saveChanges();
        queueTunnelDrain(PowerUnits.RF, moved);
        return true;
    }

    private List<RoutedInput> reconstructPatternInputs(ICraftingPatternDetails details,
            InventoryCrafting table) {
        IAEItemStack[] patternInputs = details.getInputs();
        if (patternInputs == null) return null;
        long[] requiredAmounts = new long[patternInputs.length];
        for (int slot = 0; slot < patternInputs.length; slot++) {
            IAEItemStack input = patternInputs[slot];
            requiredAmounts[slot] = input == null ? 0 : Math.max(0, input.getStackSize());
        }
        int[] availableAmounts = new int[table.getSizeInventory()];
        for (int slot = 0; slot < availableAmounts.length; slot++) {
            ItemStack stack = table.getStackInSlot(slot);
            availableAmounts[slot] = stack.isEmpty() ? 0 : stack.getCount();
        }
        List<PatternInputSlotAllocator.Allocation> allocations =
                PatternInputSlotAllocator.allocate(requiredAmounts, availableAmounts,
                        (patternSlot, runtimeSlot) -> {
                    IAEItemStack expected = patternInputs[patternSlot];
                    ItemStack actual = table.getStackInSlot(runtimeSlot);
                    return expected != null && !actual.isEmpty() && expected.isSameType(actual);
                });
        if (allocations == null) return null;

        List<RoutedInput> result = new ArrayList<RoutedInput>(allocations.size());
        for (PatternInputSlotAllocator.Allocation allocation : allocations) {
            long allocatedAmount = allocation.getAmount();
            if (allocatedAmount <= 0 || allocatedAmount > Integer.MAX_VALUE) return null;
            ItemStack stack = table.getStackInSlot(allocation.getRuntimeSlot()).copy();
            if (allocatedAmount > stack.getCount()) return null;
            stack.setCount((int) allocatedAmount);
            result.add(new RoutedInput(
                    allocation.getPatternSlot(), allocation.getRuntimeSlot(), stack));
        }
        return result;
    }

    private static List<RoutedInput> collectAutomaticInputs(InventoryCrafting table) {
        List<RoutedInput> result = new ArrayList<RoutedInput>();
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            ItemStack stack = table.getStackInSlot(slot);
            if (!stack.isEmpty()) result.add(new RoutedInput(-1, slot, stack.copy()));
        }
        return result;
    }

    private ManagerDispatch createManagerDispatch(ICraftingPatternDetails details,
            List<RoutedInput> routedInputs, long[] packed) {
        List<ItemStack> inputs = new ArrayList<ItemStack>();
        List<UnitPortType> targetTypes = new ArrayList<UnitPortType>();
        long[] forms = MaterialOutputConfigCodec.formWords(packed);
        for (RoutedInput routed : routedInputs) {
            ItemStack stack = routed.stack;
            MaterialOutputForm form = routed.patternSlot < 0 ? MaterialOutputForm.NORMAL
                    : MaterialOutputForm.fromId(MaterialOutputConfigCodec.getOutputFormId(
                            forms, routed.patternSlot));
            if (!form.supports(stack)) return null;
            inputs.add(stack.copy());
            targetTypes.add(UnitPortType.forOutputFormId(form.getId()));
        }
        if (inputs.isEmpty()) return null;
        ItemStack primaryOutput = ItemStack.EMPTY;
        long primaryAmount = 0;
        List<ItemStack> outputIdentities = new ArrayList<ItemStack>();
        IAEItemStack[] declaredOutputs = details.getOutputs();
        if (declaredOutputs != null && declaredOutputs.length > 0) {
            for (IAEItemStack output : declaredOutputs) {
                if (output != null) outputIdentities.add(output.createItemStack());
            }
            if (declaredOutputs[0] != null) {
                primaryOutput = declaredOutputs[0].createItemStack();
                primaryAmount = declaredOutputs[0].getStackSize();
            }
        }
        return new ManagerDispatch(inputs, targetTypes, primaryOutput, primaryAmount, outputIdentities);
    }

    private boolean tryAcceptManager(PatternP2PUnitManagerPart manager, ManagerDispatch dispatch,
            InventoryCrafting table) {
        if (!manager.canAcceptTask()) return false;
        for (int i = 0; i < dispatch.inputs.size(); i++) {
            if (!manager.canAcceptInput(dispatch.inputs.get(i), dispatch.targetTypes.get(i))) return false;
        }
        if (!manager.acceptInputs(dispatch.inputs, dispatch.targetTypes, dispatch.primaryOutput,
                dispatch.primaryAmount, dispatch.outputIdentities)) return false;
        clearInputs(table);
        return true;
    }

    private boolean tryAcceptOutput(PatternP2PTunnelPart output, ICraftingPatternDetails details,
            InventoryCrafting table, List<RoutedInput> routedInputs, long[] directions) {
        if (!output.isOutput() || !output.isActive() || !output.canAcceptOutputTask(details)) return false;
        // Probe the complete task before committing any slot to this one machine.
        for (RoutedInput routed : routedInputs) {
            if (!output.adjacentInsert(
                    routed.stack, directions, routed.patternSlot, true).isEmpty()) return false;
        }
        OutputReturnMetadata metadata = outputReturnMetadata(details);
        if (!output.beginOutputTask(details, metadata)) return false;
        int moved = 0;
        for (RoutedInput routed : routedInputs) {
            ItemStack current = table.getStackInSlot(routed.runtimeSlot);
            if (current.isEmpty()) continue;
            ItemStack source = current.copy();
            source.setCount(Math.min(source.getCount(), routed.stack.getCount()));
            ItemStack remainder = output.adjacentInsert(
                    source, directions, routed.patternSlot, false);
            int inserted = source.getCount() - remainder.getCount();
            if (inserted <= 0) continue;
            ItemStack remainingInSlot = current.copy();
            remainingInSlot.shrink(inserted);
            table.setInventorySlotContents(routed.runtimeSlot,
                    remainingInSlot.getCount() <= 0 ? ItemStack.EMPTY : remainingInSlot);
            moved += inserted;
        }
        if (moved <= 0) {
            output.rollbackOutputTask(metadata.primaryAmount);
            return false;
        }
        output.saveChanges();
        return true;
    }

    private boolean canAcceptOutputTask(ICraftingPatternDetails details) {
        OutputReturnMetadata metadata = outputReturnMetadata(details);
        return returnBatch.canAccept(stackKey(details.getPattern()),
                metadata.outputs, metadata.primaryKey, metadata.primaryAmount);
    }

    private boolean beginOutputTask(ICraftingPatternDetails details, OutputReturnMetadata metadata) {
        return returnBatch.begin(stackKey(details.getPattern()),
                metadata.outputs, metadata.primaryKey, metadata.primaryAmount);
    }

    private void rollbackOutputTask(long primaryAmount) {
        returnBatch.rollback(primaryAmount);
        saveChanges();
    }

    private void clearOutputReturnBatch() {
        if (!returnBatch.isActive()) return;
        returnBatch.clear();
        saveChanges();
    }

    private ItemStack returnOutputProduct(ItemStack stack, boolean simulate) {
        if (!output || stack == null || stack.isEmpty()) return stack;
        long allowed = returnBatch.filter(stackKey(stack), stack.getCount(), getReturnMode());
        if (allowed <= 0) return stack;
        PatternP2PTunnelPart input = getInput();
        if (input == null) return stack;
        ItemStack offered = stack.copy();
        offered.setCount((int) Math.min(Integer.MAX_VALUE, allowed));
        ItemStack remainder = input.returnToAdjacent(offered, simulate);
        int accepted = offered.getCount() - remainder.getCount();
        if (!simulate && accepted > 0) {
            returnBatch.returned(stackKey(stack), accepted);
            saveChanges();
        }
        if (accepted <= 0) return stack;
        ItemStack result = stack.copy();
        result.shrink(accepted);
        return result.isEmpty() ? ItemStack.EMPTY : result;
    }

    private static OutputReturnMetadata outputReturnMetadata(ICraftingPatternDetails details) {
        Map<StackKey, Long> outputs = new LinkedHashMap<StackKey, Long>();
        StackKey primaryKey = null;
        long primaryAmount = 0;
        IAEItemStack[] declared = details.getOutputs();
        if (declared != null) {
            for (IAEItemStack output : declared) {
                if (output == null || output.getStackSize() <= 0) continue;
                StackKey key = stackKey(output.createItemStack());
                if (key == null) continue;
                Long previous = outputs.get(key);
                long amount = output.getStackSize();
                outputs.put(key, previous == null ? amount : saturatingAdd(previous.longValue(), amount));
                if (primaryKey == null) {
                    primaryKey = key;
                    primaryAmount = amount;
                }
            }
        }
        return new OutputReturnMetadata(outputs, primaryKey, primaryAmount);
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static StackKey stackKey(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : new StackKey(stack);
    }

    private void readOutputReturnBatch(NBTTagCompound tag) {
        returnBatch.clear();
        if (!tag.hasKey(RETURN_BATCH_TASK_COUNT)) return;
        Map<StackKey, Long> outputs = new LinkedHashMap<StackKey, Long>();
        NBTTagList list = tag.getTagList(RETURN_BATCH_OUTPUTS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            StackKey key = stackKey(new ItemStack(entry.getCompoundTag("Stack")));
            long amount = entry.getLong("Amount");
            if (key != null && amount > 0) outputs.put(key, amount);
        }
        returnBatch.load(stackKey(new ItemStack(tag.getCompoundTag(RETURN_BATCH_PATTERN))),
                tag.getInteger(RETURN_BATCH_TASK_COUNT), outputs,
                stackKey(new ItemStack(tag.getCompoundTag(RETURN_BATCH_PRIMARY))),
                tag.getLong(RETURN_BATCH_EXPECTED_PRIMARY));
    }

    private void writeOutputReturnBatch(NBTTagCompound tag) {
        if (!returnBatch.isActive()) {
            tag.removeTag(RETURN_BATCH_MODE);
            tag.removeTag(RETURN_BATCH_PATTERN);
            tag.removeTag(RETURN_BATCH_TASK_COUNT);
            tag.removeTag(RETURN_BATCH_OUTPUTS);
            tag.removeTag(RETURN_BATCH_PRIMARY);
            tag.removeTag(RETURN_BATCH_EXPECTED_PRIMARY);
            tag.removeTag("Ae2bcReturnBatchExpectedPrimaries");
            return;
        }
        tag.removeTag(RETURN_BATCH_MODE);
        tag.setTag(RETURN_BATCH_PATTERN, returnBatch.getPattern().toStack().writeToNBT(new NBTTagCompound()));
        tag.setInteger(RETURN_BATCH_TASK_COUNT, returnBatch.getTaskCount());
        NBTTagList outputs = new NBTTagList();
        for (Map.Entry<StackKey, Long> output : returnBatch.getDeclaredOutputs().entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag("Stack", output.getKey().toStack().writeToNBT(new NBTTagCompound()));
            entry.setLong("Amount", output.getValue().longValue());
            outputs.appendTag(entry);
        }
        tag.setTag(RETURN_BATCH_OUTPUTS, outputs);
        tag.setTag(RETURN_BATCH_PRIMARY, returnBatch.getPrimaryKey().toStack().writeToNBT(new NBTTagCompound()));
        tag.setLong(RETURN_BATCH_EXPECTED_PRIMARY, returnBatch.getExpectedPrimary());
        tag.removeTag("Ae2bcReturnBatchExpectedPrimaries");
    }

    private static int countInputs(InventoryCrafting table) {
        int count = 0;
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            count += table.getStackInSlot(slot).getCount();
        }
        return count;
    }

    private static void clearInputs(InventoryCrafting table) {
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            table.setInventorySlotContents(slot, ItemStack.EMPTY);
        }
    }

    private List<PatternP2PTunnelPart> outputs() {
        List<PatternP2PTunnelPart> result = new ArrayList<PatternP2PTunnelPart>();
        try {
            for (PatternP2PTunnelPart part : getOutputs()) {
                if (part.isOutput()) result.add(part);
            }
        } catch (GridAccessException ignored) {
        }
        Collections.sort(result, OUTPUT_ENDPOINT_ORDER);
        return result;
    }

    private static final Comparator<PatternP2PTunnelPart> OUTPUT_ENDPOINT_ORDER =
            new Comparator<PatternP2PTunnelPart>() {
                @Override
                public int compare(PatternP2PTunnelPart left, PatternP2PTunnelPart right) {
                    int comparison = Integer.compare(dimension(left), dimension(right));
                    if (comparison != 0) return comparison;
                    comparison = Integer.compare(left.getTile().getPos().getX(), right.getTile().getPos().getX());
                    if (comparison != 0) return comparison;
                    comparison = Integer.compare(left.getTile().getPos().getY(), right.getTile().getPos().getY());
                    if (comparison != 0) return comparison;
                    comparison = Integer.compare(left.getTile().getPos().getZ(), right.getTile().getPos().getZ());
                    if (comparison != 0) return comparison;
                    return Integer.compare(left.getSide().getFacing().ordinal(), right.getSide().getFacing().ordinal());
                }

                private int dimension(PatternP2PTunnelPart part) {
                    return part.getTile().getWorld() == null
                            ? Integer.MIN_VALUE : part.getTile().getWorld().provider.getDimension();
                }
            };

    @Override
    public void onTunnelConfigChange() {
        super.onTunnelConfigChange();
        PatternP2PTopologyGridService.invalidate(getGridNode());
        refreshExtractionEndpoints();
    }

    @Override
    public void onTunnelNetworkChange() {
        super.onTunnelNetworkChange();
        PatternP2PTopologyGridService.invalidate(getGridNode());
        refreshExtractionEndpoints();
    }

    private void refreshExtractionEndpoints() {
        if (getTile() == null || getTile().getWorld() == null || getTile().getWorld().isRemote) return;
        extractionDeadline.wake();
        if (output) {
            wakeSelf();
        } else {
            synchronizeUnitManagers();
            wakeOutputs();
        }
    }

    private boolean hasUnitManagerForFrequency() { return !unitManagersForFrequency().isEmpty(); }

    private List<PatternP2PUnitManagerPart> unitManagersForFrequency() {
        if (getTile().getWorld() == null || getGridNode() == null) {
            return java.util.Collections.emptyList();
        }
        return PatternP2PTopologyGridService.findByFrequency(getGridNode(), getFrequency(),
                getTile().getWorld().getTotalWorldTime());
    }

    private ItemStack adjacentInsert(ItemStack source, long[] directions, int patternSlot,
            boolean simulate) {
        int direction = InputDirectionCodec.getDirectionOrdinal(directions, patternSlot);
        EnumFacing outputSide = getSide().getFacing();
        TileEntity tile = getTile().getWorld().getTileEntity(getTile().getPos().offset(outputSide));
        if (tile == null) return source;
        EnumFacing targetFace = direction < 0 ? outputSide.getOpposite() : EnumFacing.values()[direction];
        IItemHandler handler = tile.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, targetFace);
        return handler == null ? source : ItemHandlerHelper.insertItem(handler, source, simulate);
    }

    private static final class RoutedInput {
        private final int patternSlot;
        private final int runtimeSlot;
        private final ItemStack stack;

        private RoutedInput(int patternSlot, int runtimeSlot, ItemStack stack) {
            this.patternSlot = patternSlot;
            this.runtimeSlot = runtimeSlot;
            this.stack = stack;
        }
    }

    private static final class ManagerDispatch {
        private final List<ItemStack> inputs;
        private final List<UnitPortType> targetTypes;
        private final ItemStack primaryOutput;
        private final long primaryAmount;
        private final List<ItemStack> outputIdentities;

        private ManagerDispatch(List<ItemStack> inputs, List<UnitPortType> targetTypes,
                ItemStack primaryOutput, long primaryAmount, List<ItemStack> outputIdentities) {
            this.inputs = inputs;
            this.targetTypes = targetTypes;
            this.primaryOutput = primaryOutput;
            this.primaryAmount = primaryAmount;
            this.outputIdentities = outputIdentities;
        }
    }

    private static final class OutputReturnMetadata {
        private final Map<StackKey, Long> outputs;
        private final StackKey primaryKey;
        private final long primaryAmount;

        private OutputReturnMetadata(Map<StackKey, Long> outputs, StackKey primaryKey, long primaryAmount) {
            this.outputs = outputs;
            this.primaryKey = primaryKey;
            this.primaryAmount = primaryAmount;
        }
    }

    private static final class StackKey {
        private final NBTTagCompound serialized;

        private StackKey(ItemStack stack) {
            ItemStack identity = stack.copy();
            identity.setCount(1);
            serialized = identity.writeToNBT(new NBTTagCompound());
        }

        private ItemStack toStack() {
            return new ItemStack(serialized.copy());
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof StackKey
                    && serialized.equals(((StackKey) other).serialized);
        }

        @Override
        public int hashCode() {
            return serialized.hashCode();
        }
    }

    @Override public boolean hasCapability(Capability<?> capability) { return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY; }
    @Override public <T> T getCapability(Capability<T> capability) { return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this) : null; }
    @Override public int getSlots() { return 1; }
    @Nonnull @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
    @Nonnull @Override public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        return output ? returnOutputProduct(stack, simulate) : stack;
    }
    @Nonnull @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
    @Override public int getSlotLimit(int slot) { return 64; }
    @Override public boolean isItemValid(int slot, @Nonnull ItemStack stack) { return true; }

    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(1, ProductExtractionLimits.MAX_INTERVAL, false, true); }
    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isActive()) return TickRateModulation.SLOWER;
        if (!output) return probeBlockedReturn() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        EndpointProductExtractionSettings extraction = getProductExtractionSettingsFromInput();
        if (extraction == null || !extraction.isEnabled()) return TickRateModulation.SLOWER;
        long now = getTile().getWorld().getTotalWorldTime();
        if (!extractionDeadline.isDue(now, extraction.getInterval())) return TickRateModulation.SAME;
        return extractFromAdjacent(extraction.getAmount()) > 0
                ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        int extractionInterval = tag.hasKey("Ae2bcExtractionInterval")
                ? ProductExtractionLimits.clampInterval(tag.getInteger("Ae2bcExtractionInterval"))
                : ProductExtractionLimits.DEFAULT_INTERVAL;
        int extractionAmount = tag.hasKey("Ae2bcExtractionAmount")
                ? ProductExtractionLimits.clampAmount(tag.getInteger("Ae2bcExtractionAmount"))
                : ProductExtractionLimits.DEFAULT_AMOUNT;
        productExtractionSettings = new EndpointProductExtractionSettings(
                tag.getBoolean("Ae2bcExtractionEnabled"), extractionInterval, extractionAmount,
                tag.getLong("Ae2bcExtractionRevision"));
        unitConfigurationRevision = tag.getLong("Ae2bcUnitConfigurationRevision");
        roundRobinCursor = tag.getInteger("Ae2bcRoundRobinCursor");
        returnMode = tag.hasKey("Ae2bcReturnMode")
                ? ReturnMode.fromId(tag.getInteger("Ae2bcReturnMode")) : ReturnMode.UNBLOCKED;
        breakRecovery = !tag.hasKey("Ae2bcBreakRecovery") || tag.getBoolean("Ae2bcBreakRecovery");
        redstoneMode = RedstoneOutputMode.fromId(tag.getInteger("Ae2bcRedstoneMode"));
        redstoneStrength = tag.hasKey("Ae2bcRedstoneStrength")
                ? Math.max(0, Math.min(15, tag.getInteger("Ae2bcRedstoneStrength"))) : 15;
        pulsePeriodTicks = tag.hasKey("Ae2bcPulsePeriod")
                ? Math.max(1, tag.getInteger("Ae2bcPulsePeriod")) : 20;
        pulseWidthTicks = tag.hasKey("Ae2bcPulseWidth")
                ? Math.max(1, Math.min(pulsePeriodTicks, tag.getInteger("Ae2bcPulseWidth"))) : 2;
        syncInputSettings = !tag.hasKey("Ae2bcSyncInputSettings")
                || tag.getBoolean("Ae2bcSyncInputSettings");
        readOutputReturnBatch(tag);
        extractionDeadline.wake();
    }
    @Override public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("Ae2bcExtractionEnabled", productExtractionSettings.isEnabled());
        tag.setInteger("Ae2bcExtractionInterval", productExtractionSettings.getInterval());
        tag.setInteger("Ae2bcExtractionAmount", productExtractionSettings.getAmount());
        tag.setLong("Ae2bcExtractionRevision", productExtractionSettings.getRevision());
        tag.setLong("Ae2bcUnitConfigurationRevision", unitConfigurationRevision);
        tag.setInteger("Ae2bcRoundRobinCursor", roundRobinCursor);
        tag.setInteger("Ae2bcReturnMode", returnMode.getId());
        tag.setBoolean("Ae2bcBreakRecovery", breakRecovery);
        tag.setInteger("Ae2bcRedstoneMode", redstoneMode.getId());
        tag.setInteger("Ae2bcRedstoneStrength", redstoneStrength);
        tag.setInteger("Ae2bcPulseWidth", pulseWidthTicks);
        tag.setInteger("Ae2bcPulsePeriod", pulsePeriodTicks);
        tag.setBoolean("Ae2bcSyncInputSettings", syncInputSettings);
        writeOutputReturnBatch(tag);
    }

    @Override public void writeToStream(ByteBuf data) throws java.io.IOException {
        super.writeToStream(data);
        data.writeBoolean(productExtractionSettings.isEnabled());
        PatternP2PUnitSettings settings = getUnitSettings();
        data.writeByte(settings.getReturnMode().getId());
        data.writeBoolean(settings.isBreakRecovery());
        data.writeInt(settings.getExtractionInterval());
        data.writeInt(settings.getExtractionAmount());
        data.writeByte(settings.getRedstoneMode().getId());
        data.writeByte(settings.getRedstoneStrength());
        data.writeInt(settings.getPulseWidthTicks());
        data.writeInt(settings.getPulsePeriodTicks());
        data.writeBoolean(syncInputSettings);
    }

    @Override public boolean readFromStream(ByteBuf data) throws java.io.IOException {
        boolean changed = super.readFromStream(data);
        boolean oldEnabled = productExtractionSettings.isEnabled();
        PatternP2PUnitSettings old = getUnitSettings();
        boolean oldSync = syncInputSettings;
        boolean extractionEnabled = data.readBoolean();
        applySettings(new PatternP2PUnitSettings(
                ReturnMode.fromId(data.readUnsignedByte()), data.readBoolean(),
                data.readInt(), data.readInt(), RedstoneOutputMode.fromId(data.readUnsignedByte()),
                data.readUnsignedByte(), data.readInt(), data.readInt()));
        productExtractionSettings = new EndpointProductExtractionSettings(extractionEnabled,
                productExtractionSettings.getInterval(), productExtractionSettings.getAmount(),
                productExtractionSettings.getRevision());
        syncInputSettings = data.readBoolean();
        PatternP2PUnitSettings next = getUnitSettings();
        return changed || oldEnabled != productExtractionSettings.isEnabled()
                || oldSync != syncInputSettings
                || old.getReturnMode() != next.getReturnMode()
                || old.isBreakRecovery() != next.isBreakRecovery()
                || old.getExtractionInterval() != next.getExtractionInterval()
                || old.getExtractionAmount() != next.getExtractionAmount()
                || old.getRedstoneMode() != next.getRedstoneMode()
                || old.getRedstoneStrength() != next.getRedstoneStrength()
                || old.getPulseWidthTicks() != next.getPulseWidthTicks()
                || old.getPulsePeriodTicks() != next.getPulsePeriodTicks();
    }

    public void setExtractionSettings(boolean enabled, int interval, int amount) {
        boolean unitChanged = productExtractionSettings.getInterval()
                != ProductExtractionLimits.clampInterval(interval)
                || productExtractionSettings.getAmount()
                != ProductExtractionLimits.clampAmount(amount);
        if (productExtractionSettings.hasSameValues(enabled, interval, amount)) return;
        productExtractionSettings = new EndpointProductExtractionSettings(enabled, interval, amount,
                productExtractionSettings.getRevision() + 1);
        if (unitChanged) unitConfigurationRevision++;
        extractionDeadline.wake();
        saveChanges();
        if (!output) {
            if (unitChanged) synchronizeUnitManagers();
            wakeOutputs();
        }
    }
    public PatternP2PUnitSettings getUnitSettings() {
        return new PatternP2PUnitSettings(returnMode, breakRecovery,
                productExtractionSettings.getInterval(), productExtractionSettings.getAmount(),
                redstoneMode, redstoneStrength, pulseWidthTicks, pulsePeriodTicks);
    }
    public long getUnitConfigurationRevision() { return unitConfigurationRevision; }
    public void setInputSettings(boolean enabled, PatternP2PUnitSettings settings) {
        if (output || settings == null) return;
        boolean unitChanged = !sameSettings(getUnitSettings(), settings);
        boolean extractionChanged = !productExtractionSettings.hasSameValues(enabled,
                settings.getExtractionInterval(), settings.getExtractionAmount());
        if (!unitChanged && !extractionChanged) return;
        applySettings(settings);
        if (unitChanged) unitConfigurationRevision++;
        if (extractionChanged) {
            productExtractionSettings = new EndpointProductExtractionSettings(enabled,
                    settings.getExtractionInterval(), settings.getExtractionAmount(),
                    productExtractionSettings.getRevision() + 1);
        }
        extractionDeadline.wake();
        saveChanges();
        getHost().markForUpdate();
        if (unitChanged) synchronizeUnitManagers();
        if (extractionChanged) wakeOutputs();
    }

    private static boolean sameSettings(PatternP2PUnitSettings left, PatternP2PUnitSettings right) {
        return left.getReturnMode() == right.getReturnMode()
                && left.isBreakRecovery() == right.isBreakRecovery()
                && left.getExtractionInterval() == right.getExtractionInterval()
                && left.getExtractionAmount() == right.getExtractionAmount()
                && left.getRedstoneMode() == right.getRedstoneMode()
                && left.getRedstoneStrength() == right.getRedstoneStrength()
                && left.getPulseWidthTicks() == right.getPulseWidthTicks()
                && left.getPulsePeriodTicks() == right.getPulsePeriodTicks();
    }

    private void synchronizeUnitManagers() {
        if (output) return;
        for (PatternP2PUnitManagerPart manager
                : PatternP2PTopologyGridService.findAllByFrequency(getGridNode(), getFrequency())) {
            manager.applyMainConfiguration(getUnitSettings(), unitConfigurationRevision);
        }
    }
    public void setOutputSettings(ReturnMode mode, boolean sync) {
        if (!output) return;
        returnMode = mode == null ? ReturnMode.UNBLOCKED : mode;
        syncInputSettings = sync;
        saveChanges();
    }
    public boolean isSyncInputSettings() { return syncInputSettings; }
    public ReturnMode getReturnMode() {
        PatternP2PTunnelPart input = output && syncInputSettings ? getInput() : null;
        return input == null ? returnMode : input.returnMode;
    }
    public void resetTaskState() {
        if (output) {
            clearOutputReturnBatch();
            PatternP2PTunnelPart input = getInput();
            if (input != null) input.resetTaskState();
            return;
        }
        for (PatternP2PTunnelPart endpoint : outputs()) endpoint.clearOutputReturnBatch();
        for (PatternP2PUnitManagerPart manager
                : PatternP2PTopologyGridService.findAllByFrequency(getGridNode(), getFrequency())) {
            manager.resetTaskState();
        }
    }
    private void applySettings(PatternP2PUnitSettings settings) {
        returnMode = settings.getReturnMode();
        breakRecovery = settings.isBreakRecovery();
        productExtractionSettings = new EndpointProductExtractionSettings(
                productExtractionSettings.isEnabled(), settings.getExtractionInterval(),
                settings.getExtractionAmount(), productExtractionSettings.getRevision());
        redstoneMode = settings.getRedstoneMode();
        redstoneStrength = settings.getRedstoneStrength();
        pulseWidthTicks = settings.getPulseWidthTicks();
        pulsePeriodTicks = settings.getPulsePeriodTicks();
    }
    public boolean isExtractionEnabled() { return productExtractionSettings.isEnabled(); }
    public int getExtractionInterval() { return productExtractionSettings.getInterval(); }
    public int getExtractionAmount() { return productExtractionSettings.getAmount(); }

    private EndpointProductExtractionSettings getProductExtractionSettingsFromInput() {
        if (!output) return null;
        PatternP2PTunnelPart input = getInput();
        return input == null || input.isOutput() || input.getFrequency() == 0
                ? null : input.productExtractionSettings;
    }

    public boolean isEnergyOutputAvailable() { return output && isActive(); }
    @Override public boolean isEnergyEndpointAvailable() { return isEnergyOutputAvailable(); }

    @Override public int receiveExternalEnergy(int maxReceive, boolean simulate) {
        if (!isEnergyOutputAvailable() || maxReceive <= 0) return 0;
        EnumFacing face = getSide().getFacing();
        TileEntity tile = getTile().getWorld().getTileEntity(getTile().getPos().offset(face));
        if (tile == null) return 0;
        IEnergyStorage target = tile.getCapability(CapabilityEnergy.ENERGY, face.getOpposite());
        return target == null || !target.canReceive() ? 0 : target.receiveEnergy(maxReceive, simulate);
    }

    private int extractFromAdjacent(int amount) {
        IItemHandler source = adjacentHandler();
        PatternP2PTunnelPart input = getInput();
        if (input == null) return 0;
        int moved = 0;
        int transferredSlots = 0;
        for (int slot = 0; slot < source.getSlots() && moved < amount
                && transferredSlots < ProductExtractionLimits.MAX_TRANSFER_ENTRIES_PER_RUN; slot++) {
            ItemStack candidate = source.extractItem(slot, amount - moved, true);
            if (candidate.isEmpty()) continue;
            ItemStack remainder = returnOutputProduct(candidate, true);
            int accepted = candidate.getCount() - remainder.getCount();
            if (accepted <= 0) continue;
            ItemStack extracted = source.extractItem(slot, accepted, false);
            ItemStack unexpected = returnOutputProduct(extracted, false);
            int transferred = extracted.getCount() - unexpected.getCount();
            moved = (int) Math.min((long) amount, (long) moved + transferred);
            if (transferred > 0) transferredSlots++;
            if (!unexpected.isEmpty()) ItemHandlerHelper.insertItem(source, unexpected, false);
        }
        return moved;
    }

    private IItemHandler adjacentHandler() {
        TileEntity tile = getTile().getWorld().getTileEntity(getTile().getPos().offset(getSide().getFacing()));
        if (tile == null) return EmptyHandler.INSTANCE;
        IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                getSide().getFacing().getOpposite());
        return handler == null ? EmptyHandler.INSTANCE : handler;
    }

    ItemStack returnToAdjacent(ItemStack stack, boolean simulate) {
        EnumFacing face = getSide().getFacing();
        TileEntity tile = getTile().getWorld().getTileEntity(getTile().getPos().offset(face));
        if (tile == null) return stack;
        IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                face.getOpposite());
        if (handler == null) return stack;
        ItemStack remainder = InterfaceReturnFlusher.insert(
                tile, face.getOpposite(), handler, stack, simulate);
        if (!remainder.isEmpty()) {
            blockedReturnProbe = remainder.copy();
            blockedReturnProbe.setCount(1);
            returnRecoveryDeadline.wake();
            wakeSelf();
        }
        if (!simulate) queueTunnelDrain(PowerUnits.RF, stack.getCount() - remainder.getCount());
        return remainder;
    }

    private boolean probeBlockedReturn() {
        if (blockedReturnProbe.isEmpty()) return false;
        long now = getTile().getWorld().getTotalWorldTime();
        if (!returnRecoveryDeadline.isDue(now, 20)) return true;
        ItemStack remainder = ItemHandlerHelper.insertItem(adjacentHandler(), blockedReturnProbe, true);
        if (!remainder.isEmpty()) return true;
        blockedReturnProbe = ItemStack.EMPTY;
        wakeOutputs();
        return false;
    }

    private void wakeSelf() {
        try {
            getProxy().getTick().wakeDevice(getGridNode());
        } catch (GridAccessException ignored) {
        }
    }

    private void wakeOutputs() {
        for (PatternP2PTunnelPart output : outputs()) {
            output.extractionDeadline.wake();
            try {
                getProxy().getTick().alertDevice(output.getGridNode());
            } catch (GridAccessException ignored) {
                // Another output on the frequency may still be available.
            }
        }
    }

    private static final class EmptyHandler implements IItemHandler {
        private static final EmptyHandler INSTANCE = new EmptyHandler();
        @Override public int getSlots() { return 0; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    }
}
