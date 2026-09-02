package cn.ae2bc.logic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.core.settings.TickRates;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.helpers.MachineSource;
import cn.ae2bc.core.dispatch.RoundRobinPolicy;
import cn.ae2bc.core.dispatch.TaskAllocationMode;
import cn.ae2bc.core.dispatch.TaskEndpointSelector;
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternTaskEndpoint;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import appeng.parts.AEBasePart;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns the main-side task admission and endpoint dispatch.
 */
public final class PatternP2PTunnelInputLogic {
    private static final String ROUND_ROBIN_CURSOR = "RoundRobinCursor";
    private static final String RETURN_MODE = "ReturnMode";
    private static final String PATTERN_P2P_UNIT_CONFIGURATION = "PatternP2PUnitConfiguration";
    private static final String PATTERN_P2P_UNIT_CONFIGURATION_REVISION = "PatternP2PUnitConfigurationRevision";
    private static final String PRODUCT_EXTRACTION_ENABLED = "ProductExtractionEnabled";
    private static final String PRODUCT_EXTRACTION_INTERVAL = "ProductExtractionInterval";
    private static final String PRODUCT_EXTRACTION_AMOUNT = "ProductExtractionAmount";
    private static final String PRODUCT_EXTRACTION_REVISION = "ProductExtractionRevision";
    private static final String DISPATCH_MODE = "PatternDispatchMode";
    private static final String TASK_ALLOCATION_MODE = "TaskAllocationMode";
    private static final String BATCH_CONTEXT = "BatchDispatchContext";
    private static final String BATCH_SERIES = "BatchDispatchSeries";

    private final IManagedGridNode mainNode;
    private final PatternP2PTunnelPart input;
    private final IActionSource actionSource;
    private final RandomSource random = RandomSource.create();
    private final PatternMetadataCache patternMetadataCache = new PatternMetadataCache();
    private final MEStorage batchStorage = new BatchStorage();
    private List<PatternP2PTunnelPart> outputSnapshot = List.of();
    private List<PatternTaskEndpoint> taskEndpointSnapshot = List.of();
    private List<PatternTaskEndpoint> availableTaskEndpoints = List.of();
    private boolean outputSnapshotDirty = true;
    private boolean taskEndpointSnapshotDirty = true;
    private boolean outputAvailabilityDirty = true;
    private boolean hasAvailableOutput;
    private int roundRobinCursor;
    private long cursorSaveTick = Long.MIN_VALUE;
    private ReturnMode returnMode = ReturnMode.UNBLOCKED;
    private PatternP2PUnitConfiguration patternP2PUnitConfiguration = PatternP2PUnitConfiguration.DEFAULT;
    private long patternP2PUnitConfigurationRevision;
    private EndpointProductExtractionSettings productExtractionSettings = EndpointProductExtractionSettings.DEFAULT;
    private PatternDispatchMode dispatchMode = PatternDispatchMode.FULL_DISPATCH;
    private TaskAllocationMode taskAllocationMode = TaskAllocationMode.ROUND_ROBIN;
    private BatchDispatchContext batchContext;
    private BatchDispatchSeries batchSeries;
    private int batchRetryFailures;
    private long batchNextRetryTick;
    private IGridNode batchProviderNode;

    public PatternP2PTunnelInputLogic(IManagedGridNode mainNode, PatternP2PTunnelPart input) {
        this.mainNode = mainNode;
        this.input = input;
        this.actionSource = new MachineSource(mainNode::getNode);
        mainNode.addService(IGridTickable.class, new BatchTicker());
    }

    public boolean hasAvailableOutput() {
        getAvailableTaskEndpoints();
        return hasAvailableOutput;
    }

    public ReturnMode getReturnMode() {
        return returnMode;
    }

    public void setReturnMode(ReturnMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (returnMode == mode) {
            return;
        }
        returnMode = mode;
        setPatternP2PUnitConfiguration(patternP2PUnitConfiguration.withReturnMode(mode));
    }

    public PatternP2PUnitConfiguration getPatternP2PUnitConfiguration() {
        return patternP2PUnitConfiguration;
    }

    public long getPatternP2PUnitConfigurationRevision() {
        return patternP2PUnitConfigurationRevision;
    }

    public EndpointProductExtractionSettings getProductExtractionSettings() {
        return productExtractionSettings;
    }

    public PatternDispatchMode getDispatchMode() {
        return dispatchMode;
    }

