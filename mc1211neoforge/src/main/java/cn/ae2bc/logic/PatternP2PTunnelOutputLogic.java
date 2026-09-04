package cn.ae2bc.logic;

import appeng.api.AECapabilities;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.settings.TickRates;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.me.helpers.MachineSource;
import appeng.parts.automation.StackWorldBehaviors;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.pattern.InputDirectionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Owns one output-side task batch, return policy, retry state, and input-side configuration.
 */
public final class PatternP2PTunnelOutputLogic implements ProductExtractionTask {
    private static final String PENDING_INPUTS = "PendingInputs";
    private static final String PENDING_INPUT_DIRECTION = "InputDirection";
    private static final String RETURN_MODE = "ReturnMode";
    private static final String DECLARED_OUTPUTS = "DeclaredOutputs";
    private static final String REMAINING_PRIMARY_OUTPUT = "RemainingPrimaryOutput";
    private static final String ACTIVE_PATTERN = "ActivePattern";
    private static final String ACTIVE_TASK_COUNT = "ActiveTaskCount";
    private static final String BATCH_SESSION_ID = "BatchSessionId";
    private static final String SYNC_INPUT_SETTINGS = "SyncInputSettings";
    private static final String PRODUCT_EXTRACTION_ENABLED = "ProductExtractionEnabled";
    private static final String PRODUCT_EXTRACTION_INTERVAL = "ProductExtractionInterval";
    private static final String PRODUCT_EXTRACTION_AMOUNT = "ProductExtractionAmount";
    private static final String ENERGY_DISTRIBUTION_MODE = "EnergyDistributionMode";
    private static final String PRODUCT_EXTRACTION_RECOVERY = "ProductExtractionRecovery";

    private final IManagedGridNode mainNode;
    private final PatternP2PTunnelPart output;
    private final IActionSource retryActionSource;
    private final List<PendingInput> pendingInputs = new ArrayList<>();
    private final ExtractionRecoveryQueue productExtractionRecovery;
    private final ReturnBatchTracker<AEKey, AEItemKey> returnBatch = new ReturnBatchTracker<>();
    private @Nullable TargetCache targetCache;
    private final ConfigurationSync.State<OutputConfiguration> configurationState =
            new ConfigurationSync.State<>(
                    new OutputConfiguration(ReturnMode.UNBLOCKED, EndpointProductExtractionSettings.DEFAULT),
                    true, -1);
    private EnergyDistributionMode energyDistributionMode = EnergyDistributionMode.EVEN;
    private int pendingRetryFailures;
    private long pendingNextRetryTick;
    private @Nullable UUID batchSessionId;
    /** Declared output types captured for the duration of one extraction pass. */
    private @Nullable Set<AEKey> productExtractionReturnFilter;

    public PatternP2PTunnelOutputLogic(IManagedGridNode mainNode, PatternP2PTunnelPart output) {
        this.mainNode = mainNode;
        this.output = output;
        this.retryActionSource = new MachineSource(mainNode::getNode);
        this.productExtractionRecovery = new ExtractionRecoveryQueue(() -> output.getHost().markForSave());
        mainNode.addService(IGridTickable.class, new RetryTicker());
    }

    public boolean canAcceptTask() {
        if (!pendingInputs.isEmpty()) {
            return false;
        }
        return returnBatch.canPotentiallyAccept();
    }

    public boolean isTaskActive() {
        return !pendingInputs.isEmpty() || returnBatch.isActive();
    }

    public boolean hasActiveBatchSession(UUID sessionId) {
        return sessionId != null && returnBatch.isActive() && sessionId.equals(batchSessionId);
    }

    public EnergyDistributionMode getEnergyDistributionMode() {
        return energyDistributionMode;
    }

    public void setEnergyDistributionMode(EnergyDistributionMode mode) {
        if (mode == null || mode == energyDistributionMode) {
            return;
        }
        var grid = mainNode.getGrid();
        if (grid != null) {
            grid.getService(PatternP2PEnergyGridService.class).synchronizeOutputGroupMode(output, mode);
        } else {
            applyEnergyDistributionMode(mode);
        }
    }

    public void applyEnergyDistributionMode(EnergyDistributionMode mode) {
        if (mode != null && mode != energyDistributionMode) {
            energyDistributionMode = mode;
            output.getHost().markForSave();
        }
    }

    public void resetTaskState() {
        if (!isTaskActive()) {
            return;
        }
        boolean wasAvailable = canAcceptTask();
        pendingInputs.clear();
        resetPendingRetryBackoff();
        returnBatch.clear();
        batchSessionId = null;
        persistStateChange(wasAvailable);
    }

    public ReturnMode getReturnMode() {
        return configurationState.value().returnMode();
    }

