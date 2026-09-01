package cn.ae2bc.part;

import cn.ae2bc.item.PatternP2PUnitManagerItem;
import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import cn.ae2bc.registry.ModContent;

import java.util.Arrays;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.networking.CablePart;
import cn.ae2bc.core.frequency.FrequencyLimits;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import cn.ae2bc.core.unit.TransferPortOutputMode;
import cn.ae2bc.logic.DispatchBackoffPolicy;
import cn.ae2bc.logic.PatternP2PUnitDimensions;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.network.NetworkHooks;

/** Center cable for one Pattern P2P unit. Task/port behavior is added around this durable identity. */
public final class PatternP2PUnitManagerPart extends CablePart
        implements IGridTickable, IPowerChannelState {
    private static final String MAIN_CONFIGURATION = "PatternP2PUnitMainConfiguration";
    private static final String MAIN_CONFIGURATION_REVISION =
            "PatternP2PUnitMainConfigurationRevision";
    public static final ResourceLocation MODEL_ID = new ResourceLocation(
            "ae2_batchcraft", "part/p2p/pattern_p2p_unit_manager");
    public static final ResourceLocation GLASS_MODEL_ID = new ResourceLocation(
            "ae2_batchcraft", "part/pattern_p2p_unit_manager_glass");
    public static final ResourceLocation FREQUENCY_MODEL_ID = new ResourceLocation(
            "ae2_batchcraft", "part/pattern_p2p_unit_manager_frequency");
    private static final IPartModel MODEL = new PartModel(
            false, MODEL_ID, GLASS_MODEL_ID);

    private short frequency;
    private UUID unitId = UUID.randomUUID();
    private boolean modelActive;
    private final List<ItemStack> pendingInputs = new ArrayList<ItemStack>();
    private final List<cn.ae2bc.core.unit.UnitPortType> pendingInputTypes =
            new ArrayList<cn.ae2bc.core.unit.UnitPortType>();
    private final List<Integer> pendingInputSlots = new ArrayList<Integer>();
    private final java.util.Map<PatternP2PUnitPortPart, Integer> dispatchPortSlots =
            new java.util.IdentityHashMap<>();
    private final java.util.Map<PatternP2PUnitPortPart, ItemStack> dispatchPortTypes =
            new java.util.IdentityHashMap<>();
    private final java.util.Map<Integer, PatternP2PUnitPortPart> dispatchSlotPorts =
            new java.util.HashMap<>();
    private final java.util.Map<Integer, Direction> persistedSlotPortSides =
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
    private TransferPortOutputMode transferPortOutputMode = TransferPortOutputMode.NORMAL;
    private OutputSlotSharingMode outputSlotSharingMode = OutputSlotSharingMode.DISABLED;
    private long taskRevision;
    private boolean syncMainConfiguration = true;
    private PatternP2PUnitSettings mainConfiguration = PatternP2PUnitSettings.DEFAULT;
    private long mainConfigurationRevision = -1;
    private int pendingRetryFailures;
    private long pendingNextRetryTick;

    public PatternP2PUnitManagerPart(ItemStack stack) {
        super(stack);
        getProxy().setFlags(GridFlags.PREFERRED);
        getProxy().setIdlePowerUsage(1.0);
    }

    @PartModels public static List<IPartModel> getModels() { return Arrays.asList(MODEL); }
    @Override public IPartModel getStaticModels() { return MODEL; }
    @Override public AECableType getCableConnectionType() { return AECableType.SMART; }

    @Override
    public boolean changeColor(AEColor color, PlayerEntity player) {
        if (color == null || color == getCableColor()) return false;
        if (isRemote()) return true;
        getProxy().setColor(color);
        getProxy().setVisualRepresentation(coloredStack());
        getHost().markForSave();
        getHost().markForUpdate();
        getHost().partChanged();
        return true;
    }

    @Override public ItemStack getItemStack() { return coloredStack(); }
    @Override public ItemStack getItemStack(PartItemStack type) { return coloredStack(); }

    /** The manager has its own memory-card schema and must bypass AE2's P2P handler. */
    @Override public boolean useStandardMemoryCard() { return false; }

    private ItemStack coloredStack() {
        return new ItemStack(ModContent.getUnitManagerItem(getCableColor()));
    }

    @Override
    public net.minecraftforge.client.model.data.IModelData getModelData() {
        long value = Short.toUnsignedLong(frequency);
        if (modelActive) value |= 0x10000L;
        return new P2PTunnelFrequencyModelData(value);
    }

    public int getFrequencyUnsigned() { return Short.toUnsignedInt(frequency); }
    public short getFrequency() { return frequency; }
    public UUID getUnitId() { return unitId; }
    public boolean hasConfiguredFrequency() { return frequency != 0; }
    @Override public boolean isPowered() {
        return isRemote() ? modelActive : getProxy().isPowered();
    }
    @Override public boolean isActive() {
        return isRemote() ? modelActive : getGridNode() != null && getGridNode().isActive();
    }
    /** The manager is usable by bound ports only while its AE2 node is active. */
    public boolean isOperational() { return hasConfiguredFrequency() && getGridNode() != null && getGridNode().isActive(); }
    public boolean canAcceptTask() {
        if (syncMainConfiguration && getTile() != null && getTile().getLevel() != null
                && !getTile().getLevel().isClientSide) synchronizeFromInput();
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
    public OutputSlotSharingMode getOutputSlotSharingMode() { return getEffectiveSettings().getOutputSlotSharingMode(); }
    public long getTaskRevision() { return taskRevision; }
    public boolean isSyncMainConfiguration() { return syncMainConfiguration; }
    public void setSyncMainConfiguration(boolean value) {
        if (syncMainConfiguration == value) return;
        if (!value) applyLocalSettings(getEffectiveSettings());
        syncMainConfiguration = value;
        if (value) {
            inputCacheTick = Long.MIN_VALUE;
            inputCache = null;
            synchronizeFromInput();
        }
        getHost().markForSave();
        getHost().markForUpdate();
        alertPendingRetry();
        wakeBoundPorts();
    }

    public PatternP2PUnitSettings getSettings() {
        return getEffectiveSettings();
    }

    public void setSettings(PatternP2PUnitSettings settings) {
        if (settings == null || syncMainConfiguration) return;
        applyLocalSettings(settings);
        getHost().markForSave();
        getHost().markForUpdate();
        alertPendingRetry();
        wakeBoundPorts();
    }

    private PatternP2PUnitSettings getLocalSettings() {
        return new PatternP2PUnitSettings(returnMode, breakRecovery, extractionInterval,
                extractionAmount, redstoneMode, redstoneStrength, pulseWidthTicks, pulsePeriodTicks,
                transferPortOutputMode, outputSlotSharingMode);
    }

    private PatternP2PUnitSettings getEffectiveSettings() {
        return syncMainConfiguration && mainConfiguration != null ? mainConfiguration : getLocalSettings();
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
    }

    public void applyMainConfiguration(PatternP2PUnitSettings settings, long revision) {
        if (settings == null) return;
        if (mainConfigurationRevision == revision && sameSettings(mainConfiguration, settings)) return;
        mainConfiguration = settings;
        mainConfigurationRevision = revision;
        getHost().markForSave();
        getHost().markForUpdate();
        alertPendingRetry();
        wakeBoundPorts();
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
        dispatchPortTypes.clear();
        dispatchSlotPorts.clear();
        persistedSlotPortSides.clear();
        resetPendingRetryBackoff();
        taskRevision++;
        getHost().markForSave();
        getHost().markForUpdate();
        invalidateBoundPortRuntimeState();
    }

    public cn.ae2bc.logic.EnergyDistributionMode getEnergyDistributionMode() {
        for (PatternP2PTunnelEnergyPart energy
                : PatternP2PTopologyGridService.findEnergyParts(getGridNode())) {
            return energy.getDistributionMode();
        }
        return cn.ae2bc.logic.EnergyDistributionMode.EVEN;
    }

    public void setEnergyDistributionMode(cn.ae2bc.logic.EnergyDistributionMode mode) {
        if (mode == null) return;
        for (PatternP2PTunnelEnergyPart energy
                : PatternP2PTopologyGridService.findEnergyParts(getGridNode())) {
            energy.setSettings(energy.isPullEnabled(), mode);
        }
    }

    /** Admission is simulation-only; the input table is mutated only by acceptInputs after all probes pass. */
    public boolean canAcceptInput(ItemStack stack, cn.ae2bc.core.unit.UnitPortType targetType) {
        if (!canAcceptTask() || stack == null || stack.isEmpty()) return false;
        PatternP2PUnitPortPart port = findInputPort(stack, targetType, -1, null, true);
        return port != null;
    }

    public boolean acceptInputs(List<ItemStack> inputs, List<cn.ae2bc.core.unit.UnitPortType> targetTypes,
            List<Integer> inputSlots,
            ItemStack primaryOutput, long primaryAmount, List<ItemStack> outputs) {
        if (!canAcceptTask() || inputs == null || inputs.isEmpty()
                || targetTypes == null || targetTypes.size() != inputs.size()
                || inputSlots == null || inputSlots.size() != inputs.size()) return false;
        if (!canAcceptInputsAggregate(inputs, targetTypes, inputSlots)) return false;
        if (!canAcceptTask()) return false;
        synchronizeFromInput(true);
        pendingInputs.clear();
        pendingInputTypes.clear();
        pendingInputSlots.clear();
        for (ItemStack stack : inputs) pendingInputs.add(stack.copy());
        pendingInputTypes.addAll(targetTypes);
        pendingInputSlots.addAll(inputSlots);
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
        taskRevision++;
        taskActive = true;
        dispatchPortSlots.clear();
        dispatchPortTypes.clear();
        dispatchSlotPorts.clear();
        persistedSlotPortSides.clear();
        resetPendingRetryBackoff();
        getHost().markForSave();
        wake();
        wakeBoundPorts();
        return true;
    }

    private PatternP2PUnitPortPart findInputPort(ItemStack stack, cn.ae2bc.core.unit.UnitPortType targetType,
            int slot, java.util.Map<PatternP2PUnitPortPart, Integer> owners, boolean simulate) {
        java.util.List<PatternP2PUnitPortPart> ports = new ArrayList<>(PatternP2PTopologyGridService.findPorts(getGridNode(), unitId));
        ports.removeIf(port -> port.getPortType() != targetType);
        ports.removeIf(port -> !port.matchesInput(this, stack, formForPortType(targetType)));
        Direction persistedSide = persistedSlotPortSides.get(slot);
        if (persistedSide != null) {
            for (PatternP2PUnitPortPart port : ports) {
                if (port.getSide().getFacing() == persistedSide) {
                    dispatchSlotPorts.putIfAbsent(slot, port);
                    dispatchPortSlots.putIfAbsent(port, slot);
                    break;
                }
            }
        }
        ports.removeIf(port -> owners != null && !canUsePortForSlot(slot, port, owners, dispatchSlotPorts));
        ports.sort((left, right) -> Integer.compare(right.getTransferPriority(), left.getTransferPriority()));
        for (PatternP2PUnitPortPart port : ports) {
            ItemStack assignedType = dispatchPortTypes.get(port);
            if (owners != null
                    && getEffectiveSettings().getTransferPortOutputMode()
                            == cn.ae2bc.core.unit.TransferPortOutputMode.SAME_TYPE
                    && assignedType != null && !sameItem(assignedType, stack)) continue;
            int amount = port.insertTaskInput(this, stack, simulate);
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
            List<cn.ae2bc.core.unit.UnitPortType> targetTypes, List<Integer> inputSlots) {
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
            java.util.Map<PatternP2PUnitPortPart, ItemStack> assignedTypes = new java.util.IdentityHashMap<>();
            cn.ae2bc.core.unit.TransferPortOutputMode outputMode = getEffectiveSettings().getTransferPortOutputMode();
            for (PatternP2PUnitPortPart port : ports) remaining.put(port, Integer.MAX_VALUE);
            for (Integer index : indexes) {
                int slot = inputSlots.get(index);
                ItemStack stack = inputs.get(index).copy();
                int left = stack.getCount();
                for (PatternP2PUnitPortPart port : candidatePorts(ports, stack, formForPortType(type))) {
                    if (left <= 0) break;
                    if (!canUsePortForSlot(slot, port, assignedSlots, assignedSlotPorts)) continue;
                    if (outputMode == cn.ae2bc.core.unit.TransferPortOutputMode.SAME_TYPE
                            && assignedTypes.containsKey(port)
                            && !sameItem(assignedTypes.get(port), stack)) continue;
                    int simulated = port.insertTaskInput(this, stack, true);
                    if (simulated <= 0) continue;
                    int capacity = remaining.get(port);
                    if (type == cn.ae2bc.core.unit.UnitPortType.PLACE
                            && !port.getEffectiveSingleSlot()) {
                        // A placement target only accepts one block per attempt, not one
                        // block for the lifetime of the task. A shared port may retain
                        // materials from multiple encoded slots and place them as its
                        // target becomes available again.
                        capacity = Integer.MAX_VALUE;
                    } else if (type == cn.ae2bc.core.unit.UnitPortType.PLACE) {
                        capacity = Math.min(capacity, 1);
                    } else if (type == cn.ae2bc.core.unit.UnitPortType.TRANSFER
                            && capacity == Integer.MAX_VALUE) {
                        capacity = port.estimateTransferCapacity(stack, simulated);
                        remaining.put(port, capacity);
                    }
                    if (outputMode == cn.ae2bc.core.unit.TransferPortOutputMode.SINGLE_ITEM) capacity = Math.min(capacity, 1);
                    int accepted = Math.min(left, Math.min(simulated, capacity));
                    if (accepted <= 0) continue;
                    left -= accepted;
                    if (capacity != Integer.MAX_VALUE) remaining.put(port, capacity - accepted);
                    if (outputMode == cn.ae2bc.core.unit.TransferPortOutputMode.SAME_TYPE) {
                        ItemStack identity = stack.copy();
                        identity.setCount(1);
                        assignedTypes.put(port, identity);
                    }
                    assignedSlots.putIfAbsent(port, slot);
                    if (port.getEffectiveSingleSlot()) {
                        assignedSlotPorts.putIfAbsent(slot, port);
                        // Once claimed, the remainder of this encoded slot must wait for this port.
                        break;
                    }
                }
                if (left > 0 && (slot < 0 || !assignedSlotPorts.containsKey(slot))) return false;
            }
        }
        return true;
    }

    private boolean dispatchPending() {
        boolean changed = false;
        for (ListIterator<ItemStack> iterator = pendingInputs.listIterator(); iterator.hasNext();) {
            int index = iterator.nextIndex();
            ItemStack stack = iterator.next();
            cn.ae2bc.core.unit.UnitPortType targetType = pendingInputTypes.get(index);
            int slot = pendingInputSlots.get(index);
            List<PatternP2PUnitPortPart> ports = new ArrayList<>(
                    PatternP2PTopologyGridService.findPorts(getGridNode(), unitId));
            ports.removeIf(port -> port.getPortType() != targetType);
            List<PatternP2PUnitPortPart> candidates = candidatePorts(
                    ports, stack, formForPortType(targetType));
            restorePersistedSlotPort(slot, candidates);

            int remaining = stack.getCount();
            boolean insertedAny = false;
            for (PatternP2PUnitPortPart port : candidates) {
                if (remaining <= 0 || !canUsePortForSlot(
                        slot, port, dispatchPortSlots, dispatchSlotPorts)) continue;
                ItemStack assignedType = dispatchPortTypes.get(port);
                if (getEffectiveSettings().getTransferPortOutputMode()
                        == cn.ae2bc.core.unit.TransferPortOutputMode.SAME_TYPE
                        && assignedType != null && !sameItem(assignedType, stack)) continue;

                ItemStack attempt = stack.copy();
                attempt.setCount(remaining);
                int moved = Math.min(remaining, port.insertTaskInput(this, attempt, false));
                if (moved <= 0) {
                    // A target may reject a real placement after its simulation passed.
                    // Keep priority ordering, but allow the next eligible port to handle it.
                    continue;
                }
                remaining -= moved;
                insertedAny = true;
                dispatchPortSlots.putIfAbsent(port, slot);
                rememberDispatchedType(port, stack);
                if (port.getEffectiveSingleSlot()) {
                    dispatchSlotPorts.putIfAbsent(slot, port);
                    persistedSlotPortSides.put(slot, port.getSide().getFacing());
                }
            }

            if (remaining <= 0) {
                iterator.remove();
                pendingInputTypes.remove(index);
                pendingInputSlots.remove(index);
                changed = true;
            } else if (insertedAny) {
                stack.setCount(remaining);
                changed = true;
            }
        }
        if (finishTaskIfComplete()) changed = true;
        if (changed) {
            getHost().markForSave();
            getHost().markForUpdate();
        }
        return changed;
    }

    private void restorePersistedSlotPort(int slot, List<PatternP2PUnitPortPart> candidates) {
        Direction persistedSide = persistedSlotPortSides.get(slot);
        if (persistedSide == null) return;
        for (PatternP2PUnitPortPart port : candidates) {
            if (port.getSide() != null && port.getSide().getFacing() == persistedSide) {
                dispatchSlotPorts.putIfAbsent(slot, port);
                dispatchPortSlots.putIfAbsent(port, slot);
                return;
            }
        }
    }

    private void rememberDispatchedType(PatternP2PUnitPortPart port, ItemStack stack) {
        if (getEffectiveSettings().getTransferPortOutputMode()
                != cn.ae2bc.core.unit.TransferPortOutputMode.SAME_TYPE) return;
        ItemStack identity = stack.copy();
        identity.setCount(1);
        dispatchPortTypes.putIfAbsent(port, identity);
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
        PatternP2PTunnelPart input = findOperationalInput();
        if (input == null) return stack;
        ItemStack remainder = input.returnToAdjacent(stack, simulate);
        int accepted = stack.getCount() - remainder.getCount();
        if (!simulate && accepted > 0 && sameItem(stack, primaryOutput)) {
            remainingPrimary = Math.max(0, remainingPrimary - accepted);
            finishTaskIfComplete();
            getHost().markForSave();
        }
        return remainder;
    }

    private PatternP2PTunnelPart findInput() {
        if (getGridNode() == null || getTile().getLevel() == null) return null;
        long tick = getTile().getLevel().getGameTime();
        if (inputCacheTick == tick) return inputCache;
        inputCacheTick = tick;
        inputCache = PatternP2PTopologyGridService.findInput(getGridNode(), frequency);
        return inputCache;
    }

    private PatternP2PTunnelPart findOperationalInput() {
        PatternP2PTunnelPart input = findInput();
        return input != null && input.getGridNode() != null && input.getGridNode().isActive()
                ? input : null;
    }

    private void invalidateInputCache() {
        inputCacheTick = Long.MIN_VALUE;
        inputCache = null;
    }

    public void synchronizeFromInput() {
        synchronizeFromInput(false);
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
        dispatchPortTypes.clear();
        dispatchSlotPorts.clear();
        persistedSlotPortSides.clear();
        resetPendingRetryBackoff();
        invalidateBoundPortRuntimeState();
        return true;
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return !left.isEmpty() && !right.isEmpty()
                && ItemStack.isSame(left, right) && ItemStack.tagMatches(left, right);
    }

    private static boolean sameSettings(PatternP2PUnitSettings left, PatternP2PUnitSettings right) {
        return left != null && right != null
                && left.getReturnMode() == right.getReturnMode()
                && left.isBreakRecovery() == right.isBreakRecovery()
                && left.getExtractionInterval() == right.getExtractionInterval()
                && left.getExtractionAmount() == right.getExtractionAmount()
                && left.getRedstoneMode() == right.getRedstoneMode()
                && left.getRedstoneStrength() == right.getRedstoneStrength()
                && left.getPulseWidthTicks() == right.getPulseWidthTicks()
                && left.getPulsePeriodTicks() == right.getPulsePeriodTicks()
                && left.getTransferPortOutputMode() == right.getTransferPortOutputMode()
                && left.getOutputSlotSharingMode() == right.getOutputSlotSharingMode();
    }

    private static boolean containsSameItem(List<ItemStack> stacks, ItemStack candidate) {
        for (ItemStack stack : stacks) if (sameItem(stack, candidate)) return true;
        return false;
    }

    private void wake() {
        try { getProxy().getTick().wakeDevice(getGridNode()); }
        catch (appeng.me.GridAccessException ignored) { }
    }

    public void alertPendingRetry() {
        resetPendingRetryBackoff();
        try { getProxy().getTick().alertDevice(getGridNode()); }
        catch (appeng.me.GridAccessException ignored) { }
    }

    private void resetPendingRetryBackoff() {
        pendingRetryFailures = 0;
        pendingNextRetryTick = 0;
    }

    private long getGameTime() {
        return getTile() == null || getTile().getLevel() == null
                ? 0 : getTile().getLevel().getGameTime();
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private void wakeBoundPorts() {
        for (PatternP2PUnitPortPart port
                : PatternP2PTopologyGridService.findPorts(getGridNode(), unitId)) {
            port.alertTicking();
            // Effective settings (including single-slot sharing) are read from the
            // manager by the port, so notify clients when the manager changes.
            port.getHost().markForUpdate();
        }
    }

    private void invalidateBoundPortRuntimeState() {
        for (PatternP2PUnitPortPart port
                : PatternP2PTopologyGridService.findPorts(getGridNode(), unitId)) {
            port.invalidateTaskRuntimeState();
        }
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, DispatchBackoffPolicy.MAX_PENDING_DELAY, false, true);
    }

    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        refreshModelState();
        if (!isOperational()) return TickRateModulation.SLEEP;
        if (pendingInputs.isEmpty()) {
            resetPendingRetryBackoff();
            return TickRateModulation.SLEEP;
        }
        long now = getGameTime();
        if (now < pendingNextRetryTick) return TickRateModulation.SLOWER;
        if (dispatchPending()) {
            resetPendingRetryBackoff();
            return TickRateModulation.URGENT;
        }
        pendingRetryFailures = Math.min(Integer.MAX_VALUE, pendingRetryFailures + 1);
        pendingNextRetryTick = saturatingAdd(now,
                DispatchBackoffPolicy.pendingDelay(pendingRetryFailures));
        return TickRateModulation.SLOWER;
    }

    @Override public void addToWorld() {
        super.addToWorld();
        if (isRemote()) return;
        PatternP2PTopologyGridService.invalidate(getGridNode());
        invalidateInputCache();
        synchronizeFromInput();
        wake();
        wakeBoundPorts();
    }

    @Override public void removeFromWorld() {
        if (!isRemote()) {
            PatternP2PTopologyGridService.invalidate(getGridNode());
            invalidateInputCache();
        }
        super.removeFromWorld();
    }

    @Override public void gridChanged() {
        super.gridChanged();
        if (isRemote()) return;
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
        if (isRemote()) return;
        boolean active = getGridNode() != null && getGridNode().isActive();
        if (modelActive == active) return;
        modelActive = active;
        getHost().markForUpdate();
    }

    public void setFrequency(int value) {
        if (isTaskActive()) return;
        short next = (short) FrequencyLimits.clamp(value);
        if (frequency == next) return;
        frequency = next;
        PatternP2PTopologyGridService.invalidate(getGridNode());
        invalidateInputCache();
        getHost().markForSave();
        getHost().markForUpdate();
        getHost().partChanged();
        synchronizeFromInput();
        wakeBoundPorts();
    }

    @Override public void getBoxes(IPartCollisionHelper helper) {
        updateConnections();
        helper.addBox(PatternP2PUnitDimensions.FRAME_MIN, PatternP2PUnitDimensions.FRAME_MIN,
                PatternP2PUnitDimensions.FRAME_MIN, PatternP2PUnitDimensions.FRAME_MAX,
                PatternP2PUnitDimensions.FRAME_MAX, PatternP2PUnitDimensions.FRAME_MAX);

        if (getHost() != null) {
            for (Direction side : Direction.values()) {
                IPart part = getHost().getPart(side);
                if (part instanceof IGridHost) {
                    addConnectionBox(helper, side,
                            part.getCableConnectionLength(getCableConnectionType()));
                }
            }
        }
        for (Direction side : Direction.values()) {
            if (isConnected(side)) addConnectionBox(helper, side, 0.0);
        }
    }

    private static void addConnectionBox(IPartCollisionHelper helper, Direction side, double length) {
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

    @Override
    public boolean onPartActivate(PlayerEntity player, Hand hand, Vector3d hitPos) {
        if (handleMemoryCard(player, hand, false)) return true;
        if (hand == Hand.MAIN_HAND) {
            if (!player.level.isClientSide && player instanceof ServerPlayerEntity) {
                NetworkHooks.openGui((ServerPlayerEntity) player, new SimpleNamedContainerProvider(
                        (id, inventory, ignored) -> new PatternP2PUnitManagerMenu(id, inventory, this),
                        new TranslationTextComponent("item.ae2_batchcraft.pattern_p2p_unit_manager")), buffer -> {
                            buffer.writeBlockPos(getTile().getBlockPos());
                            buffer.writeInt(getFrequencyUnsigned());
                            buffer.writeUUID(unitId);
                            buffer.writeBoolean(syncMainConfiguration);
                            buffer.writeByte(getEnergyDistributionMode().getId());
                            PatternP2PUnitSettings settings = getSettings();
                            buffer.writeByte(settings.getReturnMode().getId());
                            buffer.writeBoolean(settings.isBreakRecovery());
                            buffer.writeInt(settings.getExtractionInterval());
                            buffer.writeInt(settings.getExtractionAmount());
                            buffer.writeByte(settings.getRedstoneMode().getId());
                            buffer.writeByte(settings.getRedstoneStrength());
                            buffer.writeInt(settings.getPulseWidthTicks());
                            buffer.writeInt(settings.getPulsePeriodTicks());
                            buffer.writeByte(settings.getTransferPortOutputMode().getId());
                            buffer.writeByte(settings.getOutputSlotSharingMode().getId());
                        });
            }
            return true;
        }
        return super.onPartActivate(player, hand, hitPos);
    }

    @Override public boolean onPartShiftActivate(PlayerEntity player, Hand hand, Vector3d hitPos) {
        return handleMemoryCard(player, hand, true)
                || super.onPartShiftActivate(player, hand, hitPos);
    }

    private boolean handleMemoryCard(PlayerEntity player, Hand hand, boolean save) {
        ItemStack held = player.getItemInHand(hand);
        if (hand != Hand.MAIN_HAND || !(held.getItem() instanceof IMemoryCard)) return false;
        if (player.level.isClientSide) return true;
        IMemoryCard card = (IMemoryCard) held.getItem();
        if (save) {
            CompoundNBT data = new CompoundNBT();
            // Store the actual manager item so AE2 can identify the card on all 1.16.5 builds.
            coloredStack().save(data);
            data.putShort("freq", frequency);
            data.putUUID("PatternP2PUnitId", unitId);
            card.setMemoryCardContents(held,
                    "item.ae2_batchcraft.pattern_p2p_unit_manager", data);
            card.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
        } else {
            CompoundNBT data = card.getData(held);
            if (!data.contains("freq") || !isSupportedMemoryCardSource(data)) {
                card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            } else {
                setFrequency(Short.toUnsignedInt(data.getShort("freq")));
                card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            }
        }
        return true;
    }

    private static boolean isSupportedMemoryCardSource(CompoundNBT data) {
        // The UUID is our durable card marker. Accept it first because older AE2
        // versions disagree on how custom part stacks are reconstructed from NBT.
        if (data.hasUUID("PatternP2PUnitId")) return true;
        ItemStack source = ItemStack.of(data);
        if (source.isEmpty()) {
            return false;
        }
        if (source.getItem() == ModContent.PATTERN_P2P_INPUT.get()) return true;
        for (net.minecraftforge.fml.RegistryObject<PatternP2PUnitManagerItem<PatternP2PUnitManagerPart>> item
                : ModContent.UNIT_MANAGERS.values()) {
            if (source.getItem() == item.get()) return true;
        }
        return false;
    }

    @Override public void readFromNBT(CompoundNBT data) {
        super.readFromNBT(data);
        resetPendingRetryBackoff();
        frequency = data.getShort("PatternP2PFrequency");
        inputCacheTick = Long.MIN_VALUE;
        inputCache = null;
        unitId = data.hasUUID("PatternP2PUnitId") ? data.getUUID("PatternP2PUnitId") : UUID.randomUUID();
        taskActive = data.getBoolean("PatternP2PUnitTaskActive");
        pendingInputs.clear();
        pendingInputTypes.clear();
        pendingInputSlots.clear();
        dispatchPortSlots.clear();
        dispatchPortTypes.clear();
        dispatchSlotPorts.clear();
        net.minecraft.nbt.ListNBT pending = data.getList("PatternP2PUnitPendingInputs", 10);
        for (int i = 0; i < pending.size(); i++) {
            CompoundNBT entry = pending.getCompound(i);
            ItemStack stack = ItemStack.of(entry);
            if (!stack.isEmpty()) {
                pendingInputs.add(stack);
                try { pendingInputTypes.add(cn.ae2bc.core.unit.UnitPortType.fromId(
                        entry.getString("PatternP2PUnitPortType"))); }
                catch (IllegalArgumentException ignored) {
                    pendingInputTypes.add(cn.ae2bc.core.unit.UnitPortType.TRANSFER);
                }
                pendingInputSlots.add(entry.contains("PatternSlot")
                        ? entry.getInt("PatternSlot") : i);
            }
        }
        primaryOutput = data.contains("PatternP2PUnitPrimaryOutput")
                ? ItemStack.of(data.getCompound("PatternP2PUnitPrimaryOutput")) : ItemStack.EMPTY;
        remainingPrimary = Math.max(0, data.getLong("PatternP2PUnitRemainingPrimary"));
        returnMode = data.contains("PatternP2PUnitReturnMode")
                ? cn.ae2bc.logic.ReturnMode.fromId(data.getInt("PatternP2PUnitReturnMode"))
                : cn.ae2bc.logic.ReturnMode.UNBLOCKED;
        breakRecovery = !data.contains("PatternP2PUnitBreakRecovery")
                || data.getBoolean("PatternP2PUnitBreakRecovery");
        extractionInterval = data.contains("PatternP2PUnitExtractionInterval")
                ? cn.ae2bc.core.extraction.ProductExtractionLimits.clampInterval(
                        data.getInt("PatternP2PUnitExtractionInterval"))
                : cn.ae2bc.core.extraction.ProductExtractionLimits.DEFAULT_INTERVAL;
        extractionAmount = data.contains("PatternP2PUnitExtractionAmount")
                ? cn.ae2bc.core.extraction.ProductExtractionLimits.clampAmount(
                        data.getInt("PatternP2PUnitExtractionAmount"))
                : cn.ae2bc.core.extraction.ProductExtractionLimits.DEFAULT_AMOUNT;
        declaredOutputs.clear();
        net.minecraft.nbt.ListNBT savedOutputs = data.getList("PatternP2PUnitDeclaredOutputs", 10);
        for (int i = 0; i < savedOutputs.size(); i++) {
            ItemStack output = ItemStack.of(savedOutputs.getCompound(i));
            if (!output.isEmpty()) declaredOutputs.add(output);
        }
        redstoneMode = cn.ae2bc.logic.RedstoneOutputMode.fromId(data.getInt("PatternP2PUnitRedstoneMode"));
        redstoneStrength = data.contains("PatternP2PUnitRedstoneStrength")
                ? Math.max(0, Math.min(15, data.getInt("PatternP2PUnitRedstoneStrength"))) : 15;
        pulsePeriodTicks = data.contains("PatternP2PUnitPulsePeriod")
                ? Math.max(1, data.getInt("PatternP2PUnitPulsePeriod")) : 20;
        pulseWidthTicks = data.contains("PatternP2PUnitPulseWidth")
                ? Math.max(1, Math.min(pulsePeriodTicks, data.getInt("PatternP2PUnitPulseWidth"))) : 2;
        transferPortOutputMode = data.contains("PatternP2PUnitTransferPortOutputMode")
                ? TransferPortOutputMode.fromId(data.getInt("PatternP2PUnitTransferPortOutputMode"))
                : TransferPortOutputMode.NORMAL;
        taskRevision = data.getLong("PatternP2PUnitTaskRevision");
        persistedSlotPortSides.clear();
        net.minecraft.nbt.ListNBT bindings = data.getList("PatternP2PUnitSlotPortBindings", 10);
        for (int i = 0; i < bindings.size(); i++) {
            CompoundNBT entry = bindings.getCompound(i);
            if (entry.contains("Slot") && entry.contains("Side")) {
                persistedSlotPortSides.put(entry.getInt("Slot"),
                        Direction.from3DDataValue(entry.getInt("Side")));
            }
        }
        syncMainConfiguration = !data.contains("PatternP2PUnitSyncMain")
                || data.getBoolean("PatternP2PUnitSyncMain");
        PatternP2PUnitSettings localSettings = getLocalSettings();
        mainConfiguration = data.contains(MAIN_CONFIGURATION, 10)
                ? readSettings(data.getCompound(MAIN_CONFIGURATION), localSettings)
                : localSettings;
        mainConfigurationRevision = data.contains(MAIN_CONFIGURATION_REVISION)
                ? data.getLong(MAIN_CONFIGURATION_REVISION) : -1;
    }

    @Override public void writeToNBT(CompoundNBT data) {
        super.writeToNBT(data);
        data.putShort("PatternP2PFrequency", frequency);
        data.putUUID("PatternP2PUnitId", unitId);
        data.putBoolean("PatternP2PUnitTaskActive", taskActive);
        net.minecraft.nbt.ListNBT pending = new net.minecraft.nbt.ListNBT();
        for (int i = 0; i < pendingInputs.size(); i++) {
            CompoundNBT entry = pendingInputs.get(i).save(new CompoundNBT());
            entry.putString("PatternP2PUnitPortType", pendingInputTypes.get(i).getId());
            entry.putInt("PatternSlot", pendingInputSlots.get(i));
            pending.add(entry);
        }
        data.put("PatternP2PUnitPendingInputs", pending);
        if (!primaryOutput.isEmpty()) data.put("PatternP2PUnitPrimaryOutput", primaryOutput.save(new CompoundNBT()));
        else data.remove("PatternP2PUnitPrimaryOutput");
        data.putLong("PatternP2PUnitRemainingPrimary", remainingPrimary);
        data.putInt("PatternP2PUnitReturnMode", returnMode.getId());
        data.remove("PatternP2PUnitActiveReturnMode");
        data.putBoolean("PatternP2PUnitBreakRecovery", breakRecovery);
        data.putInt("PatternP2PUnitExtractionInterval", extractionInterval);
        data.putInt("PatternP2PUnitExtractionAmount", extractionAmount);
        net.minecraft.nbt.ListNBT savedOutputs = new net.minecraft.nbt.ListNBT();
        for (ItemStack output : declaredOutputs) savedOutputs.add(output.save(new CompoundNBT()));
        data.put("PatternP2PUnitDeclaredOutputs", savedOutputs);
        data.putInt("PatternP2PUnitRedstoneMode", redstoneMode.getId());
        data.putInt("PatternP2PUnitRedstoneStrength", redstoneStrength);
        data.putInt("PatternP2PUnitPulseWidth", pulseWidthTicks);
        data.putInt("PatternP2PUnitPulsePeriod", pulsePeriodTicks);
        data.putInt("PatternP2PUnitTransferPortOutputMode", transferPortOutputMode.getId());
        data.putLong("PatternP2PUnitTaskRevision", taskRevision);
        net.minecraft.nbt.ListNBT bindings = new net.minecraft.nbt.ListNBT();
        for (java.util.Map.Entry<Integer, Direction> entry : persistedSlotPortSides.entrySet()) {
            CompoundNBT value = new CompoundNBT();
            value.putInt("Slot", entry.getKey());
            value.putInt("Side", entry.getValue().get3DDataValue());
            bindings.add(value);
        }
        data.put("PatternP2PUnitSlotPortBindings", bindings);
        data.putBoolean("PatternP2PUnitSyncMain", syncMainConfiguration);
        data.put(MAIN_CONFIGURATION, writeSettings(
                mainConfiguration != null ? mainConfiguration : getLocalSettings()));
        data.putLong(MAIN_CONFIGURATION_REVISION, mainConfigurationRevision);
    }

    private static CompoundNBT writeSettings(PatternP2PUnitSettings settings) {
        CompoundNBT data = new CompoundNBT();
        data.putInt("ReturnMode", settings.getReturnMode().getId());
        data.putBoolean("BreakRecovery", settings.isBreakRecovery());
        data.putInt("ExtractionInterval", settings.getExtractionInterval());
        data.putInt("ExtractionAmount", settings.getExtractionAmount());
        data.putInt("RedstoneMode", settings.getRedstoneMode().getId());
        data.putInt("RedstoneStrength", settings.getRedstoneStrength());
        data.putInt("PulseWidth", settings.getPulseWidthTicks());
        data.putInt("PulsePeriod", settings.getPulsePeriodTicks());
        data.putInt("TransferPortOutputMode", settings.getTransferPortOutputMode().getId());
        data.putInt("OutputSlotSharingMode", settings.getOutputSlotSharingMode().getId());
        return data;
    }

    private static PatternP2PUnitSettings readSettings(CompoundNBT data,
            PatternP2PUnitSettings fallback) {
        return new PatternP2PUnitSettings(
                data.contains("ReturnMode")
                        ? cn.ae2bc.logic.ReturnMode.fromId(data.getInt("ReturnMode"))
                        : fallback.getReturnMode(),
                data.contains("BreakRecovery") ? data.getBoolean("BreakRecovery")
                        : fallback.isBreakRecovery(),
                data.contains("ExtractionInterval") ? data.getInt("ExtractionInterval")
                        : fallback.getExtractionInterval(),
                data.contains("ExtractionAmount") ? data.getInt("ExtractionAmount")
                        : fallback.getExtractionAmount(),
                data.contains("RedstoneMode")
                        ? cn.ae2bc.logic.RedstoneOutputMode.fromId(data.getInt("RedstoneMode"))
                        : fallback.getRedstoneMode(),
                data.contains("RedstoneStrength") ? data.getInt("RedstoneStrength")
                        : fallback.getRedstoneStrength(),
                data.contains("PulseWidth") ? data.getInt("PulseWidth")
                        : fallback.getPulseWidthTicks(),
                data.contains("PulsePeriod") ? data.getInt("PulsePeriod")
                        : fallback.getPulsePeriodTicks(),
                data.contains("TransferPortOutputMode")
                        ? cn.ae2bc.core.unit.TransferPortOutputMode.fromId(data.getInt("TransferPortOutputMode"))
                        : fallback.getTransferPortOutputMode(),
                data.contains("OutputSlotSharingMode")
                        ? OutputSlotSharingMode.fromId(data.getInt("OutputSlotSharingMode"))
                        : fallback.getOutputSlotSharingMode());
    }

    @Override public void writeToStream(PacketBuffer data) throws java.io.IOException {
        super.writeToStream(data);
        modelActive = getGridNode() != null && getGridNode().isActive();
        data.writeShort(frequency);
        data.writeUUID(unitId);
        data.writeBoolean(modelActive);
    }

    @Override public boolean readFromStream(PacketBuffer data) throws java.io.IOException {
        boolean changed = super.readFromStream(data);
        short oldFrequency = frequency;
        UUID oldId = unitId;
        boolean oldModelActive = modelActive;
        frequency = data.readShort();
        unitId = data.readUUID();
        modelActive = data.readBoolean();
        return changed || frequency != oldFrequency || !unitId.equals(oldId)
                || modelActive != oldModelActive;
    }

    @Override public void getDrops(List<ItemStack> drops, boolean wrenched) {
        super.getDrops(drops, wrenched);
        for (ItemStack stack : pendingInputs) if (!stack.isEmpty()) drops.add(stack.copy());
    }
}
