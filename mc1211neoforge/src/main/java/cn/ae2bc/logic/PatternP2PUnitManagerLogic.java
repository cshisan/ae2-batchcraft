package cn.ae2bc.logic;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.me.helpers.MachineSource;
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import cn.ae2bc.pattern.MaterialOutputForm;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Comparator;
import cn.ae2bc.core.unit.TransferPortOutputMode;

/** Owns one durable unit task and gates every bound port while that task is active. */
public final class PatternP2PUnitManagerLogic implements IGridTickable {
    private static final String LOCAL_CONFIGURATION = "PatternP2PUnitLocalConfiguration";
    private static final String MAIN_CONFIGURATION = "PatternP2PUnitMainConfiguration";
    private static final String SYNC_MAIN_CONFIGURATION = "SyncUnitMainConfiguration";
    private static final String MAIN_REVISION = "PatternP2PUnitMainConfigurationRevision";
    private static final String ACTIVE_OUTPUTS = "PatternP2PUnitActiveOutputs";
    private static final String TASK_ACTIVE = "PatternP2PUnitTaskActive";
    private static final String ENERGY_DISTRIBUTION_MODE = "PatternP2PUnitEnergyDistributionMode";
    private static final String REMAINING_PRIMARY = "PatternP2PUnitRemainingPrimary";
    private static final String PENDING_INPUTS = "PatternP2PUnitPendingInputs";
    private static final String OUTPUT_FORM = "OutputForm";
    private static final String ACTIVE_PATTERN = "PatternP2PUnitActivePattern";
    private static final String BATCH_SESSION_ID = "PatternP2PUnitBatchSessionId";

    private final IManagedGridNode mainNode;
    private final PatternP2PUnitManagerPart manager;
    private final IActionSource actionSource;
    private final List<PendingMaterial> pendingInputs = new ArrayList<>();
    private final Map<PatternP2PUnitPortPart, Integer> dispatchPortSlots = new java.util.IdentityHashMap<>();
    private final Map<PatternP2PUnitPortPart, AEKey> dispatchPortTypes = new java.util.IdentityHashMap<>();
    private final Map<Integer, PatternP2PUnitPortPart> dispatchSlotPorts = new java.util.HashMap<>();
    private final Map<Integer, int[]> persistedSlotPortSides = new java.util.HashMap<>();
    private final Map<AEKey, Long> declaredOutputs = new LinkedHashMap<>();

    private boolean syncMainConfiguration = true;
    private PatternP2PUnitConfiguration localConfiguration = PatternP2PUnitConfiguration.DEFAULT;
    private PatternP2PUnitConfiguration cachedMainConfiguration = PatternP2PUnitConfiguration.DEFAULT;
    private long cachedMainRevision = -1;
    private boolean taskActive;
    private EnergyDistributionMode energyDistributionMode = EnergyDistributionMode.EVEN;
    private @Nullable AEKey primaryKey;
    private @Nullable AEItemKey activePattern;
    private @Nullable UUID batchSessionId;
    private long remainingPrimary;
    private int pendingRetryFailures;
    private long pendingNextRetryTick;

    public PatternP2PUnitManagerLogic(IManagedGridNode mainNode, PatternP2PUnitManagerPart manager) {
        this.mainNode = mainNode;
        this.manager = manager;
        this.actionSource = new MachineSource(mainNode::getNode);
        mainNode.addService(IGridTickable.class, this);
    }

    public boolean canAcceptTask() {
        return (!isTaskActive() || batchSessionId != null) && pendingInputs.isEmpty() && mainNode.isActive()
                && manager.hasConfiguredFrequency();
    }

    public boolean isTaskActive() {
        return taskActive;
    }

    public boolean hasTaskState() {
        return isTaskActive() || !pendingInputs.isEmpty();
    }

    public boolean hasActiveBatchSession(UUID sessionId) {
        return sessionId != null && taskActive && sessionId.equals(batchSessionId);
    }

    public boolean isTaskOperational() {
        return isTaskActive() && mainNode.isActive() && manager.hasConfiguredFrequency();
    }