    public void setDispatchMode(PatternDispatchMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (dispatchMode != mode) {
            dispatchMode = mode;
            alertBatchRetry();
            input.getHost().markForSave();
        }
    }

    public TaskAllocationMode getTaskAllocationMode() {
        return taskAllocationMode;
    }

    public void setTaskAllocationMode(TaskAllocationMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (taskAllocationMode != mode) {
            taskAllocationMode = mode;
            alertBatchRetry();
            input.getHost().markForSave();
        }
    }

    public void setProductExtractionEnabled(boolean enabled) {
        updateProductExtraction(enabled, productExtractionSettings.interval(), productExtractionSettings.amount());
    }

    public void setProductExtractionInterval(int interval) {
        updateProductExtraction(productExtractionSettings.enabled(), interval, productExtractionSettings.amount());
    }

    public void setProductExtractionAmount(int amount) {
        updateProductExtraction(productExtractionSettings.enabled(), productExtractionSettings.interval(), amount);
    }

    private void updateProductExtraction(boolean enabled, int interval, int amount) {
        var updated = new EndpointProductExtractionSettings(enabled, interval, amount,
                productExtractionSettings.revision() + 1);
        if (productExtractionSettings.enabled() == updated.enabled()
                && productExtractionSettings.interval() == updated.interval()
                && productExtractionSettings.amount() == updated.amount()) {
            return;
        }
        productExtractionSettings = updated;
        var unitConfiguration = patternP2PUnitConfiguration.withProductExtraction(
                updated.interval(), updated.amount());
        if (!unitConfiguration.equals(patternP2PUnitConfiguration)) {
            setPatternP2PUnitConfiguration(unitConfiguration);
        } else {
            alertProductExtractionEndpoints();
            input.getHost().markForSave();
        }
    }

    public void setPatternP2PUnitConfiguration(PatternP2PUnitConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (patternP2PUnitConfiguration.equals(configuration)) {
            return;
        }
        patternP2PUnitConfiguration = configuration;
        returnMode = configuration.returnMode();
        patternP2PUnitConfigurationRevision++;
        synchronizeSettings();
        input.getHost().markForSave();
    }

    public void synchronizeSettings() {
        for (var output : getOutputSnapshot()) {
            output.getOutputLogic().applyInputSettings(returnMode);
        }
        for (var manager : getPatternP2PUnitManagers()) {
            manager.getLogic().applyMainConfiguration(patternP2PUnitConfiguration,
                    patternP2PUnitConfigurationRevision);
        }
        alertProductExtractionEndpoints();
    }

    private void alertProductExtractionEndpoints() {
        for (var output : getOutputSnapshot()) {
            output.getOutputLogic().alertRetry();
        }
        var grid = mainNode.getGrid();
        if (grid == null || !input.hasConfiguredFrequency()) {
            return;
        }
        short frequency = input.getFrequency();
        for (var port : grid.getService(PatternP2PTopologyGridService.class)
                .getPortsForFrequency(frequency, UnitPortType.EXTRACT)) {
            port.alertTicking();
        }
    }

    public void invalidateOutputs() {
        outputSnapshotDirty = true;
        taskEndpointSnapshotDirty = true;
        outputAvailabilityDirty = true;
        alertBatchRetry();
    }

    public void invalidateOutputAvailability() {
        outputAvailabilityDirty = true;
        alertBatchRetry();
    }

    public void resetAllTaskStates() {
        var grid = mainNode.getGrid();
        if (grid == null || !input.hasConfiguredFrequency()) {
            return;
        }
        short frequency = input.getFrequency();
        var topology = grid.getService(PatternP2PTopologyGridService.class);
        for (var output : topology.getOutputs(frequency)) {
            output.resetTaskState();
        }
        for (var manager : topology.getManagers(frequency)) {
            manager.resetTaskState();
        }
        if (batchContext != null) {
            batchContext.clearHeld();
            batchContext = null;
            input.getHost().markForSave();
        }
        batchSeries = null;
        batchProviderNode = null;
        invalidateOutputAvailability();
    }

    public int getActiveTaskCount() {
        int count = 0;
        for (var endpoint : getTaskEndpoints()) {
            if (endpoint.isTaskActive()) {
                count++;
            }
        }
        return count;
    }

    public boolean canAcceptPlans() {
        return batchContext == null && hasAvailableOutput();
    }

