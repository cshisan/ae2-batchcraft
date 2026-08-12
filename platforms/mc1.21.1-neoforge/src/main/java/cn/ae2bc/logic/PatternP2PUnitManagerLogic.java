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
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.settings.TickRates;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.me.helpers.MachineSource;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import cn.ae2bc.pattern.MaterialOutputForm;
import net.minecraft.core.HolderLookup;
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

    private final IManagedGridNode mainNode;
    private final PatternP2PUnitManagerPart manager;
    private final IActionSource actionSource;
    private final List<PendingMaterial> pendingInputs = new ArrayList<>();
    private final Map<AEKey, Long> declaredOutputs = new LinkedHashMap<>();

    private boolean syncMainConfiguration = true;
    private PatternP2PUnitConfiguration localConfiguration = PatternP2PUnitConfiguration.DEFAULT;
    private PatternP2PUnitConfiguration cachedMainConfiguration = PatternP2PUnitConfiguration.DEFAULT;
    private long cachedMainRevision = -1;
    private boolean taskActive;
    private EnergyDistributionMode energyDistributionMode = EnergyDistributionMode.EVEN;
    private @Nullable AEKey primaryKey;
    private long remainingPrimary;

    public PatternP2PUnitManagerLogic(IManagedGridNode mainNode, PatternP2PUnitManagerPart manager) {
        this.mainNode = mainNode;
        this.manager = manager;
        this.actionSource = new MachineSource(mainNode::getNode);
        mainNode.addService(IGridTickable.class, this);
    }

    public boolean canAcceptTask() {
        return !isTaskActive() && pendingInputs.isEmpty() && mainNode.isActive()
                && manager.hasConfiguredFrequency();
    }

    public boolean isTaskActive() {
        return taskActive;
    }

    public boolean hasTaskState() {
        return isTaskActive() || !pendingInputs.isEmpty();
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
        declaredOutputs.clear();
        taskActive = false;
        primaryKey = null;
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
        if (!canAcceptTask() || !metadata.isValid()) {
            return false;
        }
        List<PendingMaterial> plan = collectInputs(pattern, metadata, inputHolders);
        if (plan == null || plan.isEmpty()) {
            return false;
        }
        Map<PatternP2PUnitPortType, List<PatternP2PUnitPortPart>> boundPorts = getBoundPortsByType();
        for (PendingMaterial material : plan) {
            PatternP2PUnitPortPart port = findPort(portsFor(boundPorts, material.form()),
                    material.stack(), material.form());
            if (port == null) {
                return false;
            }
        }

        declaredOutputs.clear();
        declaredOutputs.putAll(metadata.declaredOutputs());
        taskActive = true;
        primaryKey = metadata.primaryOutput().what();
        remainingPrimary = metadata.primaryOutput().amount();
        pendingInputs.clear();
        pendingInputs.addAll(plan);
        changed();
        invalidatePortRuntimeState();
        manager.notifyInputAvailabilityChanged();
        return true;
    }

    private @Nullable List<PendingMaterial> collectInputs(IPatternDetails pattern, PatternDispatchMetadata metadata,
                                                           KeyCounter[] inputHolders) {
        if (pattern instanceof AEProcessingPattern processingPattern) {
            KeyCounter available = new KeyCounter();
            for (KeyCounter holder : inputHolders) {
                available.addAll(holder);
            }
            var sparse = processingPattern.getSparseInputs();
            List<PendingMaterial> result = new ArrayList<>();
            for (int slot = 0; slot < sparse.size(); slot++) {
                GenericStack input = sparse.get(slot);
                if (input == null || input.amount() <= 0) {
                    continue;
                }
                if (available.get(input.what()) < input.amount()) {
                    return null;
                }
                MaterialOutputForm form = metadata.materialOutputConfig().getOutputForm(slot);
                if (!form.supports(input.what())) {
                    return null;
                }
                available.remove(input.what(), input.amount());
                result.add(new PendingMaterial(input, form));
            }
            return result;
        }

        List<PendingMaterial> result = new ArrayList<>();
        try {
            pattern.pushInputsToExternalInventory(inputHolders, (what, amount) -> {
                if (amount > 0) {
                    result.add(new PendingMaterial(new GenericStack(what, amount), MaterialOutputForm.NORMAL));
                }
            });
        } catch (RuntimeException ignored) {
            return null;
        }
        return result;
    }

    private Map<PatternP2PUnitPortType, List<PatternP2PUnitPortPart>> getBoundPortsByType() {
        var grid = mainNode.getGrid();
        if (grid == null) {
            return Map.of();
        }
        Map<PatternP2PUnitPortType, List<PatternP2PUnitPortPart>> result = new EnumMap<>(PatternP2PUnitPortType.class);
        for (PatternP2PUnitPortType type : PatternP2PUnitPortType.values()) {
            List<PatternP2PUnitPortPart> ports = grid.getService(PatternP2PTopologyGridService.class)
                    .getPorts(manager.getPatternP2PUnitId(), type);
            if (!ports.isEmpty()) {
                result.put(type, ports);
            }
        }
        return result;
    }

    private static List<PatternP2PUnitPortPart> portsFor(
            Map<PatternP2PUnitPortType, List<PatternP2PUnitPortPart>> boundPorts, MaterialOutputForm form) {
        return boundPorts.getOrDefault(PatternP2PUnitPortType.forOutputForm(form), List.of());
    }

    private @Nullable PatternP2PUnitPortPart findPort(List<PatternP2PUnitPortPart> boundPorts, GenericStack stack,
                                                       MaterialOutputForm form) {
        for (PatternP2PUnitPortPart port : boundPorts) {
            if (port.insertInput(manager, stack, form, Actionable.SIMULATE) >= stack.amount()) {
                return port;
            }
        }
        return null;
    }

    private boolean dispatchPending() {
        boolean changed = false;
        Map<PatternP2PUnitPortType, List<PatternP2PUnitPortPart>> boundPorts = getBoundPortsByType();
        for (var iterator = pendingInputs.listIterator(); iterator.hasNext(); ) {
            PendingMaterial pending = iterator.next();
            PatternP2PUnitPortPart port = findPort(portsFor(boundPorts, pending.form()),
                    pending.stack(), pending.form());
            if (port == null) {
                continue;
            }
            GenericStack stack = pending.stack();
            long inserted = port.insertInput(manager, stack, pending.form(), Actionable.MODULATE);
            if (inserted >= stack.amount()) {
                iterator.remove();
                changed = true;
            } else if (inserted > 0) {
                iterator.set(new PendingMaterial(
                        new GenericStack(stack.what(), stack.amount() - inserted), pending.form()));
                changed = true;
            }
        }
        if (changed) {
            finishTaskIfComplete();
        }
        return changed;
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
        remainingPrimary = 0;
        declaredOutputs.clear();
        pendingInputs.clear();
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

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, TickRates.Interface.getMax(), false, 5);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isTaskOperational()) {
            return TickRateModulation.IDLE;
        }
        return dispatchPending() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        energyDistributionMode = data.contains(ENERGY_DISTRIBUTION_MODE)
                ? EnergyDistributionMode.fromId(data.getByte(ENERGY_DISTRIBUTION_MODE))
                : EnergyDistributionMode.EVEN;
        syncMainConfiguration = !data.contains(SYNC_MAIN_CONFIGURATION)
                || data.getBoolean(SYNC_MAIN_CONFIGURATION);
        localConfiguration = PatternP2PUnitConfiguration.read(data.getCompound(LOCAL_CONFIGURATION));
        cachedMainConfiguration = PatternP2PUnitConfiguration.read(data.getCompound(MAIN_CONFIGURATION));
        cachedMainRevision = data.getLong(MAIN_REVISION);
        taskActive = data.getBoolean(TASK_ACTIVE);
        declaredOutputs.clear();
        for (var stack : readStacks(data.getList(ACTIVE_OUTPUTS, Tag.TAG_COMPOUND), registries)) {
            declaredOutputs.merge(stack.what(), stack.amount(), PatternP2PUnitManagerLogic::saturatingAdd);
        }
        GenericStack primary = data.contains(REMAINING_PRIMARY, Tag.TAG_COMPOUND)
                ? GenericStack.readTag(registries, data.getCompound(REMAINING_PRIMARY)) : null;
        primaryKey = primary == null ? null : primary.what();
        remainingPrimary = primary == null ? 0 : primary.amount();
        pendingInputs.clear();
        var pending = data.getList(PENDING_INPUTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            GenericStack stack = GenericStack.readTag(registries, entry);
            if (stack != null && stack.amount() > 0) {
                pendingInputs.add(new PendingMaterial(stack,
                        MaterialOutputForm.fromId(entry.getByte(OUTPUT_FORM))));
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
        declaredOutputs.clear();
        taskActive = false;
        primaryKey = null;
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

    private record PendingMaterial(GenericStack stack, MaterialOutputForm form) {
    }
}