    public void resetTaskState() {
        if (!hasTaskState()) {
            invalidatePortRuntimeState();
            return;
        }
        pendingInputs.clear();
        dispatchPortTypes.clear();
        dispatchSlotPorts.clear();
        persistedSlotPortSides.clear();
        resetPendingRetryBackoff();
        declaredOutputs.clear();
        taskActive = false;
        primaryKey = null;
        activePattern = null;
        batchSessionId = null;
        remainingPrimary = 0;
        changed();
        invalidatePortRuntimeState();
        manager.notifyInputAvailabilityChanged();
    }

    public PatternP2PUnitConfiguration getEffectiveConfiguration() {
        return syncMainConfiguration ? cachedMainConfiguration : localConfiguration;
    }

    public boolean isSyncMainConfiguration() {
        return syncMainConfiguration;
    }

    public EnergyDistributionMode getEnergyDistributionMode() {
        return energyDistributionMode;
    }

    public void setEnergyDistributionMode(EnergyDistributionMode mode) {
        if (applyEnergyDistributionMode(mode)) {
            var grid = mainNode.getGrid();
            if (grid != null) {
                grid.getService(PatternP2PEnergyGridService.class).demandChanged();
            }
        }
    }

    boolean applyEnergyDistributionMode(EnergyDistributionMode mode) {
        if (mode == null || mode == energyDistributionMode) {
            return false;
        }
        energyDistributionMode = mode;
        manager.getHost().markForSave();
        return true;
    }

    public void setSyncMainConfiguration(boolean enabled) {
        if (syncMainConfiguration == enabled) {
            return;
        }
        if (!enabled) {
            localConfiguration = cachedMainConfiguration;
        }
        syncMainConfiguration = enabled;
        changed();
        wakePorts();
    }

    public void setLocalConfiguration(PatternP2PUnitConfiguration configuration) {
        if (!syncMainConfiguration && configuration != null && !configuration.equals(localConfiguration)) {
            localConfiguration = configuration;
            changed();
            wakePorts();
        }
    }

    public void applyMainConfiguration(PatternP2PUnitConfiguration configuration, long revision) {
        if (!ConfigurationSync.shouldApply(
                cachedMainConfiguration, cachedMainRevision, configuration, revision)) {
            return;
        }
        cachedMainConfiguration = configuration;
        cachedMainRevision = revision;
        changed();
        wakePorts();
    }

    public boolean tryAcceptPattern(IPatternDetails pattern, PatternDispatchMetadata metadata,
                                    KeyCounter[] inputHolders) {
        return tryAcceptPattern(pattern, metadata, inputHolders, 1, 1, false, null);
    }

    private boolean tryAcceptPattern(IPatternDetails pattern, PatternDispatchMetadata metadata,
                                     KeyCounter[] inputHolders, long divisor, long atomicUnits,
                                     boolean retainCompleteRemainder, @Nullable UUID sessionId) {
        if (!metadata.isValid() || !canAcceptTask(pattern, metadata, sessionId)) {
            return false;
        }
        List<PendingMaterial> plan = collectInputs(pattern, metadata, inputHolders,
                divisor, atomicUnits);
        if (plan == null || plan.isEmpty()) {
            return false;
        }
        Map<UnitPortType, List<PatternP2PUnitPortPart>> boundPorts = getBoundPortsByType();
        if (!retainCompleteRemainder && !canAllocateCompletely(plan, boundPorts)) {
            return false;
        }

        if (taskActive) {
            metadata.declaredOutputs().forEach((key, amount) ->
                    declaredOutputs.merge(key, amount, PatternP2PUnitManagerLogic::saturatingAdd));
            remainingPrimary += metadata.primaryOutput().amount();
        } else {
            declaredOutputs.clear();
            declaredOutputs.putAll(metadata.declaredOutputs());
            taskActive = true;
            primaryKey = metadata.primaryOutput().what();
            activePattern = pattern.getDefinition();
            batchSessionId = sessionId;
            remainingPrimary = metadata.primaryOutput().amount();
        }
        pendingInputs.clear();
        pendingInputs.addAll(plan);
        resetPendingRetryBackoff();
        changed();
        invalidatePortRuntimeState();
        manager.notifyInputAvailabilityChanged();
        return true;
    }

