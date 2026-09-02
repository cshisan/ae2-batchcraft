package cn.ae2bc.logic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.me.helpers.MachineSource;
import cn.ae2bc.core.dispatch.RoundRobinPolicy;
import cn.ae2bc.core.dispatch.TaskAllocationMode;
import cn.ae2bc.core.dispatch.TaskEndpointSelector;
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternTaskEndpoint;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;

import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns the main-side task admission and round-robin dispatch.
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
    private static final String TASK_ALLOCATION_MODE = "TaskAllocationMode";

    private final IManagedGridNode mainNode;
    private final PatternP2PTunnelPart input;
    private final IActionSource actionSource;
    private final PatternMetadataCache patternMetadataCache = new PatternMetadataCache();
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
    private TaskAllocationMode taskAllocationMode = TaskAllocationMode.ROUND_ROBIN;

    public PatternP2PTunnelInputLogic(IManagedGridNode mainNode, PatternP2PTunnelPart input) {
        this.mainNode = mainNode;
        this.input = input;
        this.actionSource = new MachineSource(mainNode::getNode);
    }

    public boolean hasAvailableOutput() {
        return !getAvailableTaskEndpoints().isEmpty();
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

    public TaskAllocationMode getTaskAllocationMode() {
        return taskAllocationMode;
    }

    public void setTaskAllocationMode(TaskAllocationMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (taskAllocationMode != mode) {
            taskAllocationMode = mode;
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
    }

    public void invalidateOutputAvailability() {
        outputAvailabilityDirty = true;
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

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        if (!mainNode.isActive() || !input.hasConfiguredFrequency()) {
            return false;
        }

        List<PatternTaskEndpoint> outputs = taskAllocationMode == TaskAllocationMode.RANDOM
                ? getAvailableTaskEndpoints() : getTaskEndpoints();
        int size = outputs.size();
        var metadata = patternMetadataCache.get(pattern);
        if (!metadata.isValid()) {
            return false;
        }
        int acceptedIndex = TaskEndpointSelector.select(taskAllocationMode, roundRobinCursor,
                size, ThreadLocalRandom.current()::nextInt, index -> {
            var output = outputs.get(index);
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

    public void readFromNBT(CompoundTag data) {
        roundRobinCursor = data.getInt(ROUND_ROBIN_CURSOR);
        taskAllocationMode = data.contains(TASK_ALLOCATION_MODE)
                ? TaskAllocationMode.fromId(data.getByte(TASK_ALLOCATION_MODE))
                : TaskAllocationMode.ROUND_ROBIN;
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
    }

    public void writeToNBT(CompoundTag data) {
        data.putInt(ROUND_ROBIN_CURSOR, roundRobinCursor);
        data.putByte(TASK_ALLOCATION_MODE, (byte) taskAllocationMode.getId());
        data.putByte(RETURN_MODE, (byte) returnMode.getId());
        data.put(PATTERN_P2P_UNIT_CONFIGURATION, patternP2PUnitConfiguration.write());
        data.putLong(PATTERN_P2P_UNIT_CONFIGURATION_REVISION, patternP2PUnitConfigurationRevision);
        data.putBoolean(PRODUCT_EXTRACTION_ENABLED, productExtractionSettings.enabled());
        data.putInt(PRODUCT_EXTRACTION_INTERVAL, productExtractionSettings.interval());
        data.putInt(PRODUCT_EXTRACTION_AMOUNT, productExtractionSettings.amount());
        data.putLong(PRODUCT_EXTRACTION_REVISION, productExtractionSettings.revision());
    }
}