    public MEStorage getBatchStorage() {
        return batchStorage;
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        if (!mainNode.isActive() || !input.hasConfiguredFrequency() || batchContext != null) {
            return false;
        }

        List<PatternTaskEndpoint> outputs = getTaskEndpoints();
        int size = outputs.size();
        var metadata = patternMetadataCache.get(pattern, input.getLevel());
        if (!metadata.isValid()) {
            return false;
        }
        if (dispatchMode == PatternDispatchMode.BATCH_DISTRIBUTION && metadata.batchCount() > 1) {
            UUID sessionId = batchSeries == null ? UUID.randomUUID() : batchSeries.sessionId();
            BatchDispatchContext context = BatchDispatchContext.create(
                    pattern, inputs, input.getLevel(), sessionId, metadata);
            if (context != null) {
                if (batchSeries != null && !batchSeries.matches(context)) {
                    if (hasActiveBatchSession(batchSeries.sessionId())) {
                        return false;
                    }
                    batchProviderNode = null;
                    context = BatchDispatchContext.create(
                            pattern, inputs, input.getLevel(), UUID.randomUUID(), metadata);
                    if (context == null) {
                        return false;
                    }
                    batchSeries = null;
                }
                if (batchSeries == null) {
                    batchSeries = BatchDispatchSeries.from(context);
                }
                batchContext = context;
                planNextRound();
                input.getHost().markForSave();
                alertBatchRetry();
                return false;
            }
        }
        return pushPatternComplete(pattern, metadata, inputs, outputs, size);
    }

    private boolean pushPatternComplete(IPatternDetails pattern, PatternDispatchMetadata metadata,
                                        KeyCounter[] inputs, List<PatternTaskEndpoint> outputs, int size) {
        List<PatternTaskEndpoint> candidates = taskAllocationMode == TaskAllocationMode.RANDOM
                ? getAvailableTaskEndpoints() : outputs;
        int acceptedIndex = TaskEndpointSelector.select(taskAllocationMode, roundRobinCursor,
                candidates.size(), random::nextInt, index -> {
                    var output = candidates.get(index);
                    if (!output.isOperationalTaskEndpoint() || !output.canAcceptTask()) {
                        return false;
                    }
                    return output.tryAcceptPattern(pattern, metadata, inputs, actionSource);
                });
        if (acceptedIndex >= 0) {
            outputAvailabilityDirty = true;
            if (taskAllocationMode == TaskAllocationMode.ROUND_ROBIN) {
                roundRobinCursor = RoundRobinPolicy.advance(acceptedIndex, size);
                markCursorForSave();
            }
            return true;
        }
        return false;
    }

    private boolean planNextRound() {
        BatchDispatchContext context = batchContext;
        if (context == null || context.roundUnits() > 0 || context.remainingUnits() <= 0) {
            return false;
        }
        IPatternDetails pattern = context.decodePattern(input.getLevel());
        PatternDispatchMetadata atomicMetadata = context.dispatchMetadata(input.getLevel());
        if (pattern == null || atomicMetadata == null || !atomicMetadata.isValid()) {
            return false;
        }
        List<PatternTaskEndpoint> endpoints = getTaskEndpoints();
        int size = endpoints.size();
        long[] capacities = new long[size];
        for (int offset = 0; offset < size; offset++) {
            int index = Math.floorMod(roundRobinCursor + offset, size);
            PatternTaskEndpoint endpoint = endpoints.get(index);
            if (endpoint.isOperationalTaskEndpoint()) {
                capacities[index] = endpoint.getMaximumAcceptedAtomicUnits(
                        pattern, atomicMetadata, context.atomicInputs(), context.remainingUnits(),
                        context.sessionId(), actionSource);
            }
        }
        long[] allocations = TaskAllocationPlanner.distribute(
                context.remainingUnits(), capacities, roundRobinCursor,
                taskAllocationMode, random::nextInt);
        long plannedUnits = sum(allocations);
        if (plannedUnits <= 0) {
            return false;
        }
        java.util.Map<String, Long> roundPlan = new java.util.LinkedHashMap<>();
        for (int i = 0; i < allocations.length; i++) {
            if (allocations[i] > 0) {
                roundPlan.merge(endpoints.get(i).getDispatchId(), allocations[i], PatternP2PTunnelInputLogic::saturatingAdd);
            }
        }
        if (!context.startRound(roundPlan)) {
            return false;
        }
        input.getHost().markForSave();
        alertBatchProvider();
        return true;
    }