    public long getMaximumAcceptedAtomicUnits(IPatternDetails pattern, PatternDispatchMetadata atomicMetadata,
                                               KeyCounter[] atomicInputs, long upperBound, UUID sessionId) {
        if (upperBound <= 0 || !atomicMetadata.isValid()) {
            return 0;
        }
        return AtomicTaskCapacityProbe.findMaximum(upperBound,
                units -> canAcceptAtomicUnits(pattern, atomicMetadata, atomicInputs, units, sessionId));
    }

    public boolean tryAcceptPatternAtomicUnits(IPatternDetails pattern, PatternDispatchMetadata atomicMetadata,
                                               KeyCounter[] atomicInputs, long units, UUID sessionId) {
        KeyCounter[] scaledInputs = PatternInputScaling.multiply(atomicInputs, units);
        PatternDispatchMetadata scaledMetadata = atomicMetadata.forAtomicUnits(units);
        return scaledInputs != null && scaledMetadata.isValid()
                && tryAcceptPattern(pattern, scaledMetadata, scaledInputs,
                        atomicMetadata.batchCount(), units, true, sessionId);
    }

    private boolean canAcceptAtomicUnits(IPatternDetails pattern, PatternDispatchMetadata atomicMetadata,
                                         KeyCounter[] atomicInputs, long units, UUID sessionId) {
        KeyCounter[] scaledInputs = PatternInputScaling.multiply(atomicInputs, units);
        PatternDispatchMetadata scaledMetadata = atomicMetadata.forAtomicUnits(units);
        if (scaledInputs == null || !scaledMetadata.isValid()
                || !canAcceptTask(pattern, scaledMetadata, sessionId)) {
            return false;
        }
        List<PendingMaterial> plan = collectInputs(pattern, scaledMetadata, scaledInputs,
                scaledMetadata.batchCount(), units);
        if (plan == null || plan.isEmpty()) {
            return false;
        }
        Map<UnitPortType, List<PatternP2PUnitPortPart>> boundPorts = getBoundPortsByType();
        if (!canAllocateCompletely(plan, boundPorts)) return false;
        return true;
    }

    private @Nullable List<PendingMaterial> collectInputs(IPatternDetails pattern, PatternDispatchMetadata metadata,
                                                           KeyCounter[] inputHolders,
                                                           long divisor, long units) {
        AEProcessingPattern processingPattern = metadata.processingPattern();
        if (processingPattern != null) {
            List<ProcessingInputMapper.SlotInput> mapped = ProcessingInputMapper.map(
                    processingPattern, inputHolders, manager.getLevel(), divisor, units);
            if (mapped == null) {
                // A processing task must retain its encoded slot identity. Falling
                // back to unscoped inputs would bypass per-slot output forms and
                // the multi-slot sharing rule, especially for repeated materials.
                return null;
            }
            List<PendingMaterial> result = new ArrayList<>();
            for (ProcessingInputMapper.SlotInput input : mapped) {
                MaterialOutputForm form = metadata.materialOutputConfig().getOutputForm(input.slot());
                if (!form.supports(input.stack().what())) {
                    return null;
                }
                result.add(new PendingMaterial(input.stack(), form, input.slot()));
            }
            return result;
        }

        return collectDefaultInputs(inputHolders);
    }

    private List<PendingMaterial> collectDefaultInputs(KeyCounter[] inputHolders) {
        List<PendingMaterial> result = new ArrayList<>();
        for (KeyCounter holder : inputHolders) {
            if (holder == null) {
                continue;
            }
            for (var entry : holder) {
                if (entry.getLongValue() > 0) {
                    result.add(new PendingMaterial(
                            new GenericStack(entry.getKey(), entry.getLongValue()), MaterialOutputForm.NORMAL, -1));
                }
            }
        }
        return result;
    }

