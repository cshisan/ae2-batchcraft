package cn.ae2bc.part;

import cn.ae2bc.item.PatternP2PUnitManagerItem;
import cn.ae2bc.registry.ModContent;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.parts.PartModel;
import appeng.parts.networking.PartCable;
import cn.ae2bc.core.frequency.FrequencyLimits;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import cn.ae2bc.core.unit.TransferPortOutputMode;
import cn.ae2bc.logic.EnergyDistributionMode;
import cn.ae2bc.logic.PatternP2PUnitDimensions;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.Ae2bcMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

/** Center cable that owns one Pattern P2P unit. */
public final class PatternP2PUnitManagerPart extends PartCable
        implements IGridTickable, IPowerChannelState {
    public static final ResourceLocation MODEL_ID = new ResourceLocation(
            "ae2_batchcraft", "part/p2p/pattern_p2p_unit_manager");
    public static final ResourceLocation GLASS_MODEL_ID = new ResourceLocation(
            "ae2_batchcraft", "part/pattern_p2p_unit_manager_glass");
    public static final ResourceLocation FREQUENCY_MODEL_ID = new ResourceLocation(
            "appliedenergistics2", "part/builtin/p2p_tunnel_frequency");
    private static final String MAIN_CONFIGURATION = "PatternP2PUnitMainConfiguration";
    private static final String MAIN_CONFIGURATION_REVISION =
            "PatternP2PUnitMainConfigurationRevision";
    private static final IPartModel MODEL = new PartModel(false, MODEL_ID, GLASS_MODEL_ID);

    private short frequency;
    private UUID unitId = UUID.randomUUID();
    private boolean modelActive;
    private final List<ItemStack> pendingInputs = new ArrayList<ItemStack>();
    private final List<cn.ae2bc.core.unit.UnitPortType> pendingInputTypes =
            new ArrayList<cn.ae2bc.core.unit.UnitPortType>();
    /** Stable encoded-pattern slot for each pending input. */
    private final List<Integer> pendingInputSlots = new ArrayList<Integer>();
    private final java.util.Map<PatternP2PUnitPortPart, Integer> dispatchPortSlots =
            new java.util.IdentityHashMap<>();
    private final java.util.Map<Integer, PatternP2PUnitPortPart> dispatchSlotPorts =
            new java.util.HashMap<>();
    private final java.util.Map<PatternP2PUnitPortPart, net.minecraft.item.Item> dispatchPortTypes =
            new java.util.IdentityHashMap<>();
    private final java.util.Map<Integer, EnumFacing> persistedSlotPortSides =
            new java.util.HashMap<>();
    private final List<ItemStack> declaredOutputs = new ArrayList<ItemStack>();
    private boolean taskActive;
    private ItemStack primaryOutput = ItemStack.EMPTY;
    private long remainingPrimary;
    private cn.ae2bc.logic.ReturnMode returnMode = cn.ae2bc.logic.ReturnMode.UNBLOCKED;
    private boolean breakRecovery = true;
    private int extractionInterval = cn.ae2bc.core.extraction.ProductExtractionLimits.DEFAULT_INTERVAL;
    private int extractionAmount = cn.ae2bc.core.extraction.ProductExtractionLimits.DEFAULT_AMOUNT;
    private long inputCacheTick = Long.MIN_VALUE;
    private PatternP2PTunnelPart inputCache;
    private cn.ae2bc.logic.RedstoneOutputMode redstoneMode = cn.ae2bc.logic.RedstoneOutputMode.SINGLE_TRIGGER;
    private int redstoneStrength = 15;
    private int pulseWidthTicks = 2;
    private int pulsePeriodTicks = 20;
    private OutputSlotSharingMode outputSlotSharingMode = OutputSlotSharingMode.DISABLED;
    private TransferPortOutputMode transferPortOutputMode = TransferPortOutputMode.NORMAL;
    private EnergyDistributionMode energyDistributionMode = EnergyDistributionMode.EVEN;
    private long taskRevision;
    private boolean syncMainConfiguration = true;
    private boolean synchronizingFromInput;
    private long lastAppliedMainConfigurationRevision = -1;

    public PatternP2PUnitManagerPart(ItemStack stack) {
        super(stack);
        if (stack.getItem() instanceof PatternP2PUnitManagerItem) {
            getProxy().setColor(((PatternP2PUnitManagerItem) stack.getItem()).getColor(stack));
        }
        getProxy().setFlags(GridFlags.PREFERRED);
        getProxy().setIdlePowerUsage(1.0);
    }

    public int getFrequencyUnsigned() { return Short.toUnsignedInt(frequency); }
    public short getFrequency() { return frequency; }
    public UUID getUnitId() { return unitId; }
    public boolean hasConfiguredFrequency() { return frequency != 0; }
    @Override public boolean isPowered() {
        return getTile().getWorld() != null && getTile().getWorld().isRemote
                ? modelActive : getProxy().isPowered();
    }
    @Override public boolean isActive() {
        return getTile().getWorld() != null && getTile().getWorld().isRemote
                ? modelActive : getGridNode() != null && getGridNode().isActive();
    }
    public boolean isOperational() { return hasConfiguredFrequency() && getGridNode() != null && getGridNode().isActive(); }
    public boolean canAcceptTask() {
        if (syncMainConfiguration && getTile() != null && getTile().getWorld() != null
                && !getTile().getWorld().isRemote) synchronizeFromInput();
        return isOperational() && !taskActive && pendingInputs.isEmpty();
    }
    public boolean isTaskActive() { return taskActive || !pendingInputs.isEmpty(); }
    public cn.ae2bc.logic.ReturnMode getReturnMode() { return getEffectiveSettings().getReturnMode(); }
    public boolean isBreakRecovery() { return getEffectiveSettings().isBreakRecovery(); }
    public int getExtractionInterval() { return getEffectiveSettings().getExtractionInterval(); }
    public int getExtractionAmount() { return getEffectiveSettings().getExtractionAmount(); }
    public cn.ae2bc.logic.RedstoneOutputMode getRedstoneMode() { return getEffectiveSettings().getRedstoneMode(); }
    public int getRedstoneStrength() { return getEffectiveSettings().getRedstoneStrength(); }
    public int getPulseWidthTicks() { return getEffectiveSettings().getPulseWidthTicks(); }
    public int getPulsePeriodTicks() { return getEffectiveSettings().getPulsePeriodTicks(); }
    public OutputSlotSharingMode getOutputSlotSharingMode() {
        return getEffectiveSettings().getOutputSlotSharingMode();
    }
    public long getTaskRevision() { return taskRevision; }
    public boolean isSyncMainConfiguration() { return syncMainConfiguration; }
    public void setSyncMainConfiguration(boolean value) {
        if (syncMainConfiguration == value) return;
        syncMainConfiguration = value;
        if (value) {
            invalidateInputCache();
            synchronizeFromInput();
        }
        saveChanges();
        getHost().markForUpdate();
        wakeBoundPorts();
        applyOutputSlotSharingModeToPorts();
        cn.ae2bc.network.ModNetwork.refreshUnitPortStates(this);
    }

    public PatternP2PUnitSettings getSettings() {
        return getEffectiveSettings();
    }

    public void setSettings(PatternP2PUnitSettings settings) {
        if (settings == null || syncMainConfiguration) return;
        applyLocalSettings(settings);
        saveChanges();
        getHost().markForUpdate();
        wakeBoundPorts();
        applyOutputSlotSharingModeToPorts();
        cn.ae2bc.network.ModNetwork.refreshUnitPortStates(this);
    }

    private PatternP2PUnitSettings getLocalSettings() {
        return new PatternP2PUnitSettings(returnMode, breakRecovery, extractionInterval,
                extractionAmount, redstoneMode, redstoneStrength, pulseWidthTicks, pulsePeriodTicks,
                transferPortOutputMode, outputSlotSharingMode, energyDistributionMode);
    }

    private PatternP2PUnitSettings getEffectiveSettings() {
        return getLocalSettings();
    }

    private void applyLocalSettings(PatternP2PUnitSettings settings) {
        returnMode = settings.getReturnMode();
        breakRecovery = settings.isBreakRecovery();
        extractionInterval = settings.getExtractionInterval();
        extractionAmount = settings.getExtractionAmount();
        redstoneMode = settings.getRedstoneMode();
        redstoneStrength = settings.getRedstoneStrength();
        pulseWidthTicks = settings.getPulseWidthTicks();
        pulsePeriodTicks = settings.getPulsePeriodTicks();
        transferPortOutputMode = settings.getTransferPortOutputMode();
        outputSlotSharingMode = settings.getOutputSlotSharingMode();
        energyDistributionMode = settings.getEnergyDistributionMode();
    }

    public void applyMainConfiguration(PatternP2PUnitSettings settings, long revision) {
        if (settings == null || !syncMainConfiguration) return;
        if (lastAppliedMainConfigurationRevision == revision
                && sameSettings(getLocalSettings(), settings)) return;
        applyLocalSettings(settings);
        lastAppliedMainConfigurationRevision = revision;
        saveChanges();
        getHost().markForUpdate();
        wakeBoundPorts();
        applyOutputSlotSharingModeToPorts();
        cn.ae2bc.network.ModNetwork.refreshUnitPortStates(this);
    }

    /** Propagates the manager's current single-slot policy to bound output ports. */
    public void applyOutputSlotSharingModeToPorts() {
        for (PatternP2PUnitPortPart port
                : PatternP2PTopologyGridService.findPorts(getGridNode(), unitId)) {
            port.applyManagerSingleSlot(getOutputSlotSharingMode());
        }
    }

    public void resetTaskState() {
        pendingInputs.clear();
        pendingInputTypes.clear();
        pendingInputSlots.clear();
        declaredOutputs.clear();
        primaryOutput = ItemStack.EMPTY;
        remainingPrimary = 0;
        taskActive = false;
        dispatchPortSlots.clear();
        dispatchSlotPorts.clear();
        dispatchPortTypes.clear();
        taskRevision++;
        saveChanges();
        getHost().markForUpdate();
        invalidateBoundPortRuntimeState();
    }

    public EnergyDistributionMode getEnergyDistributionMode() {
        return getEffectiveSettings().getEnergyDistributionMode();
    }

    public void setEnergyDistributionMode(EnergyDistributionMode mode) {
        if (mode == null || syncMainConfiguration || energyDistributionMode == mode) return;
        energyDistributionMode = mode;
        saveChanges();
        getHost().markForUpdate();
        wakeBoundPorts();
    }

    public boolean canAcceptInput(ItemStack stack, cn.ae2bc.core.unit.UnitPortType targetType) {
        if (!canAcceptTask() || stack == null || stack.isEmpty()) return false;
        return findInputPort(stack, targetType, -1, null, true) != null;
    }

    public boolean acceptInputs(List<ItemStack> inputs, List<cn.ae2bc.core.unit.UnitPortType> targetTypes,
            ItemStack primaryOutput, long primaryAmount, List<ItemStack> outputs) {
        // Keep the legacy overload's task admission path explicit for callers and
        // preserve the same input-synchronized behavior as the main overload.
        synchronizeFromInput();
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < inputs.size(); i++) slots.add(i);
        return acceptInputs(inputs, targetTypes, slots, primaryOutput, primaryAmount, outputs);
    }

    public boolean acceptInputs(List<ItemStack> inputs, List<cn.ae2bc.core.unit.UnitPortType> targetTypes,
            List<Integer> patternSlots, ItemStack primaryOutput, long primaryAmount,
            List<ItemStack> outputs) {
        if (!canAcceptTask() || inputs == null || inputs.isEmpty()
                || targetTypes == null || targetTypes.size() != inputs.size()
                || patternSlots == null || patternSlots.size() != inputs.size()) return false;
        if (!canAcceptInputsAggregate(inputs, targetTypes, patternSlots)) return false;
        pendingInputs.clear();
        pendingInputTypes.clear();
        pendingInputSlots.clear();
        for (int i = 0; i < inputs.size(); i++) {
            pendingInputs.add(inputs.get(i).copy());
            pendingInputSlots.add(patternSlots.get(i));
        }
        pendingInputTypes.addAll(targetTypes);
        this.primaryOutput = primaryOutput == null ? ItemStack.EMPTY : primaryOutput.copy();
        if (!this.primaryOutput.isEmpty()) this.primaryOutput.setCount(1);
        this.remainingPrimary = this.primaryOutput.isEmpty() ? 0 : Math.max(0, primaryAmount);
        declaredOutputs.clear();
        if (outputs != null) {
            for (ItemStack output : outputs) {
                if (output != null && !output.isEmpty() && !containsSameItem(declaredOutputs, output)) {
                    ItemStack identity = output.copy();
                    identity.setCount(1);
                    declaredOutputs.add(identity);
                }
            }
        }
        synchronizeFromInput(true);
        taskRevision++;
        taskActive = true;
        dispatchPortSlots.clear();
        dispatchSlotPorts.clear();
        dispatchPortTypes.clear();
        saveChanges();
        wake();
        wakeBoundPorts();
        return true;
    }

    private PatternP2PUnitPortPart findInputPort(ItemStack stack, cn.ae2bc.core.unit.UnitPortType targetType,
            int slot, java.util.Map<PatternP2PUnitPortPart, Integer> owners, boolean simulate) {
        java.util.List<PatternP2PUnitPortPart> ports = new ArrayList<>(PatternP2PTopologyGridService.findPorts(getGridNode(), unitId));
        ports.removeIf(port -> port.getPortType() != targetType);
        ports.removeIf(port -> !port.matchesInput(this, stack, formForPortType(targetType)));
        EnumFacing persistedSide = persistedSlotPortSides.get(slot);
        if (persistedSide != null) {
            for (PatternP2PUnitPortPart port : ports) {
                if (port.getSide().getFacing() == persistedSide) {
                    dispatchSlotPorts.put(slot, port);
                    break;
                }
            }
        }
        ports.removeIf(port -> owners != null && !canUsePortForSlot(slot, port, owners, dispatchSlotPorts));
        ports.sort((left, right) -> Integer.compare(right.getTransferPriority(), left.getTransferPriority()));
        for (PatternP2PUnitPortPart port : ports) {
            int amount = port.insertTaskInput(this, stack, formForPortType(targetType), simulate);
            if (amount > 0) return port;
        }
        return null;
    }

    private static cn.ae2bc.pattern.MaterialOutputForm formForPortType(
            cn.ae2bc.core.unit.UnitPortType type) {
        return cn.ae2bc.pattern.MaterialOutputForm.fromId(
                type == cn.ae2bc.core.unit.UnitPortType.DROP ? 1
                        : type == cn.ae2bc.core.unit.UnitPortType.PLACE ? 2 : 0);
    }

    private List<PatternP2PUnitPortPart> candidatePorts(
            List<PatternP2PUnitPortPart> ports, ItemStack stack,
            cn.ae2bc.pattern.MaterialOutputForm form) {
        List<PatternP2PUnitPortPart> result = new ArrayList<>();
        for (PatternP2PUnitPortPart port : ports) {
            if (port.matchesInput(this, stack, form)) result.add(port);
        }
        result.sort((left, right) -> Integer.compare(right.getTransferPriority(), left.getTransferPriority()));
        return result;
    }

    private boolean canAcceptInputsAggregate(List<ItemStack> inputs,
            List<cn.ae2bc.core.unit.UnitPortType> targetTypes,
            List<Integer> patternSlots) {
        java.util.Map<cn.ae2bc.core.unit.UnitPortType, List<PatternP2PUnitPortPart>> portsByType =
                new java.util.EnumMap<>(cn.ae2bc.core.unit.UnitPortType.class);
        for (PatternP2PUnitPortPart port : PatternP2PTopologyGridService.findPorts(getGridNode(), unitId)) {
            portsByType.computeIfAbsent(port.getPortType(), ignored -> new ArrayList<>()).add(port);
        }
        for (cn.ae2bc.core.unit.UnitPortType type : cn.ae2bc.core.unit.UnitPortType.values()) {
            java.util.List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) if (targetTypes.get(i) == type) indexes.add(i);
            if (indexes.isEmpty()) continue;
            List<PatternP2PUnitPortPart> ports = portsByType.get(type);
            if (ports == null || ports.isEmpty()) return false;
            java.util.Map<PatternP2PUnitPortPart, Integer> remaining = new java.util.IdentityHashMap<>();
            java.util.Map<PatternP2PUnitPortPart, Integer> assignedSlots = new java.util.IdentityHashMap<>();
            java.util.Map<Integer, PatternP2PUnitPortPart> assignedSlotPorts = new java.util.HashMap<>();
            java.util.Map<PatternP2PUnitPortPart, net.minecraft.item.Item> assignedTypes = new java.util.IdentityHashMap<>();
            cn.ae2bc.core.unit.TransferPortOutputMode outputMode = getEffectiveSettings().getTransferPortOutputMode();
            for (PatternP2PUnitPortPart port : ports) {
                remaining.put(port, type == cn.ae2bc.core.unit.UnitPortType.TRANSFER && outputMode
                        == cn.ae2bc.core.unit.TransferPortOutputMode.SINGLE_ITEM ? 1 : Integer.MAX_VALUE);
            }
            for (Integer index : indexes) {
                ItemStack stack = inputs.get(index).copy();
                int slot = patternSlots.get(index);
                int left = stack.getCount();
                for (PatternP2PUnitPortPart port : candidatePorts(ports, stack, formForPortType(type))) {
                    if (left <= 0) break;
                    if (!canUsePortForSlot(slot, port, assignedSlots, assignedSlotPorts)) continue;
                    if (outputMode == cn.ae2bc.core.unit.TransferPortOutputMode.SAME_TYPE
                            && assignedTypes.containsKey(port)
                            && assignedTypes.get(port) != stack.getItem()) continue;
                    int simulated = port.insertTaskInput(this, stack, formForPortType(type), true);
                    int capacity = remaining.get(port);
                    if (type == cn.ae2bc.core.unit.UnitPortType.TRANSFER
                            && capacity == Integer.MAX_VALUE) {
                        capacity = port.estimateTransferCapacity(stack, simulated);
                        remaining.put(port, capacity);
                    }
                    if (type == cn.ae2bc.core.unit.UnitPortType.TRANSFER
                            && outputMode == cn.ae2bc.core.unit.TransferPortOutputMode.SINGLE_ITEM) {
                        capacity = Math.min(capacity, 1);
                    }
                    int accepted = Math.min(left, Math.min(simulated, capacity));
                    left -= accepted;
                    if (capacity != Integer.MAX_VALUE) remaining.put(port, capacity - accepted);
                    if (accepted > 0) {
                        assignedSlots.putIfAbsent(port, slot);
                        if (port.getEffectiveSingleSlot()) {
                            assignedSlotPorts.putIfAbsent(slot, port);
                            persistedSlotPortSides.putIfAbsent(slot, port.getSide().getFacing());
                            // A single-slot port may not split one pattern slot across ports.
                            break;
                        }
                    }
                    if (outputMode == cn.ae2bc.core.unit.TransferPortOutputMode.SAME_TYPE
                            && accepted > 0) assignedTypes.put(port, stack.getItem());
                }
                boolean singleSlot = false;
                for (PatternP2PUnitPortPart port : ports) {
                    if (port.getEffectiveSingleSlot()) { singleSlot = true; break; }
                }
                if (left > 0 && !singleSlot) return false;
                if (left > 0 && !assignedSlotPorts.containsKey(slot)) return false;
            }
        }
        return true;
    }

    private boolean dispatchPending() {
        boolean changed = false;
        for (int index = 0; index < pendingInputs.size();) {
            ItemStack stack = pendingInputs.get(index);
            cn.ae2bc.core.unit.UnitPortType targetType = pendingInputTypes.get(index);
            int slot = pendingInputSlots.get(index);
            cn.ae2bc.pattern.MaterialOutputForm form = formForPortType(targetType);
            int remaining = stack.getCount();
            boolean insertedAny = false;
            List<PatternP2PUnitPortPart> ports = new ArrayList<>(
                    PatternP2PTopologyGridService.findPorts(getGridNode(), unitId));
            ports.removeIf(port -> port.getPortType() != targetType);
            ports = candidatePorts(ports, stack, form);
            for (PatternP2PUnitPortPart port : ports) {
                if (remaining <= 0 || !canUsePortForSlot(slot, port,
                        dispatchPortSlots, dispatchSlotPorts)) continue;
                if (getEffectiveSettings().getTransferPortOutputMode()
                        == cn.ae2bc.core.unit.TransferPortOutputMode.SAME_TYPE
                        && dispatchPortTypes.containsKey(port)
                        && dispatchPortTypes.get(port) != stack.getItem()) continue;
                ItemStack portion = stack.copy();
                portion.setCount(remaining);
                int moved = port.insertTaskInput(this, portion, form, false);
                if (moved <= 0) continue;
                moved = Math.min(moved, remaining);
                remaining -= moved;
                insertedAny = true;
                dispatchPortSlots.putIfAbsent(port, slot);
                if (port.getEffectiveSingleSlot()) {
                    dispatchSlotPorts.putIfAbsent(slot, port);
                    if (port.getSide() != null) persistedSlotPortSides.putIfAbsent(slot, port.getSide().getFacing());
                    // Keep the complete material on this port when single-slot is enabled.
                    break;
                }
                if (getEffectiveSettings().getTransferPortOutputMode()
                        == cn.ae2bc.core.unit.TransferPortOutputMode.SAME_TYPE) {
                    dispatchPortTypes.put(port, stack.getItem());
                }
            }
            if (remaining <= 0) {
                pendingInputs.remove(index);
                pendingInputTypes.remove(index);
                pendingInputSlots.remove(index);
                changed = true;
            } else {
                if (insertedAny) {
                    stack.setCount(remaining);
                    changed = true;
                }
                index++;
            }
        }
        if (finishTaskIfComplete()) changed = true;
        if (changed) { saveChanges(); getHost().markForUpdate(); }
        return changed;
    }

    private boolean canUsePortForSlot(int slot, PatternP2PUnitPortPart port,
            java.util.Map<PatternP2PUnitPortPart, Integer> owners,
            java.util.Map<Integer, PatternP2PUnitPortPart> slotOwners) {
        if (slot < 0 || owners == null) return true;
        Integer owner = owners.get(port);
        if (port.getEffectiveSingleSlot() && owner != null && owner.intValue() != slot) return false;
        PatternP2PUnitPortPart slotOwner = slotOwners.get(slot);
        return slotOwner == null || slotOwner == port || !slotOwner.getEffectiveSingleSlot();
    }

    public ItemStack returnProduct(ItemStack stack, boolean simulate) {
        if (!isTaskActive() || stack == null || stack.isEmpty()) return stack;
        if (getReturnMode() == cn.ae2bc.logic.ReturnMode.STRICT
                && !containsSameItem(declaredOutputs, stack)) return stack;
        PatternP2PTunnelPart input = findInput();
        if (input == null) return stack;
        ItemStack remainder = input.returnToAdjacent(stack, simulate);
        int accepted = stack.getCount() - remainder.getCount();
        if (!simulate && accepted > 0 && sameItem(stack, primaryOutput)) {
            remainingPrimary = Math.max(0, remainingPrimary - accepted);
            finishTaskIfComplete();
            saveChanges();
        }
        return remainder;
    }

    /** Retries an already validated extraction remainder after its task may have completed. */
    public ItemStack returnProductRecovery(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        PatternP2PTunnelPart input = findInput();
        return input == null || !input.isActive() ? stack : input.returnToAdjacent(stack, false);
    }

    private PatternP2PTunnelPart findInput() {
        if (getGridNode() == null || getTile().getWorld() == null) return null;
        long tick = getTile().getWorld().getTotalWorldTime();
        if (inputCacheTick == tick) return inputCache;
        inputCacheTick = tick;
        inputCache = PatternP2PTopologyGridService.findInput(getGridNode(), frequency);
        return inputCache;
    }

    private void invalidateInputCache() {
        inputCacheTick = Long.MIN_VALUE;
        inputCache = null;
    }

    public void synchronizeFromInput() {
        if (synchronizingFromInput) return;
        synchronizingFromInput = true;
        try {
            synchronizeFromInput(false);
        } finally {
            synchronizingFromInput = false;
        }
    }

    private void synchronizeFromInput(boolean forceRefresh) {
        if (forceRefresh) invalidateInputCache();
        PatternP2PTunnelPart input = findInput();
        if (input != null) applyMainConfiguration(
                input.getUnitSettings(), input.getUnitConfigurationRevision());
    }

    private boolean finishTaskIfComplete() {
        if (!taskActive || !pendingInputs.isEmpty() || remainingPrimary > 0) return false;
        taskActive = false;
        primaryOutput = ItemStack.EMPTY;
        remainingPrimary = 0;
        declaredOutputs.clear();
        pendingInputSlots.clear();
        dispatchPortSlots.clear();
        dispatchSlotPorts.clear();
        dispatchPortTypes.clear();
        invalidateBoundPortRuntimeState();
        return true;
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return !left.isEmpty() && !right.isEmpty()
                && ItemStack.areItemsEqual(left, right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static boolean sameSettings(PatternP2PUnitSettings left, PatternP2PUnitSettings right) {
        return left.getReturnMode() == right.getReturnMode()
                && left.isBreakRecovery() == right.isBreakRecovery()
                && left.getExtractionInterval() == right.getExtractionInterval()
                && left.getExtractionAmount() == right.getExtractionAmount()
                && left.getRedstoneMode() == right.getRedstoneMode()
                && left.getRedstoneStrength() == right.getRedstoneStrength()
                && left.getPulseWidthTicks() == right.getPulseWidthTicks()
                && left.getPulsePeriodTicks() == right.getPulsePeriodTicks()
                && left.getTransferPortOutputMode() == right.getTransferPortOutputMode()
                && left.getOutputSlotSharingMode() == right.getOutputSlotSharingMode()
                && left.getEnergyDistributionMode() == right.getEnergyDistributionMode();
    }

    private static boolean containsSameItem(List<ItemStack> stacks, ItemStack candidate) {
        for (ItemStack stack : stacks) if (sameItem(stack, candidate)) return true;
        return false;
    }

    private void wake() {
        try { getProxy().getTick().wakeDevice(getGridNode()); }
        catch (appeng.me.GridAccessException ignored) { }
    }

    private void wakeBoundPorts() {
        for (PatternP2PUnitPortPart port
                : PatternP2PTopologyGridService.findPorts(getGridNode(), unitId)) {
            port.alertTicking();
        }
    }

    private void invalidateBoundPortRuntimeState() {
        for (PatternP2PUnitPortPart port
                : PatternP2PTopologyGridService.findPorts(getGridNode(), unitId)) {
            port.invalidateTaskRuntimeState();
        }
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, false, false);
    }
    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        refreshModelState();
        if (!isOperational() || pendingInputs.isEmpty()) return TickRateModulation.SLEEP;
        return dispatchPending() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    @Override public void gridChanged() {
        super.gridChanged();
        if (getTile() == null || getTile().getWorld() == null || getTile().getWorld().isRemote) return;
        PatternP2PTopologyGridService.invalidate(getGridNode());
        invalidateInputCache();
        synchronizeFromInput();
        refreshModelState();
        getHost().markForUpdate();
        wake();
        wakeBoundPorts();
    }

    @MENetworkEventSubscribe
    public void onPowerStatusChanged(MENetworkPowerStatusChange event) {
        PatternP2PTopologyGridService.invalidate(getGridNode());
        invalidateInputCache();
        synchronizeFromInput();
        refreshModelState();
        wake();
        wakeBoundPorts();
    }

    @MENetworkEventSubscribe
    public void onChannelsChanged(MENetworkChannelsChanged event) {
        PatternP2PTopologyGridService.invalidate(getGridNode());
        invalidateInputCache();
        synchronizeFromInput();
        getHost().markForUpdate();
        wake();
        wakeBoundPorts();
    }

    private void refreshModelState() {
        if (getTile() == null || getTile().getWorld() == null || getTile().getWorld().isRemote) return;
        boolean active = getGridNode() != null && getGridNode().isActive();
        if (modelActive == active) return;
        modelActive = active;
        getHost().markForUpdate();
    }

    public void setFrequency(int value) {
        if (isTaskActive()) return;
        short next = (short) FrequencyLimits.clamp(value);
        if (next == frequency) return;
        frequency = next;
        PatternP2PTopologyGridService.invalidate(getGridNode());
        invalidateInputCache();
        saveChanges();
        getHost().markForUpdate();
        getHost().partChanged();
        synchronizeFromInput();
        wakeBoundPorts();
    }

    @Override public AECableType getCableConnectionType() { return AECableType.SMART; }
    @Override public boolean changeColor(AEColor color, EntityPlayer player) {
        if (color == null || color == getCableColor()) return false;
        if (player.world.isRemote) return true;
        getProxy().setColor(color);
        getProxy().setVisualRepresentation(coloredStack());
        saveChanges();
        getHost().markForUpdate();
        getHost().partChanged();
        return true;
    }
    @Override public ItemStack getItemStack() { return coloredStack(); }
    @Override public ItemStack getItemStack(PartItemStack type) { return coloredStack(); }

    /** The manager has its own memory-card schema and must bypass AE2's P2P handler. */
    @Override public boolean useStandardMemoryCard() { return false; }

    private ItemStack coloredStack() { return ModContent.UNIT_MANAGER.stack(getCableColor()); }

    @Override public Long getRenderFlag() {
        long value = Short.toUnsignedLong(frequency);
        if (modelActive) value |= 0x10000L;
        return Long.valueOf(value);
    }
    @Override public IPartModel getStaticModels() { return MODEL; }

    @Override public void getBoxes(IPartCollisionHelper helper) {
        super.getBoxes(helper);
        helper.addBox(PatternP2PUnitDimensions.FRAME_MIN, PatternP2PUnitDimensions.FRAME_MIN,
                PatternP2PUnitDimensions.FRAME_MIN, PatternP2PUnitDimensions.FRAME_MAX,
                PatternP2PUnitDimensions.FRAME_MAX, PatternP2PUnitDimensions.FRAME_MAX);

        if (getHost() != null) {
            for (EnumFacing side : EnumFacing.values()) {
                IPart part = getHost().getPart(side);
                if (part instanceof IGridHost) {
                    addConnectionBox(helper, side,
                            part.getCableConnectionLength(getCableConnectionType()));
                }
            }
        }
        for (EnumFacing side : EnumFacing.values()) {
            if (isConnected(side)) addConnectionBox(helper, side, 0.0);
        }
    }

    private static void addConnectionBox(IPartCollisionHelper helper, EnumFacing side, double length) {
        double min = PatternP2PUnitDimensions.FRAME_MIN;
        double max = PatternP2PUnitDimensions.FRAME_MAX;
        if (length < 0.0 || length > min) return;
        switch (side) {
            case DOWN:
                helper.addBox(min, length, min, max, min, max);
                break;
            case UP:
                helper.addBox(min, max, min, max, 16.0 - length, max);
                break;
            case NORTH:
                helper.addBox(min, min, length, max, max, min);
                break;
            case SOUTH:
                helper.addBox(min, min, max, max, max, 16.0 - length);
                break;
            case WEST:
                helper.addBox(length, min, min, min, max, max);
                break;
            case EAST:
                helper.addBox(max, min, min, 16.0 - length, max, max);
                break;
            default:
                break;
        }
    }

    @Override public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d hit) {
        if (handleMemoryCard(player, hand, false)) return true;
        if (hand == EnumHand.MAIN_HAND) {
            if (!player.world.isRemote) {
                player.openGui(Ae2bcMod.INSTANCE, Ae2bcMod.GUI_UNIT_MANAGER,
                        player.world, getTile().getPos().getX(), getTile().getPos().getY(), getTile().getPos().getZ());
            }
            return true;
        }
        return super.onPartActivate(player, hand, hit);
    }

    @Override public boolean onPartShiftActivate(EntityPlayer player, EnumHand hand, Vec3d hit) {
        return handleMemoryCard(player, hand, true) || super.onPartShiftActivate(player, hand, hit);
    }

    private boolean handleMemoryCard(EntityPlayer player, EnumHand hand, boolean save) {
        ItemStack held = player.getHeldItem(hand);
        if (hand != EnumHand.MAIN_HAND || !(held.getItem() instanceof IMemoryCard)) return false;
        if (player.world.isRemote) return true;
        IMemoryCard card = (IMemoryCard) held.getItem();
        if (save) {
            NBTTagCompound data = new NBTTagCompound();
            // Store the actual manager item so AE2 can identify the card on all 1.12.2 builds.
            coloredStack().writeToNBT(data);
            data.setShort("freq", frequency);
            data.setUniqueId("PatternP2PUnitId", unitId);
            card.setMemoryCardContents(held, "item.ae2_batchcraft.pattern_p2p_unit_manager", data);
            card.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
        } else {
            NBTTagCompound data = card.getData(held);
            if (!data.hasKey("freq") || !isSupportedMemoryCardSource(data)) {
                card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            } else {
                setFrequency(Short.toUnsignedInt(data.getShort("freq")));
                card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            }
        }
        return true;
    }

    private static boolean isSupportedMemoryCardSource(NBTTagCompound data) {
        // The UUID is our durable card marker. Accept it first because older AE2
        // versions disagree on how custom part stacks are reconstructed from NBT.
        if (data.hasUniqueId("PatternP2PUnitId")) return true;
        ItemStack source = new ItemStack(data);
        if (source.isEmpty()) {
            return false;
        }
        if (source.getItem() == ModContent.INPUT) return true;
        return source.getItem() == ModContent.UNIT_MANAGER;
    }

    @Override public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        frequency = data.getShort("PatternP2PFrequency");
        inputCacheTick = Long.MIN_VALUE;
        inputCache = null;
        unitId = data.hasUniqueId("PatternP2PUnitId")
                ? data.getUniqueId("PatternP2PUnitId") : UUID.randomUUID();
        taskActive = data.getBoolean("PatternP2PUnitTaskActive");
        pendingInputs.clear();
        pendingInputTypes.clear();
        pendingInputSlots.clear();
        net.minecraft.nbt.NBTTagList pending = data.getTagList("PatternP2PUnitPendingInputs", 10);
        for (int i = 0; i < pending.tagCount(); i++) {
            NBTTagCompound entry = pending.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(entry);
            if (!stack.isEmpty()) {
                pendingInputs.add(stack);
                // New tasks persist their encoded slot. Legacy tasks are migrated by
                // preserving the old pending-list order as the best available identity.
                pendingInputSlots.add(entry.hasKey("PatternSlot")
                        ? entry.getInteger("PatternSlot") : i);
                try { pendingInputTypes.add(cn.ae2bc.core.unit.UnitPortType.fromId(
                        entry.getString("PatternP2PUnitPortType"))); }
                catch (IllegalArgumentException ignored) {
                    pendingInputTypes.add(cn.ae2bc.core.unit.UnitPortType.TRANSFER);
                }
            }
        }
        primaryOutput = data.hasKey("PatternP2PUnitPrimaryOutput", 10)
                ? new ItemStack(data.getCompoundTag("PatternP2PUnitPrimaryOutput")) : ItemStack.EMPTY;
        remainingPrimary = Math.max(0, data.getLong("PatternP2PUnitRemainingPrimary"));
        returnMode = data.hasKey("PatternP2PUnitReturnMode")
                ? cn.ae2bc.logic.ReturnMode.fromId(data.getInteger("PatternP2PUnitReturnMode"))
                : cn.ae2bc.logic.ReturnMode.UNBLOCKED;
        breakRecovery = !data.hasKey("PatternP2PUnitBreakRecovery")
                || data.getBoolean("PatternP2PUnitBreakRecovery");
        extractionInterval = data.hasKey("PatternP2PUnitExtractionInterval")
                ? cn.ae2bc.core.extraction.ProductExtractionLimits.clampInterval(
                        data.getInteger("PatternP2PUnitExtractionInterval"))
                : cn.ae2bc.core.extraction.ProductExtractionLimits.DEFAULT_INTERVAL;
        extractionAmount = data.hasKey("PatternP2PUnitExtractionAmount")
                ? cn.ae2bc.core.extraction.ProductExtractionLimits.clampAmount(
                        data.getInteger("PatternP2PUnitExtractionAmount"))
                : cn.ae2bc.core.extraction.ProductExtractionLimits.DEFAULT_AMOUNT;
        declaredOutputs.clear();
        net.minecraft.nbt.NBTTagList savedOutputs = data.getTagList("PatternP2PUnitDeclaredOutputs", 10);
        for (int i = 0; i < savedOutputs.tagCount(); i++) {
            ItemStack output = new ItemStack(savedOutputs.getCompoundTagAt(i));
            if (!output.isEmpty()) declaredOutputs.add(output);
        }
        redstoneMode = cn.ae2bc.logic.RedstoneOutputMode.fromId(data.getInteger("PatternP2PUnitRedstoneMode"));
        redstoneStrength = data.hasKey("PatternP2PUnitRedstoneStrength")
                ? Math.max(0, Math.min(15, data.getInteger("PatternP2PUnitRedstoneStrength"))) : 15;
        pulsePeriodTicks = data.hasKey("PatternP2PUnitPulsePeriod")
                ? Math.max(1, data.getInteger("PatternP2PUnitPulsePeriod")) : 20;
        pulseWidthTicks = data.hasKey("PatternP2PUnitPulseWidth")
                ? Math.max(1, Math.min(pulsePeriodTicks, data.getInteger("PatternP2PUnitPulseWidth"))) : 2;
        transferPortOutputMode = data.hasKey("PatternP2PUnitTransferPortOutputMode")
                ? TransferPortOutputMode.fromId(data.getInteger("PatternP2PUnitTransferPortOutputMode"))
                : TransferPortOutputMode.NORMAL;
        outputSlotSharingMode = data.hasKey("PatternP2PUnitOutputSlotSharingMode")
                ? OutputSlotSharingMode.fromId(data.getInteger("PatternP2PUnitOutputSlotSharingMode"))
                : OutputSlotSharingMode.DISABLED;
        energyDistributionMode = data.hasKey("PatternP2PUnitEnergyDistributionMode")
                ? EnergyDistributionMode.fromId(data.getInteger("PatternP2PUnitEnergyDistributionMode"))
                : EnergyDistributionMode.EVEN;
        taskRevision = data.getLong("PatternP2PUnitTaskRevision");
        persistedSlotPortSides.clear();
        net.minecraft.nbt.NBTTagList bindings = data.getTagList("PatternP2PUnitSlotPortBindings", 10);
        for (int i = 0; i < bindings.tagCount(); i++) {
            NBTTagCompound entry = bindings.getCompoundTagAt(i);
            if (entry.hasKey("Slot") && entry.hasKey("Side")) {
                persistedSlotPortSides.put(entry.getInteger("Slot"),
                        EnumFacing.values()[entry.getInteger("Side") % EnumFacing.values().length]);
            }
        }
        syncMainConfiguration = !data.hasKey("PatternP2PUnitSyncMain")
                || data.getBoolean("PatternP2PUnitSyncMain");
        PatternP2PUnitSettings localSettings = getLocalSettings();
        if (!data.hasKey("PatternP2PUnitReturnMode") && data.hasKey(MAIN_CONFIGURATION, 10)) {
            applyLocalSettings(readSettings(data.getCompoundTag(MAIN_CONFIGURATION), localSettings));
        } else if (!data.hasKey("PatternP2PUnitOutputSlotSharingMode")
                && syncMainConfiguration && data.hasKey(MAIN_CONFIGURATION, 10)) {
            outputSlotSharingMode = readSettings(data.getCompoundTag(MAIN_CONFIGURATION), localSettings)
                    .getOutputSlotSharingMode();
        }
        lastAppliedMainConfigurationRevision = data.hasKey(MAIN_CONFIGURATION_REVISION)
                ? data.getLong(MAIN_CONFIGURATION_REVISION) : -1;
    }

    @Override public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setShort("PatternP2PFrequency", frequency);
        data.setUniqueId("PatternP2PUnitId", unitId);
        data.setBoolean("PatternP2PUnitTaskActive", taskActive);
        net.minecraft.nbt.NBTTagList pending = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i < pendingInputs.size(); i++) {
            NBTTagCompound entry = pendingInputs.get(i).writeToNBT(new NBTTagCompound());
            entry.setString("PatternP2PUnitPortType", pendingInputTypes.get(i).getId());
            entry.setInteger("PatternSlot", pendingInputSlots.get(i));
            pending.appendTag(entry);
        }
        data.setTag("PatternP2PUnitPendingInputs", pending);
        if (!primaryOutput.isEmpty()) data.setTag("PatternP2PUnitPrimaryOutput",
                primaryOutput.writeToNBT(new NBTTagCompound()));
        else data.removeTag("PatternP2PUnitPrimaryOutput");
        data.setLong("PatternP2PUnitRemainingPrimary", remainingPrimary);
        data.setInteger("PatternP2PUnitReturnMode", returnMode.getId());
        data.removeTag("PatternP2PUnitActiveReturnMode");
        data.setBoolean("PatternP2PUnitBreakRecovery", breakRecovery);
        data.setInteger("PatternP2PUnitExtractionInterval", extractionInterval);
        data.setInteger("PatternP2PUnitExtractionAmount", extractionAmount);
        net.minecraft.nbt.NBTTagList savedOutputs = new net.minecraft.nbt.NBTTagList();
        for (ItemStack output : declaredOutputs) {
            savedOutputs.appendTag(output.writeToNBT(new NBTTagCompound()));
        }
        data.setTag("PatternP2PUnitDeclaredOutputs", savedOutputs);
        data.setInteger("PatternP2PUnitRedstoneMode", redstoneMode.getId());
        data.setInteger("PatternP2PUnitRedstoneStrength", redstoneStrength);
        data.setInteger("PatternP2PUnitPulseWidth", pulseWidthTicks);
        data.setInteger("PatternP2PUnitPulsePeriod", pulsePeriodTicks);
        data.setInteger("PatternP2PUnitTransferPortOutputMode", transferPortOutputMode.getId());
        data.setInteger("PatternP2PUnitOutputSlotSharingMode", outputSlotSharingMode.getId());
        data.setInteger("PatternP2PUnitEnergyDistributionMode", energyDistributionMode.getId());
        data.setLong("PatternP2PUnitTaskRevision", taskRevision);
        net.minecraft.nbt.NBTTagList bindings = new net.minecraft.nbt.NBTTagList();
        for (java.util.Map.Entry<Integer, EnumFacing> entry : persistedSlotPortSides.entrySet()) {
            NBTTagCompound value = new NBTTagCompound();
            value.setInteger("Slot", entry.getKey());
            value.setInteger("Side", entry.getValue().getIndex());
            bindings.appendTag(value);
        }
        data.setTag("PatternP2PUnitSlotPortBindings", bindings);
        data.setBoolean("PatternP2PUnitSyncMain", syncMainConfiguration);
        data.removeTag(MAIN_CONFIGURATION);
        data.setLong(MAIN_CONFIGURATION_REVISION, lastAppliedMainConfigurationRevision);
    }

    @Override public void writeToStream(ByteBuf data) throws java.io.IOException {
        super.writeToStream(data);
        modelActive = getGridNode() != null && getGridNode().isActive();
        data.writeShort(frequency);
        data.writeLong(unitId.getMostSignificantBits());
        data.writeLong(unitId.getLeastSignificantBits());
        data.writeBoolean(modelActive);
        PatternP2PUnitSettings settings = getSettings();
        data.writeByte(settings.getReturnMode().getId());
        data.writeBoolean(settings.isBreakRecovery());
        data.writeInt(settings.getExtractionInterval());
        data.writeInt(settings.getExtractionAmount());
        data.writeByte(settings.getRedstoneMode().getId());
        data.writeByte(settings.getRedstoneStrength());
        data.writeInt(settings.getPulseWidthTicks());
        data.writeInt(settings.getPulsePeriodTicks());
        data.writeByte(settings.getTransferPortOutputMode().getId());
        data.writeByte(settings.getOutputSlotSharingMode().getId());
        data.writeByte(settings.getEnergyDistributionMode().getId());
        data.writeBoolean(syncMainConfiguration);
    }

    @Override public boolean readFromStream(ByteBuf data) throws java.io.IOException {
        boolean changed = super.readFromStream(data);
        short oldFrequency = frequency;
        UUID oldId = unitId;
        boolean oldModelActive = modelActive;
        PatternP2PUnitSettings oldSettings = getSettings();
        frequency = data.readShort();
        unitId = new UUID(data.readLong(), data.readLong());
        modelActive = data.readBoolean();
        setSettingsFromStream(new PatternP2PUnitSettings(
                cn.ae2bc.logic.ReturnMode.fromId(data.readUnsignedByte()), data.readBoolean(),
                data.readInt(), data.readInt(),
                cn.ae2bc.logic.RedstoneOutputMode.fromId(data.readUnsignedByte()),
                data.readUnsignedByte(), data.readInt(), data.readInt(),
                cn.ae2bc.core.unit.TransferPortOutputMode.fromId(data.readUnsignedByte()),
                OutputSlotSharingMode.fromId(data.readUnsignedByte()),
                EnergyDistributionMode.fromId(data.readUnsignedByte())));
        boolean oldSync = syncMainConfiguration;
        syncMainConfiguration = data.readBoolean();
        PatternP2PUnitSettings nextSettings = getSettings();
        return changed || frequency != oldFrequency
                || !unitId.equals(oldId) || oldSync != syncMainConfiguration
                || modelActive != oldModelActive
                || oldSettings.getReturnMode() != nextSettings.getReturnMode()
                || oldSettings.isBreakRecovery() != nextSettings.isBreakRecovery()
                || oldSettings.getExtractionInterval() != nextSettings.getExtractionInterval()
                || oldSettings.getExtractionAmount() != nextSettings.getExtractionAmount()
                || oldSettings.getRedstoneMode() != nextSettings.getRedstoneMode()
                || oldSettings.getRedstoneStrength() != nextSettings.getRedstoneStrength()
                || oldSettings.getPulseWidthTicks() != nextSettings.getPulseWidthTicks()
                || oldSettings.getPulsePeriodTicks() != nextSettings.getPulsePeriodTicks()
                || oldSettings.getTransferPortOutputMode() != nextSettings.getTransferPortOutputMode()
                || oldSettings.getOutputSlotSharingMode() != nextSettings.getOutputSlotSharingMode()
                || oldSettings.getEnergyDistributionMode() != nextSettings.getEnergyDistributionMode();
    }

    private void setSettingsFromStream(PatternP2PUnitSettings settings) {
        applyLocalSettings(settings);
    }

    private static PatternP2PUnitSettings readSettings(NBTTagCompound data,
            PatternP2PUnitSettings fallback) {
        return new PatternP2PUnitSettings(
                data.hasKey("ReturnMode")
                        ? cn.ae2bc.logic.ReturnMode.fromId(data.getInteger("ReturnMode"))
                        : fallback.getReturnMode(),
                data.hasKey("BreakRecovery") ? data.getBoolean("BreakRecovery")
                        : fallback.isBreakRecovery(),
                data.hasKey("ExtractionInterval") ? data.getInteger("ExtractionInterval")
                        : fallback.getExtractionInterval(),
                data.hasKey("ExtractionAmount") ? data.getInteger("ExtractionAmount")
                        : fallback.getExtractionAmount(),
                data.hasKey("RedstoneMode")
                        ? cn.ae2bc.logic.RedstoneOutputMode.fromId(data.getInteger("RedstoneMode"))
                        : fallback.getRedstoneMode(),
                data.hasKey("RedstoneStrength") ? data.getInteger("RedstoneStrength")
                        : fallback.getRedstoneStrength(),
                data.hasKey("PulseWidth") ? data.getInteger("PulseWidth")
                        : fallback.getPulseWidthTicks(),
                data.hasKey("PulsePeriod") ? data.getInteger("PulsePeriod")
                        : fallback.getPulsePeriodTicks(),
                data.hasKey("TransferPortOutputMode")
                        ? cn.ae2bc.core.unit.TransferPortOutputMode.fromId(data.getInteger("TransferPortOutputMode"))
                        : fallback.getTransferPortOutputMode(),
                data.hasKey("OutputSlotSharingMode")
                        ? OutputSlotSharingMode.fromId(data.getInteger("OutputSlotSharingMode"))
                        : fallback.getOutputSlotSharingMode(),
                data.hasKey("EnergyDistributionMode")
                        ? EnergyDistributionMode.fromId(data.getInteger("EnergyDistributionMode"))
                        : fallback.getEnergyDistributionMode());
    }

    @Override public void getDrops(List<ItemStack> drops, boolean wrenched) {
        super.getDrops(drops, wrenched);
        for (ItemStack stack : pendingInputs) if (!stack.isEmpty()) drops.add(stack.copy());
    }
}