    private boolean dispatchReadyRound() {
        BatchDispatchContext context = batchContext;
        if (context == null || !context.isRoundReady()) {
            return false;
        }
        IPatternDetails pattern = context.decodePattern(input.getLevel());
        PatternDispatchMetadata atomicMetadata = context.dispatchMetadata(input.getLevel());
        if (pattern == null || atomicMetadata == null || !atomicMetadata.isValid()) {
            return false;
        }
        List<PatternTaskEndpoint> endpoints = getTaskEndpoints();
        int size = endpoints.size();
        java.util.Map<String, PatternTaskEndpoint> endpointsById = new java.util.LinkedHashMap<>();
        endpoints.forEach(endpoint -> endpointsById.put(endpoint.getDispatchId(), endpoint));
        int lastAccepted = -1;
        for (var allocation : context.roundAllocations().entrySet()) {
            PatternTaskEndpoint endpoint = endpointsById.get(allocation.getKey());
            long units = allocation.getValue();
            if (endpoint == null || !endpoint.isOperationalTaskEndpoint()) {
                continue;
            }
            long accepted = endpoint.getMaximumAcceptedAtomicUnits(
                    pattern, atomicMetadata, context.atomicInputs(), units, context.sessionId(), actionSource);
            if (accepted < units || !endpoint.tryAcceptPatternAtomicUnits(
                    pattern, atomicMetadata, context.atomicInputs(), units, context.sessionId(), actionSource)) {
                continue;
            }
            context.dispatched(allocation.getKey(), units);
            lastAccepted = endpoints.indexOf(endpoint);
        }
        if (lastAccepted < 0) {
            return false;
        }
        context.finishRoundIfEmpty();
        if (taskAllocationMode == TaskAllocationMode.ROUND_ROBIN) {
            roundRobinCursor = RoundRobinPolicy.advance(lastAccepted, size);
            markCursorForSave();
        }
        outputAvailabilityDirty = true;
        if (context.isComplete()) {
            batchContext = null;
        } else {
            planNextRound();
        }
        input.getHost().markForSave();
        return true;
    }

    public void addDrops(List<ItemStack> drops) {
        if (batchContext != null) {
            var grid = mainNode.getGrid();
            if (grid != null) {
                batchContext.returnHeldTo(grid.getStorageService().getInventory(), actionSource);
            }
            batchContext.addHeldDrops(drops, input.getLevel(), input.getBlockEntity().getBlockPos());
        }
    }

    public void clearContent() {
        if (batchContext != null) {
            batchContext.clearHeld();
            batchContext = null;
        }
        batchSeries = null;
        batchProviderNode = null;
        resetBatchRetryBackoff();
    }