    private Map<UnitPortType, List<PatternP2PUnitPortPart>> getBoundPortsByType() {
        var grid = mainNode.getGrid();
        if (grid == null) {
            return Map.of();
        }
        Map<UnitPortType, List<PatternP2PUnitPortPart>> result = new EnumMap<>(UnitPortType.class);
        for (UnitPortType type : UnitPortType.values()) {
            List<PatternP2PUnitPortPart> ports = grid.getService(PatternP2PTopologyGridService.class)
                    .getPorts(manager.getPatternP2PUnitId(), type);
            if (!ports.isEmpty()) {
                result.put(type, ports);
            }
        }
        return result;
    }

    private static List<PatternP2PUnitPortPart> portsFor(
            Map<UnitPortType, List<PatternP2PUnitPortPart>> boundPorts, MaterialOutputForm form) {
        return boundPorts.getOrDefault(UnitPortType.forOutputFormId(form.getId()), List.of());
    }

    private boolean canUsePortForSlot(int slot, PatternP2PUnitPortPart port,
                                      Map<PatternP2PUnitPortPart, Integer> owners,
                                      Map<Integer, PatternP2PUnitPortPart> slotOwners) {
        if (slot < 0) return true;
        Integer portOwner = owners.get(port);
        if (isSingleSlotEnabled(port) && portOwner != null && portOwner != slot) return false;
        PatternP2PUnitPortPart slotOwner = slotOwners.get(slot);
        return slotOwner == null || slotOwner == port || !isSingleSlotEnabled(slotOwner);
    }

    private boolean isSingleSlotEnabled(PatternP2PUnitPortPart port) {
        return port.getEffectiveSingleSlot();
    }

    private List<PatternP2PUnitPortPart> sortedPorts(List<PatternP2PUnitPortPart> ports) {
        List<PatternP2PUnitPortPart> result = new ArrayList<>(ports);
        result.sort(Comparator.comparingInt(PatternP2PUnitPortPart::getTransferPriority).reversed());
        return result;
    }

    private List<PatternP2PUnitPortPart> candidatePorts(List<PatternP2PUnitPortPart> ports,
                                                        PendingMaterial material) {
        List<PatternP2PUnitPortPart> filtered = new ArrayList<>();
        for (PatternP2PUnitPortPart port : ports) {
            if (port.matchesInput(manager, material.stack(), material.form())) {
                filtered.add(port);
            }
        }
        return sortedPorts(filtered);
    }

    /** Validates aggregate capacity across all ports; one material may span multiple ports. */
    private boolean canAllocateCompletely(List<PendingMaterial> materials,
                                          Map<UnitPortType, List<PatternP2PUnitPortPart>> boundPorts) {
        Map<MaterialOutputForm, List<PendingMaterial>> groups = new java.util.EnumMap<>(MaterialOutputForm.class);
        for (PendingMaterial material : materials) {
            groups.computeIfAbsent(material.form(), ignored -> new ArrayList<>()).add(material);
        }
        for (var entry : groups.entrySet()) {
            if (!canAllocateGroup(entry.getValue(), portsFor(boundPorts, entry.getKey()))) return false;
        }
        return true;
    }