    public void setReturnMode(ReturnMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (configurationState.isSynchronizationEnabled()) {
            return;
        }
        if (configurationState.value().returnMode() != mode) {
            boolean wasAvailable = canAcceptTask();
            configurationState.setLocalValue(new OutputConfiguration(
                    mode, configurationState.value().productExtractionSettings()));
            persistStateChange(wasAvailable);
        }
    }

    public boolean isSyncInputSettings() {
        return configurationState.isSynchronizationEnabled();
    }

    public void setSyncInputSettings(boolean enabled) {
        if (!configurationState.setSynchronizationEnabled(enabled)) {
            return;
        }
        if (enabled) {
            output.synchronizeFromInput();
        }
        alertRetry();
        output.getHost().markForSave();
    }

    public EndpointProductExtractionSettings getProductExtractionSettings() {
        return configurationState.value().productExtractionSettings();
    }

    public void setProductExtractionEnabled(boolean enabled) {
        EndpointProductExtractionSettings settings = configurationState.value().productExtractionSettings();
        updateProductExtractionSettings(enabled, settings.interval(), settings.amount());
    }

    public void setProductExtractionInterval(int interval) {
        EndpointProductExtractionSettings settings = configurationState.value().productExtractionSettings();
        updateProductExtractionSettings(settings.enabled(), interval, settings.amount());
    }

    public void setProductExtractionAmount(int amount) {
        EndpointProductExtractionSettings settings = configurationState.value().productExtractionSettings();
        updateProductExtractionSettings(settings.enabled(), settings.interval(), amount);
    }

    private void updateProductExtractionSettings(boolean enabled, int interval, int amount) {
        EndpointProductExtractionSettings current = configurationState.value().productExtractionSettings();
        if (configurationState.isSynchronizationEnabled() || current.hasSameValues(enabled, interval, amount)) {
            return;
        }
        EndpointProductExtractionSettings updated = new EndpointProductExtractionSettings(enabled, interval, amount,
                current.revision() + 1);
        configurationState.setLocalValue(new OutputConfiguration(
                configurationState.value().returnMode(), updated));
        output.getHost().markForSave();
        alertRetry();
    }