    private long getGameTime() {
        var level = input.getLevel();
        return level == null ? 0 : level.getGameTime();
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long sum(long[] values) {
        long result = 0;
        for (long value : values) {
            result = saturatingAdd(result, Math.max(0, value));
        }
        return result;
    }

    private List<PatternP2PTunnelPart> getOutputSnapshot() {
        if (outputSnapshotDirty) {
            outputSnapshot = input.getOutputs().stream()
                    .filter(PatternP2PTunnelPart::isStandardOutput)
                    .toList();
            outputSnapshotDirty = false;
            outputAvailabilityDirty = true;
        }
        return outputSnapshot;
    }

    private List<PatternTaskEndpoint> getTaskEndpoints() {
        if (taskEndpointSnapshotDirty) {
            var endpoints = new java.util.ArrayList<PatternTaskEndpoint>(getOutputSnapshot());
            endpoints.addAll(getPatternP2PUnitManagers());
            taskEndpointSnapshot = List.copyOf(endpoints);
            taskEndpointSnapshotDirty = false;
            outputAvailabilityDirty = true;
        }
        return taskEndpointSnapshot;
    }

    private List<PatternTaskEndpoint> getAvailableTaskEndpoints() {
        if (!mainNode.isActive() || !input.hasConfiguredFrequency()) {
            hasAvailableOutput = false;
            return List.of();
        }
        if (outputAvailabilityDirty) {
            availableTaskEndpoints = getTaskEndpoints().stream()
                    .filter(PatternTaskEndpoint::isOperationalTaskEndpoint)
                    .filter(PatternTaskEndpoint::canAcceptTask)
                    .toList();
            hasAvailableOutput = !availableTaskEndpoints.isEmpty();
            outputAvailabilityDirty = false;
        }
        return availableTaskEndpoints;
    }

    private List<PatternP2PUnitManagerPart> getPatternP2PUnitManagers() {
        var grid = mainNode.getGrid();
        if (grid == null || !input.hasConfiguredFrequency()) {
            return List.of();
        }
        return grid.getService(PatternP2PTopologyGridService.class).getManagers(input.getFrequency());
    }

    private void markCursorForSave() {
        var level = input.getLevel();
        if (level == null) {
            input.getHost().markForSave();
            return;
        }
        long currentTick = level.getGameTime();
        if (cursorSaveTick != currentTick) {
            cursorSaveTick = currentTick;
            input.getHost().markForSave();
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        resetBatchRetryBackoff();
        dispatchMode = data.contains(DISPATCH_MODE)
                ? PatternDispatchMode.fromId(data.getByte(DISPATCH_MODE))
                : PatternDispatchMode.FULL_DISPATCH;
        taskAllocationMode = data.contains(TASK_ALLOCATION_MODE)
                ? TaskAllocationMode.fromId(data.getByte(TASK_ALLOCATION_MODE))
                : TaskAllocationMode.ROUND_ROBIN;
        roundRobinCursor = data.getInt(ROUND_ROBIN_CURSOR);
        returnMode = data.contains(RETURN_MODE)
                ? ReturnMode.fromId(data.getByte(RETURN_MODE)) : ReturnMode.UNBLOCKED;
        patternP2PUnitConfiguration = data.contains(PATTERN_P2P_UNIT_CONFIGURATION)
                ? PatternP2PUnitConfiguration.read(data.getCompound(PATTERN_P2P_UNIT_CONFIGURATION))
                : PatternP2PUnitConfiguration.DEFAULT.withReturnMode(returnMode);
        returnMode = patternP2PUnitConfiguration.returnMode();
        patternP2PUnitConfigurationRevision = data.getLong(PATTERN_P2P_UNIT_CONFIGURATION_REVISION);
        productExtractionSettings = new EndpointProductExtractionSettings(
                data.getBoolean(PRODUCT_EXTRACTION_ENABLED),
                data.contains(PRODUCT_EXTRACTION_INTERVAL)
                        ? data.getInt(PRODUCT_EXTRACTION_INTERVAL) : ProductExtractionSettings.DEFAULT_INTERVAL,
                data.contains(PRODUCT_EXTRACTION_AMOUNT)
                        ? data.getInt(PRODUCT_EXTRACTION_AMOUNT) : ProductExtractionSettings.DEFAULT_AMOUNT,
                data.getLong(PRODUCT_EXTRACTION_REVISION));
        CompoundTag unitConfiguration = data.getCompound(PATTERN_P2P_UNIT_CONFIGURATION);
        if (!unitConfiguration.contains(PRODUCT_EXTRACTION_INTERVAL)
                || !unitConfiguration.contains(PRODUCT_EXTRACTION_AMOUNT)) {
            patternP2PUnitConfiguration = patternP2PUnitConfiguration.withProductExtraction(
                    productExtractionSettings.interval(), productExtractionSettings.amount());
        }
        batchContext = data.contains(BATCH_CONTEXT, Tag.TAG_COMPOUND)
                ? BatchDispatchContext.read(data.getCompound(BATCH_CONTEXT), registries) : null;
        batchSeries = data.contains(BATCH_SERIES, Tag.TAG_COMPOUND)
                ? BatchDispatchSeries.read(data.getCompound(BATCH_SERIES), registries) : null;
        if (batchContext != null && (batchSeries == null || !batchSeries.matches(batchContext))) {
            batchSeries = BatchDispatchSeries.from(batchContext);
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.putByte(DISPATCH_MODE, (byte) dispatchMode.ordinal());
        data.putByte(TASK_ALLOCATION_MODE, (byte) taskAllocationMode.getId());
        data.putInt(ROUND_ROBIN_CURSOR, roundRobinCursor);
        data.putByte(RETURN_MODE, (byte) returnMode.getId());
        data.put(PATTERN_P2P_UNIT_CONFIGURATION, patternP2PUnitConfiguration.write());
        data.putLong(PATTERN_P2P_UNIT_CONFIGURATION_REVISION, patternP2PUnitConfigurationRevision);
        data.putBoolean(PRODUCT_EXTRACTION_ENABLED, productExtractionSettings.enabled());
        data.putInt(PRODUCT_EXTRACTION_INTERVAL, productExtractionSettings.interval());
        data.putInt(PRODUCT_EXTRACTION_AMOUNT, productExtractionSettings.amount());
        data.putLong(PRODUCT_EXTRACTION_REVISION, productExtractionSettings.revision());
        if (batchContext != null) {
            data.put(BATCH_CONTEXT, batchContext.write(registries));
        } else {
            data.remove(BATCH_CONTEXT);
        }
        if (batchSeries != null) {
            data.put(BATCH_SERIES, batchSeries.write(registries));
        } else {
            data.remove(BATCH_SERIES);
        }
    }

    public void alertBatchRetry() {
        resetBatchRetryBackoff();
        mainNode.ifPresent((grid, node) -> {
            if (batchContext != null) {
                grid.getTickManager().alertDevice(node);
            }
        });
    }

    private void resetBatchRetryBackoff() {
        batchRetryFailures = 0;
        batchNextRetryTick = 0;
    }

    private boolean retryBatchDispatch() {
        if (batchContext == null || !mainNode.isActive()) {
            return false;
        }
        if (batchContext.isRoundReady()) {
            return dispatchReadyRound();
        }
        return batchContext.roundUnits() == 0 && planNextRound();
    }

    private IGridNode getAdjacentBatchProviderNode(IActionSource source) {
        if (batchContext == null || !mainNode.isActive() || !input.hasConfiguredFrequency()
                || batchContext.roundUnits() <= 0 || batchContext.isRoundReady()) {
            return null;
        }
        return source.machine()
                .map(appeng.api.networking.security.IActionHost::getActionableNode)
                .filter(Objects::nonNull)
                .filter(node -> node.getOwner() instanceof PatternProviderLogicHost)
                .filter(node -> isAdjacentProvider(node.getOwner()))
                .orElse(null);
    }

    private boolean hasActiveBatchSession(UUID sessionId) {
        for (PatternTaskEndpoint endpoint : getTaskEndpoints()) {
            if (endpoint.hasActiveBatchSession(sessionId)) {
                return true;
            }
        }
        return false;
    }

    private void alertBatchProvider() {
        IGridNode providerNode = batchProviderNode;
        if (providerNode == null) {
            return;
        }
        var grid = providerNode.getGrid();
        if (grid == null || grid != mainNode.getGrid() || !isAdjacentProvider(providerNode.getOwner())) {
            batchProviderNode = null;
            return;
        }
        grid.getTickManager().alertDevice(providerNode);
    }

    private boolean isAdjacentProvider(Object owner) {
        var side = input.getSide();
        if (side == null) {
            return false;
        }
        var expected = input.getBlockEntity().getBlockPos().relative(side);
        if (owner instanceof BlockEntity blockEntity) {
            return blockEntity.getBlockPos().equals(expected);
        }
        return owner instanceof AEBasePart part
                && part.getHost().getBlockEntity().getBlockPos().equals(expected);
    }

    private final class BatchStorage implements MEStorage {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            IGridNode providerNode = getAdjacentBatchProviderNode(source);
            if (providerNode == null) {
                return 0;
            }
            long accepted = batchContext.insert(what, amount, mode, source);
            if (accepted > 0 && mode == Actionable.MODULATE) {
                batchProviderNode = providerNode;
                input.getHost().markForSave();
                if (batchContext.isRoundReady()) {
                    alertBatchRetry();
                }
            }
            return accepted;
        }

        @Override
        public Component getDescription() {
            return input.getPartItem().asItem().getDescription();
        }
    }

    private final class BatchTicker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(1, 20, false, TickRates.Interface.getMax());
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (batchContext == null || !mainNode.isActive()) {
                resetBatchRetryBackoff();
                return TickRateModulation.SLEEP;
            }
            if (batchContext.roundUnits() == 0 && batchContext.remainingUnits() > 0) {
                resetBatchRetryBackoff();
                planNextRound();
                return TickRateModulation.URGENT;
            }
            long now = getGameTime();
            if (now < batchNextRetryTick) {
                return TickRateModulation.SLOWER;
            }
            if (retryBatchDispatch()) {
                resetBatchRetryBackoff();
                return TickRateModulation.URGENT;
            }
            batchRetryFailures = Math.min(Integer.MAX_VALUE, batchRetryFailures + 1);
            batchNextRetryTick = saturatingAdd(now, DispatchBackoffPolicy.pendingDelay(batchRetryFailures));
            return TickRateModulation.SLOWER;
        }
    }
}