    private boolean canAllocateGroup(List<PendingMaterial> materials, List<PatternP2PUnitPortPart> ports) {
        if (ports.isEmpty()) return false;
        TransferPortOutputMode mode = getEffectiveConfiguration().transferPortOutputMode();
        Map<PatternP2PUnitPortPart, Long> remaining = new java.util.IdentityHashMap<>();
        for (PatternP2PUnitPortPart port : ports) {
            long capacity = mode == TransferPortOutputMode.SINGLE_ITEM ? 1 : Long.MAX_VALUE;
            remaining.put(port, capacity);
        }
        // Keep encoded slot order. The real dispatch loop uses this order too;
        // sorting by simulated capacity can otherwise reserve a lower-priority
        // port before the material that should have claimed the higher priority one.
        List<PendingMaterial> ordered = materials;
        Map<PatternP2PUnitPortPart, appeng.api.stacks.AEKey> assignedType = new java.util.IdentityHashMap<>();
        Map<PatternP2PUnitPortPart, Integer> assignedSlot = new java.util.IdentityHashMap<>();
        Map<Integer, PatternP2PUnitPortPart> assignedSlotPort = new java.util.HashMap<>();
        for (PendingMaterial material : ordered) {
            long left = material.stack().amount();
            List<PatternP2PUnitPortPart> candidates = candidatePorts(ports, material);
            for (PatternP2PUnitPortPart port : candidates) {
                if (left <= 0) break;
                if (mode == TransferPortOutputMode.SAME_TYPE
                        && assignedType.containsKey(port)
                        && !assignedType.get(port).equals(material.stack().what())) continue;
                if (!canUsePortForSlot(material.slot(), port, assignedSlot, assignedSlotPort)) continue;
                long simulated = port.insertInput(manager, material.stack(), material.form(), Actionable.SIMULATE);
                if (simulated <= 0) continue;
                long portCapacity = remaining.get(port);
                if (mode != TransferPortOutputMode.SINGLE_ITEM && portCapacity == Long.MAX_VALUE) {
                    portCapacity = port.estimateTransferCapacity(material.stack().what(), simulated);
                    remaining.put(port, portCapacity);
                }
                long accepted = Math.min(left, Math.min(simulated, portCapacity));
                if (accepted <= 0) continue;
                left -= accepted;
                if (portCapacity != Long.MAX_VALUE) remaining.put(port, portCapacity - accepted);
                if (mode == TransferPortOutputMode.SAME_TYPE) assignedType.put(port, material.stack().what());
                if (material.slot() >= 0) {
                    assignedSlot.putIfAbsent(port, material.slot());
                    if (isSingleSlotEnabled(port)) {
                        assignedSlotPort.putIfAbsent(material.slot(), port);
                        // A single-slot port may not split this material over
                        // another port, even when its simulated capacity is partial.
                        break;
                    }
                }
            }
            if (left > 0 && (material.slot() < 0 || !assignedSlotPort.containsKey(material.slot()))) {
                return false;
            }
        }
        return true;
    }

    private boolean dispatchPending() {
        boolean changed = false;
        Map<UnitPortType, List<PatternP2PUnitPortPart>> boundPorts = getBoundPortsByType();
        restorePersistedSlotPorts(boundPorts);
        for (var iterator = pendingInputs.listIterator(); iterator.hasNext(); ) {
            PendingMaterial pending = iterator.next();
            long remaining = pending.stack().amount();
            boolean insertedAny = false;

            // Priority only determines the attempt order. The connected machine is
            // authoritative: it may reject a material or accept only part of it.
            for (PatternP2PUnitPortPart port : candidatePorts(
                    portsFor(boundPorts, pending.form()), pending)) {
                if (remaining <= 0 || !canUsePortForSlot(pending.slot(), port,
                        dispatchPortSlots, dispatchSlotPorts)) {
                    continue;
                }
                if (!allowsConfiguredPortType(port, pending.stack().what())) {
                    continue;
                }
                long inserted = port.insertInput(manager,
                        new GenericStack(pending.stack().what(), remaining),
                        pending.form(), Actionable.MODULATE);
                if (inserted <= 0) {
                    // A simulated candidate can still reject the real insertion;
                    // continue with the next port instead of retrying this one.
                    continue;
                }
                inserted = Math.min(inserted, remaining);
                remaining -= inserted;
                insertedAny = true;
                if (pending.slot() >= 0) {
                    dispatchPortSlots.putIfAbsent(port, pending.slot());
                    if (isSingleSlotEnabled(port)) {
                        dispatchSlotPorts.putIfAbsent(pending.slot(), port);
                    }
                }
                if (getEffectiveConfiguration().transferPortOutputMode() == TransferPortOutputMode.SAME_TYPE) {
                    dispatchPortTypes.putIfAbsent(port, pending.stack().what());
                }
            }

            if (remaining <= 0) {
                iterator.remove();
                changed = true;
            } else if (insertedAny) {
                iterator.set(new PendingMaterial(
                        new GenericStack(pending.stack().what(), remaining), pending.form(), pending.slot()));
                changed = true;
            }
        }
        if (changed) {
            finishTaskIfComplete();
        }
        return changed;
    }