    public void applyInputSettings(ReturnMode mode, EndpointProductExtractionSettings extractionSettings) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(extractionSettings, "extractionSettings");
        OutputConfiguration current = configurationState.value();
        OutputConfiguration incoming = new OutputConfiguration(mode, extractionSettings);
        boolean wasAvailable = canAcceptTask();
        boolean applied = configurationState.applyBroadcast(incoming, extractionSettings.revision());
        if (!applied) {
            return;
        }
        boolean returnModeChanged = current.returnMode() != mode;
        boolean extractionChanged = !current.productExtractionSettings().hasSameValues(
                extractionSettings.enabled(), extractionSettings.interval(), extractionSettings.amount());
        if (returnModeChanged) {
            persistStateChange(wasAvailable);
        } else {
            output.getHost().markForSave();
        }
        if (extractionChanged) {
            alertRetry();
        }
    }

    public long filterReturnAmount(AEKey what, long amount) {
        var extractionFilter = productExtractionReturnFilter;
        if (extractionFilter != null && !isTaskActive()) {
            return 0;
        }
        if (extractionFilter != null && getReturnMode() == ReturnMode.STRICT
                && !extractionFilter.contains(what)) {
            return 0;
        }
        return returnBatch.filter(what, amount, getReturnMode());
    }

    public void beginProductExtractionFilter() {
        productExtractionReturnFilter = Set.copyOf(returnBatch.getDeclaredOutputs().keySet());
    }

    public void endProductExtractionFilter() {
        productExtractionReturnFilter = null;
    }

    public void onReturnedStack(GenericStack stack) {
        if (!returnBatch.isActive()) {
            return;
        }
        boolean wasAvailable = canAcceptTask();
        long expectedPrimaryBefore = returnBatch.getExpectedPrimary();
        boolean completed = returnBatch.returned(stack.what(), stack.amount());
        if (completed) {
            batchSessionId = null;
        }
        if (returnBatch.getExpectedPrimary() != expectedPrimaryBefore) {
            persistStateChange(wasAvailable);
        }
    }

    public boolean tryAcceptPattern(IPatternDetails pattern, PatternDispatchMetadata metadata,
                                    KeyCounter[] inputs, IActionSource source) {
        return tryAcceptPattern(pattern, metadata, inputs, source, true, 1, 1, false, null);
    }

    private boolean tryAcceptPattern(IPatternDetails pattern, PatternDispatchMetadata metadata,
                                     KeyCounter[] inputs, IActionSource source,
                                     boolean allowCraftingMachine, long divisor, long atomicUnits,
                                     boolean retainCompleteRemainder, @Nullable UUID sessionId) {
        if (!mainNode.isActive() || !output.hasConfiguredFrequency()) {
            return false;
        }
        if (!metadata.isValid() || !canAcceptTask(pattern, metadata, sessionId)) {
            return false;
        }
        boolean wasAvailable = canAcceptTask();
        if (!(output.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        Direction outputSide = output.getSide();
        if (outputSide == null) {
            return false;
        }
        var targetPos = output.getBlockEntity().getBlockPos().relative(outputSide);
        Direction automaticFace = outputSide.getOpposite();
        TargetCache targets = getTargetCache(level, targetPos);

        // The crafting-machine API has only one ejection face and cannot represent per-material routing.
        if (allowCraftingMachine && !metadata.hasExplicitDirections()) {
            var machine = targets.get(automaticFace).getCraftingMachine();
            if (machine != null && machine.acceptsPlans()) {
                if (!beginTask(pattern, metadata, sessionId)) {
                    return false;
                }
                if (machine.pushPattern(pattern, inputs, automaticFace)) {
                    if (returnBatch.isActive()) {
                        persistStateChange(wasAvailable);
                    }
                    return true;
                }
                cancelTask(metadata, sessionId);
            }
        }

        if (!pattern.supportsPushInputsToExternalInventory()) {
            return false;
        }
        List<RoutedInput> selected = metadata.hasExplicitDirections()
                ? reconstructProcessingInputs(metadata.processingPattern(), inputs, metadata.outputDirections(),
                        divisor, atomicUnits)
                : null;
        if (selected == null && metadata.hasExplicitDirections()) {
            return false;
        }
        if (selected == null) {
            selected = collectCounterInputs(inputs);
        }
        if (selected.isEmpty()) {
            return false;
        }
        selected = condenseInputs(selected);

        StorageCache storages = new StorageCache(targets);
        List<PlannedInsert> plan = buildPlan(storages, automaticFace, selected, source, true);
        if (plan == null) {
            if (!retainCompleteRemainder || !beginTask(pattern, metadata, sessionId)) {
                return false;
            }
            pendingInputs.clear();
            for (RoutedInput routed : selected) {
                pendingInputs.add(new PendingInput(routed.stack(), routed.face()));
            }
            resetPendingRetryBackoff();
            persistStateChange(wasAvailable);
            wakeRetryIfNeeded();
            return true;
        }

        if (!beginTask(pattern, metadata, sessionId)) {
            return false;
        }
        boolean insertedAny = false;
        List<PendingInput> remainder = new ArrayList<>(plan.size());
        for (var planned : plan) {
            GenericStack stack = planned.input().stack();
            long inserted = planned.insert(source);
            insertedAny |= inserted > 0;
            if (inserted < stack.amount()) {
                remainder.add(new PendingInput(new GenericStack(stack.what(),
                        stack.amount() - Math.max(0, inserted)), planned.input().face()));
            }
        }
        if (!insertedAny) {
            if (!retainCompleteRemainder) {
                cancelTask(metadata, sessionId);
                return false;
            }
            remainder.clear();
            for (var planned : plan) {
                remainder.add(new PendingInput(planned.input().stack(), planned.input().face()));
            }
        }
        pendingInputs.clear();
        pendingInputs.addAll(remainder);
        resetPendingRetryBackoff();
        if (returnBatch.isActive() || !pendingInputs.isEmpty()) {
            persistStateChange(wasAvailable);
        }
        wakeRetryIfNeeded();
        return true;
    }

    public long getMaximumAcceptedAtomicUnits(IPatternDetails pattern, PatternDispatchMetadata atomicMetadata,
                                               KeyCounter[] atomicInputs, long upperBound, UUID sessionId,
                                               IActionSource source) {
        if (upperBound <= 0 || !mainNode.isActive() || !output.hasConfiguredFrequency()
                || !atomicMetadata.isValid()) {
            return 0;
        }
        return AtomicTaskCapacityProbe.findMaximum(upperBound,
                units -> canAcceptAtomicUnits(pattern, atomicMetadata, atomicInputs, units, sessionId, source));
    }

    public boolean tryAcceptPatternAtomicUnits(IPatternDetails pattern, PatternDispatchMetadata atomicMetadata,
                                               KeyCounter[] atomicInputs, long units, UUID sessionId,
                                               IActionSource source) {
        KeyCounter[] scaledInputs = PatternInputScaling.multiply(atomicInputs, units);
        PatternDispatchMetadata scaledMetadata = atomicMetadata.forAtomicUnits(units);
        return scaledInputs != null && scaledMetadata.isValid()
                && tryAcceptPattern(pattern, scaledMetadata, scaledInputs, source, false,
                        atomicMetadata.batchCount(), units, true, sessionId);
    }

    private boolean canAcceptAtomicUnits(IPatternDetails pattern, PatternDispatchMetadata atomicMetadata,
                                         KeyCounter[] atomicInputs, long units, UUID sessionId,
                                         IActionSource source) {
        KeyCounter[] scaledInputs = PatternInputScaling.multiply(atomicInputs, units);
        PatternDispatchMetadata scaledMetadata = atomicMetadata.forAtomicUnits(units);
        if (scaledInputs == null || !scaledMetadata.isValid()
                || !canAcceptTask(pattern, scaledMetadata, sessionId)
                || !(output.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        Direction outputSide = output.getSide();
        if (outputSide == null || !pattern.supportsPushInputsToExternalInventory()) {
            return false;
        }
        Direction automaticFace = outputSide.getOpposite();
        TargetCache targets = getTargetCache(level, output.getBlockEntity().getBlockPos().relative(outputSide));
        List<RoutedInput> selected = scaledMetadata.hasExplicitDirections()
                ? reconstructProcessingInputs(scaledMetadata.processingPattern(), scaledInputs,
                        scaledMetadata.outputDirections(), scaledMetadata.batchCount(), units)
                : collectCounterInputs(scaledInputs);
        if (selected == null || selected.isEmpty()) {
            return false;
        }
        return buildPlan(new StorageCache(targets), automaticFace,
                condenseInputs(selected), source, true) != null;
    }

    private @Nullable List<RoutedInput> reconstructProcessingInputs(AEProcessingPattern pattern,
                                                                     KeyCounter[] inputHolders,
                                                                     InputDirectionData directions,
                                                                     long divisor, long units) {
        try {
            List<ProcessingInputMapper.SlotInput> mapped = ProcessingInputMapper.map(
                    pattern, inputHolders, output.getLevel(), divisor, units);
            if (mapped == null) {
                return null;
            }
            List<RoutedInput> result = new ArrayList<>(mapped.size());
            for (ProcessingInputMapper.SlotInput input : mapped) {
                result.add(new RoutedInput(input.stack(), directions.getDirection(input.slot())));
            }
            return result;
        } catch (RuntimeException exception) {
            Ae2bcMod.LOGGER.warn("Unable to map runtime processing inputs to configured directions", exception);
            return null;
        }
    }

    private List<RoutedInput> collectCounterInputs(KeyCounter[] inputHolders) {
        List<RoutedInput> selected = new ArrayList<>(inputHolders.length);
        for (KeyCounter holder : inputHolders) {
            for (var entry : holder) {
                if (entry.getLongValue() > 0) {
                    selected.add(new RoutedInput(
                            new GenericStack(entry.getKey(), entry.getLongValue()), null));
                }
            }
        }
        return selected;
    }

    private List<RoutedInput> condenseInputs(List<RoutedInput> inputs) {
        Map<RouteKey, Long> condensed = new LinkedHashMap<>(inputs.size());
        for (RoutedInput input : inputs) {
            RouteKey key = new RouteKey(input.stack().what(), input.face());
            condensed.merge(key, input.stack().amount(), PatternP2PTunnelOutputLogic::saturatingAdd);
        }
        List<RoutedInput> result = new ArrayList<>(condensed.size());
        for (var entry : condensed.entrySet()) {
            result.add(new RoutedInput(new GenericStack(entry.getKey().what(), entry.getValue()),
                    entry.getKey().face()));
        }
        return result;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private @Nullable List<PlannedInsert> buildPlan(StorageCache storages, Direction automaticFace,
                                                    List<RoutedInput> inputs, IActionSource source,
                                                    boolean requireFull) {
        List<PlannedInsert> plan = new ArrayList<>(inputs.size());
        for (var input : inputs) {
            GenericStack stack = input.stack();
            Direction face = input.face() == null ? automaticFace : input.face();
            MEStorage storage = storages.get(face).get(stack.what().getType());
            if (storage == null) {
                return null;
            }
            long accepted = Math.max(0, storage.insert(
                    stack.what(), stack.amount(), Actionable.SIMULATE, source));
            if (accepted <= 0 || requireFull && accepted < stack.amount()) {
                return null;
            }
            long plannedAmount = Math.min(stack.amount(), accepted);
            plan.add(new PlannedInsert(
                    new RoutedInput(new GenericStack(stack.what(), plannedAmount), input.face()), storage));
        }
        return plan;
    }

    private final class StorageCache {
        private final TargetCache targets;
        private final EnumMap<Direction, Map<AEKeyType, MEStorage>> byFace = new EnumMap<>(Direction.class);

        private StorageCache(TargetCache targets) {
            this.targets = targets;
        }

        private Map<AEKeyType, MEStorage> get(Direction face) {
            return byFace.computeIfAbsent(face,
                    key -> targets.get(key).resolveStorages(PatternP2PTunnelOutputLogic.this::onTargetChanged));
        }
    }

    private boolean canAcceptTask(IPatternDetails pattern, PatternDispatchMetadata metadata,
                                  @Nullable UUID sessionId) {
        if (!pendingInputs.isEmpty()) {
            return false;
        }
        GenericStack primary = metadata.primaryOutput();
        if (sessionId != null && returnBatch.isActive()) {
            return sessionId.equals(batchSessionId)
                    && returnBatch.canAppend(pattern.getDefinition(), metadata.declaredOutputs(),
                    primary.what(), primary.amount());
        }
        if (sessionId == null && batchSessionId != null) {
            return false;
        }
        return returnBatch.canAccept(pattern.getDefinition(), metadata.declaredOutputs(),
                primary.what(), primary.amount());
    }

    private boolean beginTask(IPatternDetails pattern, PatternDispatchMetadata metadata,
                              @Nullable UUID sessionId) {
        GenericStack primary = metadata.primaryOutput();
        if (sessionId != null && returnBatch.isActive()) {
            boolean appended = sessionId.equals(batchSessionId)
                    && returnBatch.append(pattern.getDefinition(), metadata.declaredOutputs(),
                            primary.what(), primary.amount());
            if (appended) {
                alertRetry();
            }
            return appended;
        }
        if (sessionId == null && batchSessionId != null) {
            return false;
        }
        boolean begun = returnBatch.begin(pattern.getDefinition(), metadata.declaredOutputs(),
                primary.what(), primary.amount());
        if (begun) {
            batchSessionId = sessionId;
            alertRetry();
        }
        return begun;
    }

    private void cancelTask(PatternDispatchMetadata metadata, @Nullable UUID sessionId) {
        if (sessionId != null && returnBatch.isActive() && sessionId.equals(batchSessionId)
                && returnBatch.getTaskCount() == 1) {
            returnBatch.rollbackAppend(metadata.declaredOutputs(), metadata.primaryOutput().amount());
        } else {
            returnBatch.rollback(metadata.primaryOutput().amount());
        }
        if (!returnBatch.isActive()) {
            batchSessionId = null;
        }
    }

    private TargetCache getTargetCache(ServerLevel level, BlockPos targetPos) {
        if (targetCache == null || !targetCache.matches(level, targetPos)) {
            targetCache = new TargetCache(level, targetPos);
        }
        return targetCache;
    }

    private boolean retryPending() {
        if (pendingInputs.isEmpty() || !mainNode.isActive() || !output.hasConfiguredFrequency()) {
            return false;
        }
        Direction outputSide = output.getSide();
        if (outputSide == null || !(output.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        var targetPos = output.getBlockEntity().getBlockPos().relative(outputSide);
        Direction automaticFace = outputSide.getOpposite();
        var storages = new StorageCache(getTargetCache(level, targetPos));
        boolean wasAvailable = canAcceptTask();
        boolean progressed = false;
        for (var it = pendingInputs.listIterator(); it.hasNext(); ) {
            var pending = it.next();
            var stack = pending.stack();
            var plan = buildPlan(storages, automaticFace,
                    List.of(new RoutedInput(stack, pending.face())), retryActionSource, false);
            if (plan == null) {
                continue;
            }
            var planned = plan.getFirst();
            long inserted = planned.insert(retryActionSource);
            if (inserted >= stack.amount()) {
                it.remove();
                progressed = true;
            } else if (inserted > 0) {
                it.set(new PendingInput(new GenericStack(stack.what(), stack.amount() - inserted), pending.face()));
                progressed = true;
            }
        }
        if (progressed) {
            persistStateChange(wasAvailable);
        }
        return progressed;
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        resetPendingRetryBackoff();
        energyDistributionMode = data.contains(ENERGY_DISTRIBUTION_MODE)
                ? EnergyDistributionMode.fromId(data.getByte(ENERGY_DISTRIBUTION_MODE))
                : EnergyDistributionMode.EVEN;
        ReturnMode restoredReturnMode = data.contains(RETURN_MODE)
                ? ReturnMode.fromId(data.getByte(RETURN_MODE)) : ReturnMode.UNBLOCKED;
        GenericStack loadedRemainingPrimary = data.contains(REMAINING_PRIMARY_OUTPUT, Tag.TAG_COMPOUND)
                ? GenericStack.readTag(registries, data.getCompound(REMAINING_PRIMARY_OUTPUT)) : null;
        boolean synchronizationEnabled = !data.contains(SYNC_INPUT_SETTINGS) || data.getBoolean(SYNC_INPUT_SETTINGS);
        EndpointProductExtractionSettings restoredExtractionSettings = new EndpointProductExtractionSettings(
                data.getBoolean(PRODUCT_EXTRACTION_ENABLED),
                data.contains(PRODUCT_EXTRACTION_INTERVAL)
                        ? data.getInt(PRODUCT_EXTRACTION_INTERVAL) : ProductExtractionSettings.DEFAULT_INTERVAL,
                data.contains(PRODUCT_EXTRACTION_AMOUNT)
                        ? data.getInt(PRODUCT_EXTRACTION_AMOUNT) : ProductExtractionSettings.DEFAULT_AMOUNT,
                0);
        configurationState.restore(new OutputConfiguration(restoredReturnMode, restoredExtractionSettings),
                restoredExtractionSettings.revision(), synchronizationEnabled);
        Map<AEKey, Long> loadedDeclaredOutputs = readCounter(data, DECLARED_OUTPUTS, registries);
        AEItemKey loadedPattern = data.contains(ACTIVE_PATTERN, Tag.TAG_COMPOUND)
                ? AEItemKey.fromTag(registries, data.getCompound(ACTIVE_PATTERN)) : null;
        int loadedTaskCount = data.getInt(ACTIVE_TASK_COUNT);
        returnBatch.load(loadedPattern, loadedTaskCount, loadedDeclaredOutputs,
                loadedRemainingPrimary == null ? null : loadedRemainingPrimary.what(),
                loadedRemainingPrimary == null ? 0 : loadedRemainingPrimary.amount());
        batchSessionId = returnBatch.isActive() && data.hasUUID(BATCH_SESSION_ID)
                ? data.getUUID(BATCH_SESSION_ID) : null;
        pendingInputs.clear();
        var list = data.getList(PENDING_INPUTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            var stack = GenericStack.readTag(registries, entry);
            if (stack != null && stack.amount() > 0) {
                Direction face = entry.contains(PENDING_INPUT_DIRECTION, Tag.TAG_STRING)
                        ? Direction.byName(entry.getString(PENDING_INPUT_DIRECTION)) : null;
                pendingInputs.add(new PendingInput(stack, face));
            }
        }
        productExtractionRecovery.read(data, PRODUCT_EXTRACTION_RECOVERY, registries);
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.putByte(ENERGY_DISTRIBUTION_MODE, (byte) energyDistributionMode.getId());
        data.putByte(RETURN_MODE, (byte) getReturnMode().getId());
        data.remove("ActiveReturnMode");
        if (returnBatch.isActive() && returnBatch.getPrimaryKey() != null && returnBatch.getExpectedPrimary() > 0) {
            data.put(REMAINING_PRIMARY_OUTPUT, GenericStack.writeTag(registries,
                    new GenericStack(returnBatch.getPrimaryKey(), returnBatch.getExpectedPrimary())));
        } else {
            data.remove(REMAINING_PRIMARY_OUTPUT);
        }
        if (returnBatch.isActive() && returnBatch.getPattern() != null) {
            data.put(ACTIVE_PATTERN, returnBatch.getPattern().toTag(registries));
        } else {
            data.remove(ACTIVE_PATTERN);
        }
        if (returnBatch.isActive()) {
            data.putInt(ACTIVE_TASK_COUNT, returnBatch.getTaskCount());
        } else {
            data.remove(ACTIVE_TASK_COUNT);
        }
        EndpointProductExtractionSettings extractionSettings = getProductExtractionSettings();
        data.putBoolean(SYNC_INPUT_SETTINGS, configurationState.isSynchronizationEnabled());
        data.putBoolean(PRODUCT_EXTRACTION_ENABLED, extractionSettings.enabled());
        data.putInt(PRODUCT_EXTRACTION_INTERVAL, extractionSettings.interval());
        data.putInt(PRODUCT_EXTRACTION_AMOUNT, extractionSettings.amount());
        data.put(DECLARED_OUTPUTS, writeCounter(returnBatch.getDeclaredOutputs(), registries));
        data.remove("ExpectedPrimaryOutputs");
        ListTag list = new ListTag();
        for (var pending : pendingInputs) {
            CompoundTag entry = GenericStack.writeTag(registries, pending.stack());
            if (pending.face() != null) {
                entry.putString(PENDING_INPUT_DIRECTION, pending.face().getName());
            }
            list.add(entry);
        }
        if (returnBatch.isActive() && batchSessionId != null) {
            data.putUUID(BATCH_SESSION_ID, batchSessionId);
        } else {
            data.remove(BATCH_SESSION_ID);
        }
        data.put(PENDING_INPUTS, list);
        productExtractionRecovery.write(data, PRODUCT_EXTRACTION_RECOVERY, registries);
    }

    public void addDrops(List<net.minecraft.world.item.ItemStack> drops) {
        for (var pending : pendingInputs) {
            GenericStack stack = pending.stack();
            stack.what().addDrops(stack.amount(), drops, output.getLevel(), output.getBlockEntity().getBlockPos());
        }
        productExtractionRecovery.addDrops(drops, output.getLevel(), output.getBlockEntity().getBlockPos());
    }

    public void clearContent() {
        pendingInputs.clear();
        resetPendingRetryBackoff();
        productExtractionRecovery.clear();
        returnBatch.clear();
        batchSessionId = null;
    }

    private static Map<AEKey, Long> readCounter(CompoundTag data, String key,
                                                 HolderLookup.Provider registries) {
        var list = data.getList(key, Tag.TAG_COMPOUND);
        Map<AEKey, Long> result = new LinkedHashMap<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            GenericStack stack = GenericStack.readTag(registries, list.getCompound(i));
            if (stack != null && stack.amount() > 0) {
                result.merge(stack.what(), stack.amount(), PatternP2PTunnelOutputLogic::saturatingAdd);
            }
        }
        return result;
    }

    private static ListTag writeCounter(Map<AEKey, Long> counter, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (var entry : counter.entrySet()) {
            if (entry.getValue() > 0) {
                result.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getValue())));
            }
        }
        return result;
    }

    private void persistStateChange(boolean wasAvailable) {
        output.getHost().markForSave();
        var grid = mainNode.getGrid();
        if (grid != null) {
            grid.getService(PatternP2PEnergyGridService.class).demandChanged();
        }
        if (wasAvailable != canAcceptTask()) {
            output.notifyInputAvailabilityChanged();
        }
    }

    private void wakeRetryIfNeeded() {
        if (!pendingInputs.isEmpty()) {
            alertRetry();
        }
    }

    public void alertRetry() {
        resetPendingRetryBackoff();
        mainNode.ifPresent((grid, node) -> {
            if (!pendingInputs.isEmpty()) {
                grid.getTickManager().alertDevice(node);
            }
            grid.getService(ProductExtractionGridService.class).wake(node, this);
        });
    }

    public void onTargetChanged() {
        targetCache = null;
        alertRetry();
        output.notifyInputAvailabilityChanged();
    }

    @Override
    public boolean hasProductExtractionWork() {
        var settings = getEffectiveProductExtractionSettings();
        return !productExtractionRecovery.isEmpty()
                || isTaskActive() && settings != null && settings.enabled();
    }

    @Override
    public int getProductExtractionInterval() {
        var settings = getEffectiveProductExtractionSettings();
        return settings == null ? ProductExtractionSettings.DEFAULT_INTERVAL : settings.interval();
    }

    @Override
    public ProductExtractionTickState tickProductExtraction() {
        ProductExtractionBudget budget = new ProductExtractionBudget();
        budget.beginEndpoint();
        return tickProductExtraction(budget);
    }

    @Override
    public ProductExtractionTickState tickProductExtraction(ProductExtractionBudget budget) {
        if (!mainNode.isActive() || !(output.getLevel() instanceof ServerLevel level)) {
            return ProductExtractionTickState.DISABLED;
        }
        boolean recoveryProgress = drainProductExtractionRecovery();
        var endpointSettings = getEffectiveProductExtractionSettings();
        if (!isTaskActive() || endpointSettings == null || !endpointSettings.enabled()) {
            if (recoveryProgress) {
                return ProductExtractionTickState.PROGRESSED;
            }
            return productExtractionRecovery.isEmpty()
                    ? ProductExtractionTickState.DISABLED : ProductExtractionTickState.WAITING;
        }
        var settings = new ProductExtractionSettings(true, endpointSettings.interval(),
                endpointSettings.amount(), false, java.util.Set.of());
        Direction side = output.getSide();
        if (side == null) {
            return recoveryProgress
                    ? ProductExtractionTickState.PROGRESSED : ProductExtractionTickState.NO_PROGRESS;
        }
        var targetPos = output.getBlockEntity().getBlockPos().relative(side);
        Direction targetSide = side.getOpposite();
        var sources = getTargetCache(level, targetPos).get(targetSide).resolveStorages(this::onTargetChanged);
        beginProductExtractionFilter();
        ProductExtractor.Result result;
        try {
            result = ProductExtractor.extract(ExtractionSource.fromTypeMap(sources),
                    output.getReturnInventory(), settings, retryActionSource,
                    productExtractionRecovery::queue, budget);
        } finally {
            endProductExtractionFilter();
        }
        if (result.budgetExhausted()) {
            return ProductExtractionTickState.BUDGET_EXHAUSTED;
        }
        if (result.destinationBlocked()) {
            return ProductExtractionTickState.NO_PROGRESS;
        }
        return result.moved() > 0 || recoveryProgress
                ? ProductExtractionTickState.PROGRESSED : ProductExtractionTickState.NO_PROGRESS;
    }

    private @Nullable EndpointProductExtractionSettings getEffectiveProductExtractionSettings() {
        return getProductExtractionSettings();
    }

    private record OutputConfiguration(ReturnMode returnMode,
                                       EndpointProductExtractionSettings productExtractionSettings) {
    }

    private boolean drainProductExtractionRecovery() {
        RemoteReturnInventory destination = output.getReturnInventory();
        destination.setProductExtractionBypass(true);
        try {
            return productExtractionRecovery.drain((what, amount) ->
                    destination.insert(what, amount, Actionable.MODULATE, retryActionSource));
        } finally {
            destination.setProductExtractionBypass(false);
        }
    }

    private record RoutedInput(GenericStack stack, @Nullable Direction face) {
    }

    private record RouteKey(AEKey what, @Nullable Direction face) {
    }

    private record PendingInput(GenericStack stack, @Nullable Direction face) {
    }

    private final class PlannedInsert {
        private final RoutedInput input;
        private final MEStorage storage;

        private PlannedInsert(RoutedInput input, MEStorage storage) {
            this.input = input;
            this.storage = storage;
        }

        private RoutedInput input() {
            return input;
        }

        private long insert(IActionSource source) {
            return storage.insert(input.stack().what(), input.stack().amount(),
                    Actionable.MODULATE, source);
        }
    }

    private final class TargetCache {
        private final ServerLevel level;
        private final BlockPos targetPos;
        private final EnumMap<Direction, DirectionalTargetCache> byFace = new EnumMap<>(Direction.class);

        private TargetCache(ServerLevel level, BlockPos targetPos) {
            this.level = level;
            this.targetPos = targetPos.immutable();
        }

        private boolean matches(ServerLevel level, BlockPos targetPos) {
            return this.level == level && this.targetPos.equals(targetPos);
        }

        private DirectionalTargetCache get(Direction face) {
            return byFace.computeIfAbsent(face,
                    key -> new DirectionalTargetCache(level, targetPos, key));
        }
    }

    private final class DirectionalTargetCache {
        private final BlockCapabilityCache<ICraftingMachine, Direction> craftingMachine;
        private final BlockCapabilityCache<MEStorage, Direction> directStorage;
        private final Map<AEKeyType, ExternalStorageStrategy> externalStrategies;

        private DirectionalTargetCache(ServerLevel level, BlockPos targetPos, Direction face) {
            craftingMachine = BlockCapabilityCache.create(AECapabilities.CRAFTING_MACHINE,
                    level, targetPos, face, () -> true,
                    PatternP2PTunnelOutputLogic.this::onTargetCapabilityInvalidated);
            directStorage = BlockCapabilityCache.create(AECapabilities.ME_STORAGE,
                    level, targetPos, face, () -> true,
                    PatternP2PTunnelOutputLogic.this::onTargetCapabilityInvalidated);
            externalStrategies = StackWorldBehaviors.createExternalStorageStrategies(level, targetPos, face);
        }

        private @Nullable ICraftingMachine getCraftingMachine() {
            return craftingMachine.getCapability();
        }

        private Map<AEKeyType, MEStorage> resolveStorages(Runnable changeListener) {
            var storage = directStorage.getCapability();
            Map<AEKeyType, MEStorage> result = new IdentityHashMap<>();
            if (storage != null) {
                for (var type : AEKeyTypes.getAll()) {
                    result.put(type, storage);
                }
            } else {
                for (var entry : externalStrategies.entrySet()) {
                    var wrapper = entry.getValue().createWrapper(false, changeListener);
                    if (wrapper != null) {
                        result.put(entry.getKey(), wrapper);
                    }
                }
            }
            return result;
        }
    }

    private void onTargetCapabilityInvalidated() {
        onTargetChanged();
    }

    private final class RetryTicker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(1, 20, false, TickRates.Interface.getMax());
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (pendingInputs.isEmpty()) {
                resetPendingRetryBackoff();
                return TickRateModulation.SLEEP;
            }
            long now = getGameTime();
            if (now < pendingNextRetryTick) {
                return TickRateModulation.SLOWER;
            }
            boolean retryProgress = mainNode.isActive() && retryPending();
            if (retryProgress) {
                resetPendingRetryBackoff();
                return TickRateModulation.URGENT;
            }
            pendingRetryFailures = Math.min(Integer.MAX_VALUE, pendingRetryFailures + 1);
            pendingNextRetryTick = saturatingAdd(now,
                    DispatchBackoffPolicy.pendingDelay(pendingRetryFailures));
            return TickRateModulation.SLOWER;
        }
    }

    private void resetPendingRetryBackoff() {
        pendingRetryFailures = 0;
        pendingNextRetryTick = 0;
    }

    private long getGameTime() {
        var level = output.getLevel();
        return level == null ? 0 : level.getGameTime();
    }

}