    private void restorePersistedSlotPorts(Map<UnitPortType, List<PatternP2PUnitPortPart>> boundPorts) {
        if (persistedSlotPortSides.isEmpty()) {
            return;
        }
        for (var entry : persistedSlotPortSides.entrySet()) {
            int slot = entry.getKey();
            int[] side = entry.getValue();
            for (List<PatternP2PUnitPortPart> ports : boundPorts.values()) {
                for (PatternP2PUnitPortPart port : ports) {
                    Direction portSide = port.getSide();
                    if (portSide != null && portSide.get3DDataValue() == side[0]) {
                        dispatchSlotPorts.putIfAbsent(slot, port);
                        dispatchPortSlots.putIfAbsent(port, slot);
                    }
                }
            }
        }
        persistedSlotPortSides.clear();
    }

    /** Applies only the explicit SAME_TYPE port mode; machine-specific type rules
     * remain delegated to the real insertion result below. */
    private boolean allowsConfiguredPortType(PatternP2PUnitPortPart port, AEKey what) {
        if (getEffectiveConfiguration().transferPortOutputMode() != TransferPortOutputMode.SAME_TYPE) {
            return true;
        }
        AEKey assigned = dispatchPortTypes.get(port);
        return assigned == null || assigned.equals(what);
    }

    public long filterReturned(AEKey what, long amount) {
        if (!isTaskOperational() || amount <= 0) {
            return 0;
        }
        return getEffectiveConfiguration().returnMode() == ReturnMode.STRICT
                && !declaredOutputs.containsKey(what) ? 0 : amount;
    }

    public long simulateReturned(AEKey what, long amount) {
        long filtered = filterReturned(what, amount);
        if (filtered <= 0) {
            return 0;
        }
        return manager.getReturnInventory().insert(what, filtered, Actionable.SIMULATE, actionSource);
    }

    public long insertReturned(AEKey what, long amount, Actionable mode) {
        long filtered = filterReturned(what, amount);
        if (filtered <= 0) {
            return 0;
        }
        return manager.getReturnInventory().insert(what, filtered, mode, actionSource);
    }

    public long insertProductExtractionRecovery(AEKey what, long amount) {
        RemoteReturnInventory destination = manager.getReturnInventory();
        destination.setProductExtractionBypass(true);
        try {
            return destination.insert(what, amount, Actionable.MODULATE, actionSource);
        } finally {
            destination.setProductExtractionBypass(false);
        }
    }

    public void onReturnedStack(GenericStack stack) {
        if (!isTaskActive() || stack == null || stack.amount() <= 0) {
            return;
        }
        if (Objects.equals(primaryKey, stack.what())) {
            remainingPrimary = Math.max(0, remainingPrimary - stack.amount());
            if (remainingPrimary == 0) {
                finishTaskIfComplete();
            } else {
                changed();
            }
        }
    }

    private void finishTaskIfComplete() {
        if (UnitTaskCompletion.isComplete(taskActive, remainingPrimary, !pendingInputs.isEmpty())) {
            finishTask();
        } else {
            changed();
        }
    }

    private void finishTask() {
        taskActive = false;
        primaryKey = null;
        activePattern = null;
        batchSessionId = null;
        remainingPrimary = 0;
        declaredOutputs.clear();
        pendingInputs.clear();
        dispatchPortSlots.clear();
        dispatchPortTypes.clear();
        dispatchSlotPorts.clear();
        persistedSlotPortSides.clear();
        resetPendingRetryBackoff();
        changed();
        invalidatePortRuntimeState();
        manager.notifyInputAvailabilityChanged();
    }

    private void wakePorts() {
        var grid = mainNode.getGrid();
        if (grid == null) {
            return;
        }
        for (PatternP2PUnitPortPart port : grid.getService(PatternP2PTopologyGridService.class)
                .getPorts(manager.getPatternP2PUnitId())) {
            port.alertTicking();
        }
    }

    private void invalidatePortRuntimeState() {
        var grid = mainNode.getGrid();
        if (grid == null) {
            return;
        }
        for (PatternP2PUnitPortPart port : grid.getService(PatternP2PTopologyGridService.class)
                .getPorts(manager.getPatternP2PUnitId())) {
            port.invalidateTaskRuntimeState();
        }
    }

    private void changed() {
        manager.getHost().markForSave();
        mainNode.ifPresent((grid, node) -> {
            grid.getTickManager().alertDevice(node);
            grid.getService(PatternP2PEnergyGridService.class).demandChanged();
        });
    }

    public void alertPendingRetry() {
        resetPendingRetryBackoff();
        mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        manager.notifyInputAvailabilityChanged();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, DispatchBackoffPolicy.MAX_PENDING_DELAY, false, 5);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isTaskOperational()) {
            return TickRateModulation.IDLE;
        }
        if (pendingInputs.isEmpty()) {
            resetPendingRetryBackoff();
            return TickRateModulation.IDLE;
        }
        long now = getGameTime();
        if (now < pendingNextRetryTick) {
            return TickRateModulation.SLOWER;
        }
        if (dispatchPending()) {
            resetPendingRetryBackoff();
            return TickRateModulation.URGENT;
        }
        pendingRetryFailures = Math.min(Integer.MAX_VALUE, pendingRetryFailures + 1);
        pendingNextRetryTick = saturatingAdd(now,
                DispatchBackoffPolicy.pendingDelay(pendingRetryFailures));
        return TickRateModulation.SLOWER;
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        resetPendingRetryBackoff();
        energyDistributionMode = data.contains(ENERGY_DISTRIBUTION_MODE)
                ? EnergyDistributionMode.fromId(data.getByte(ENERGY_DISTRIBUTION_MODE))
                : EnergyDistributionMode.EVEN;
        syncMainConfiguration = !data.contains(SYNC_MAIN_CONFIGURATION)
                || data.getBoolean(SYNC_MAIN_CONFIGURATION);
        localConfiguration = PatternP2PUnitConfiguration.read(data.getCompound(LOCAL_CONFIGURATION));
        cachedMainConfiguration = PatternP2PUnitConfiguration.read(data.getCompound(MAIN_CONFIGURATION));
        cachedMainRevision = data.getLong(MAIN_REVISION);
        taskActive = data.getBoolean(TASK_ACTIVE);
        activePattern = taskActive && data.contains(ACTIVE_PATTERN, Tag.TAG_COMPOUND)
                ? AEItemKey.fromTag(registries, data.getCompound(ACTIVE_PATTERN)) : null;
        batchSessionId = taskActive && data.hasUUID(BATCH_SESSION_ID)
                ? data.getUUID(BATCH_SESSION_ID) : null;
        declaredOutputs.clear();
        for (var stack : readStacks(data.getList(ACTIVE_OUTPUTS, Tag.TAG_COMPOUND), registries)) {
            declaredOutputs.merge(stack.what(), stack.amount(), PatternP2PUnitManagerLogic::saturatingAdd);
        }
        GenericStack primary = data.contains(REMAINING_PRIMARY, Tag.TAG_COMPOUND)
                ? GenericStack.readTag(registries, data.getCompound(REMAINING_PRIMARY)) : null;
        primaryKey = primary == null ? null : primary.what();
        remainingPrimary = primary == null ? 0 : primary.amount();
        pendingInputs.clear();
        dispatchPortSlots.clear();
        dispatchPortTypes.clear();
        dispatchSlotPorts.clear();
        var pending = data.getList(PENDING_INPUTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            GenericStack stack = GenericStack.readTag(registries, entry);
            if (stack != null && stack.amount() > 0) {
                pendingInputs.add(new PendingMaterial(stack,
                        MaterialOutputForm.fromId(entry.getByte(OUTPUT_FORM)),
                        entry.contains("PatternSlot") ? entry.getInt("PatternSlot") : -1));
                if (entry.contains("BoundPortSide") && entry.contains("PatternSlot")) {
                    persistedSlotPortSides.put(entry.getInt("PatternSlot"),
                            new int[] { entry.getInt("BoundPortSide") });
                }
            }
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.putByte(ENERGY_DISTRIBUTION_MODE, (byte) energyDistributionMode.getId());
        data.putBoolean(SYNC_MAIN_CONFIGURATION, syncMainConfiguration);
        data.put(LOCAL_CONFIGURATION, localConfiguration.write());
        data.put(MAIN_CONFIGURATION, cachedMainConfiguration.write());
        data.putLong(MAIN_REVISION, cachedMainRevision);
        data.putBoolean(TASK_ACTIVE, taskActive);
        if (taskActive && activePattern != null) {
            data.put(ACTIVE_PATTERN, activePattern.toTag(registries));
        } else {
            data.remove(ACTIVE_PATTERN);
        }
        if (taskActive && batchSessionId != null) {
            data.putUUID(BATCH_SESSION_ID, batchSessionId);
        } else {
            data.remove(BATCH_SESSION_ID);
        }
        data.remove("PatternP2PUnitActiveReturnMode");
        List<GenericStack> outputs = declaredOutputs.entrySet().stream()
                .map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
        data.put(ACTIVE_OUTPUTS, writeStacks(outputs, registries));
        if (primaryKey != null && remainingPrimary > 0) {
            data.put(REMAINING_PRIMARY, GenericStack.writeTag(
                    registries, new GenericStack(primaryKey, remainingPrimary)));
        } else {
            data.remove(REMAINING_PRIMARY);
        }
        ListTag pending = new ListTag();
        for (PendingMaterial material : pendingInputs) {
            CompoundTag entry = GenericStack.writeTag(registries, material.stack());
            entry.putByte(OUTPUT_FORM, (byte) material.form().getId());
            entry.putInt("PatternSlot", material.slot());
            PatternP2PUnitPortPart boundPort = material.slot() < 0
                    ? null : dispatchSlotPorts.get(material.slot());
            if (boundPort != null && boundPort.getSide() != null) {
                entry.putInt("BoundPortSide", boundPort.getSide().get3DDataValue());
            }
            pending.add(entry);
        }
        data.put(PENDING_INPUTS, pending);
    }

    public void addDrops(List<net.minecraft.world.item.ItemStack> drops) {
        for (PendingMaterial pending : pendingInputs) {
            pending.stack().what().addDrops(pending.stack().amount(), drops,
                    manager.getLevel(), manager.getBlockEntity().getBlockPos());
        }
    }

    public void clearContent() {
        pendingInputs.clear();
        dispatchPortSlots.clear();
        dispatchPortTypes.clear();
        dispatchSlotPorts.clear();
        resetPendingRetryBackoff();
        declaredOutputs.clear();
        taskActive = false;
        primaryKey = null;
        activePattern = null;
        batchSessionId = null;
        remainingPrimary = 0;
    }

    private static List<GenericStack> readStacks(ListTag list, HolderLookup.Provider registries) {
        List<GenericStack> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            GenericStack stack = GenericStack.readTag(registries, list.getCompound(i));
            if (stack != null && stack.amount() > 0) {
                result.add(stack);
            }
        }
        return result;
    }

    private static ListTag writeStacks(List<GenericStack> stacks, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (GenericStack stack : stacks) {
            result.add(GenericStack.writeTag(registries, stack));
        }
        return result;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private boolean canAcceptTask(IPatternDetails pattern, PatternDispatchMetadata metadata,
                                  @Nullable UUID sessionId) {
        if (!pendingInputs.isEmpty()) {
            return false;
        }
        if (!taskActive) {
            return canAcceptTask();
        }
        if (sessionId == null || !sessionId.equals(batchSessionId)
                || !Objects.equals(activePattern, pattern.getDefinition())
                || !Objects.equals(primaryKey, metadata.primaryOutput().what())
                || !declaredOutputs.keySet().equals(metadata.declaredOutputs().keySet())
                || remainingPrimary > Long.MAX_VALUE - metadata.primaryOutput().amount()) {
            return false;
        }
        for (var entry : metadata.declaredOutputs().entrySet()) {
            long current = declaredOutputs.getOrDefault(entry.getKey(), 0L);
            if (entry.getValue() <= 0 || current > Long.MAX_VALUE - entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void resetPendingRetryBackoff() {
        pendingRetryFailures = 0;
        pendingNextRetryTick = 0;
    }

    private long getGameTime() {
        var level = manager.getLevel();
        return level == null ? 0 : level.getGameTime();
    }

    private record PendingMaterial(GenericStack stack, MaterialOutputForm form, int slot) {
    }
}
