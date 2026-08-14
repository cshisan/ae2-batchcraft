package cn.ae2bc.logic;


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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import cn.ae2bc.platform.ForgeBlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private static final String SYNC_INPUT_SETTINGS = "SyncInputSettings";
    private static final String ENERGY_DISTRIBUTION_MODE = "EnergyDistributionMode";
    private static final String PRODUCT_EXTRACTION_RECOVERY = "ProductExtractionRecovery";

    private final IManagedGridNode mainNode;
    private final PatternP2PTunnelPart output;
    private final IActionSource retryActionSource;
    private final List<PendingInput> pendingInputs = new ArrayList<>();
    private final ExtractionRecoveryQueue productExtractionRecovery;
    private final ReturnBatchTracker<AEKey, AEItemKey> returnBatch = new ReturnBatchTracker<>();
    private @Nullable TargetCache targetCache;
    private ReturnMode returnMode = ReturnMode.UNBLOCKED;
    private boolean syncInputSettings = true;
    private EnergyDistributionMode energyDistributionMode = EnergyDistributionMode.EVEN;

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
        returnBatch.clear();
        persistStateChange(wasAvailable);
    }

    public ReturnMode getReturnMode() {
        return returnMode;
    }

    public void setReturnMode(ReturnMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (returnMode != mode) {
            boolean wasAvailable = canAcceptTask();
            returnMode = mode;
            persistStateChange(wasAvailable);
        }
    }

    public boolean isSyncInputSettings() {
        return syncInputSettings;
    }

    public void setSyncInputSettings(boolean enabled) {
        if (syncInputSettings == enabled) {
            return;
        }
        syncInputSettings = enabled;
        if (enabled) {
            output.synchronizeFromInput();
        }
        output.getHost().markForSave();
    }

    public void applyInputSettings(ReturnMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (!syncInputSettings || returnMode == mode) {
            return;
        }
        boolean wasAvailable = canAcceptTask();
        returnMode = mode;
        persistStateChange(wasAvailable);
    }

    public long filterReturnAmount(AEKey what, long amount) {
        return returnBatch.filter(what, amount, returnMode);
    }

    public void onReturnedStack(GenericStack stack) {
        if (!returnBatch.isActive()) {
            return;
        }
        boolean wasAvailable = canAcceptTask();
        long expectedPrimaryBefore = returnBatch.getExpectedPrimary();
        returnBatch.returned(stack.what(), stack.amount());
        if (returnBatch.getExpectedPrimary() != expectedPrimaryBefore) {
            persistStateChange(wasAvailable);
        }
    }

    public boolean tryAcceptPattern(IPatternDetails pattern, PatternDispatchMetadata metadata,
                                    KeyCounter[] inputs, IActionSource source) {
        if (!mainNode.isActive() || !output.hasConfiguredFrequency()) {
            return false;
        }
        if (!metadata.isValid() || !canAcceptTask(pattern, metadata)) {
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
        if (!metadata.hasExplicitDirections()) {
            var machine = targets.get(automaticFace).getCraftingMachine();
            if (machine != null && machine.acceptsPlans()) {
                if (!beginTask(pattern, metadata)) {
                    return false;
                }
                if (machine.pushPattern(pattern, inputs, automaticFace)) {
                    if (returnBatch.isActive()) {
                        persistStateChange(wasAvailable);
                    }
                    return true;
                }
                cancelTask(metadata);
            }
        }

        if (!pattern.supportsPushInputsToExternalInventory()) {
            return false;
        }
        List<RoutedInput> selected = metadata.hasExplicitDirections()
                ? reconstructProcessingInputs((AEProcessingPattern) pattern, inputs, metadata.outputDirections())
                : null;
        if (selected == null) {
            selected = collectAutomaticInputs(pattern, inputs);
        }
        if (selected.isEmpty()) {
            return false;
        }
        selected = condenseInputs(selected);

        StorageCache storages = new StorageCache(targets);
        List<PlannedInsert> plan = buildPlan(storages, automaticFace, selected, source, true);
        if (plan == null) {
            return false;
        }

        if (!beginTask(pattern, metadata)) {
            return false;
        }
        boolean insertedAny = false;
        List<PendingInput> remainder = new ArrayList<>(plan.size());
        for (var planned : plan) {
            GenericStack stack = planned.input().stack();
            long inserted = planned.storage().insert(stack.what(), stack.amount(),
                    Actionable.MODULATE, source);
            insertedAny |= inserted > 0;
            if (inserted < stack.amount()) {
                remainder.add(new PendingInput(new GenericStack(stack.what(),
                        stack.amount() - Math.max(0, inserted)), planned.input().face()));
            }
        }
        if (!insertedAny) {
            cancelTask(metadata);
            return false;
        }
        pendingInputs.clear();
        pendingInputs.addAll(remainder);
        if (returnBatch.isActive() || !pendingInputs.isEmpty()) {
            persistStateChange(wasAvailable);
        }
        wakeRetryIfNeeded();
        return true;
    }

    private @Nullable List<RoutedInput> reconstructProcessingInputs(AEProcessingPattern pattern,
                                                                     KeyCounter[] inputHolders,
                                                                     InputDirectionData directions) {
        try {
            KeyCounter available = new KeyCounter();
            for (KeyCounter holder : inputHolders) {
                available.addAll(holder);
            }
            var sparseInputs = pattern.getSparseInputs();
            List<RoutedInput> result = new ArrayList<>(sparseInputs.length);
            for (int slot = 0; slot < sparseInputs.length; slot++) {
                GenericStack input = sparseInputs[slot];
                if (input == null || input.amount() <= 0) {
                    continue;
                }
                if (available.get(input.what()) < input.amount()) {
                    return null;
                }
                available.remove(input.what(), input.amount());
                result.add(new RoutedInput(input, directions.getDirection(slot)));
            }
            return result;
        } catch (RuntimeException exception) {
            Ae2bcMod.LOGGER.warn("Unable to reconstruct processing inputs with direction metadata; using automatic routing",
                    exception);
            return null;
        }
    }

    private List<RoutedInput> collectAutomaticInputs(IPatternDetails pattern, KeyCounter[] inputHolders) {
        List<RoutedInput> selected = new ArrayList<>(inputHolders.length);
        try {
            pattern.pushInputsToExternalInventory(inputHolders, (what, amount) -> {
                if (amount > 0) {
                    selected.add(new RoutedInput(new GenericStack(what, amount), null));
                }
            });
        } catch (RuntimeException exception) {
            Ae2bcMod.LOGGER.warn("Unable to collect processing-pattern inputs", exception);
            selected.clear();
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
                    key -> targets.get(key).resolveStorages(PatternP2PTunnelOutputLogic.this::alertRetry));
        }
    }

    private boolean canAcceptTask(IPatternDetails pattern, PatternDispatchMetadata metadata) {
        if (!pendingInputs.isEmpty()) {
            return false;
        }
        GenericStack primary = metadata.primaryOutput();
        return returnBatch.canAccept(pattern.getDefinition(), metadata.declaredOutputs(),
                primary.what(), primary.amount());
    }

    private boolean beginTask(IPatternDetails pattern, PatternDispatchMetadata metadata) {
        GenericStack primary = metadata.primaryOutput();
        return returnBatch.begin(pattern.getDefinition(), metadata.declaredOutputs(),
                primary.what(), primary.amount());
    }

    private void cancelTask(PatternDispatchMetadata metadata) {
        returnBatch.rollback(metadata.primaryOutput().amount());
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
            var planned = plan.get(0);
            long inserted = planned.storage().insert(stack.what(), stack.amount(),
                    Actionable.MODULATE, retryActionSource);
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

    public void readFromNBT(CompoundTag data) {
        energyDistributionMode = data.contains(ENERGY_DISTRIBUTION_MODE)
                ? EnergyDistributionMode.fromId(data.getByte(ENERGY_DISTRIBUTION_MODE))
                : EnergyDistributionMode.EVEN;
        returnMode = data.contains(RETURN_MODE)
                ? ReturnMode.fromId(data.getByte(RETURN_MODE)) : ReturnMode.UNBLOCKED;
        GenericStack loadedRemainingPrimary = data.contains(REMAINING_PRIMARY_OUTPUT, Tag.TAG_COMPOUND)
                ? GenericStack.readTag(data.getCompound(REMAINING_PRIMARY_OUTPUT)) : null;
        syncInputSettings = !data.contains(SYNC_INPUT_SETTINGS) || data.getBoolean(SYNC_INPUT_SETTINGS);
        Map<AEKey, Long> loadedDeclaredOutputs = readCounter(data, DECLARED_OUTPUTS );
        AEItemKey loadedPattern = data.contains(ACTIVE_PATTERN, Tag.TAG_COMPOUND)
                ? AEItemKey.fromTag(data.getCompound(ACTIVE_PATTERN)) : null;
        int loadedTaskCount = data.getInt(ACTIVE_TASK_COUNT);
        returnBatch.load(loadedPattern, loadedTaskCount, loadedDeclaredOutputs,
                loadedRemainingPrimary == null ? null : loadedRemainingPrimary.what(),
                loadedRemainingPrimary == null ? 0 : loadedRemainingPrimary.amount());
        pendingInputs.clear();
        var list = data.getList(PENDING_INPUTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            var stack = GenericStack.readTag(entry);
            if (stack != null && stack.amount() > 0) {
                Direction face = entry.contains(PENDING_INPUT_DIRECTION, Tag.TAG_STRING)
                        ? Direction.byName(entry.getString(PENDING_INPUT_DIRECTION)) : null;
                pendingInputs.add(new PendingInput(stack, face));
            }
        }
        productExtractionRecovery.read(data, PRODUCT_EXTRACTION_RECOVERY );
    }

    public void writeToNBT(CompoundTag data) {
        data.putByte(ENERGY_DISTRIBUTION_MODE, (byte) energyDistributionMode.getId());
        data.putByte(RETURN_MODE, (byte) returnMode.getId());
        data.remove("ActiveReturnMode");
        if (returnBatch.isActive() && returnBatch.getPrimaryKey() != null && returnBatch.getExpectedPrimary() > 0) {
            data.put(REMAINING_PRIMARY_OUTPUT, GenericStack.writeTag(
                    new GenericStack(returnBatch.getPrimaryKey(), returnBatch.getExpectedPrimary())));
        } else {
            data.remove(REMAINING_PRIMARY_OUTPUT);
        }
        if (returnBatch.isActive() && returnBatch.getPattern() != null) {
            data.put(ACTIVE_PATTERN, returnBatch.getPattern().toTag());
        } else {
            data.remove(ACTIVE_PATTERN);
        }
        if (returnBatch.isActive()) {
            data.putInt(ACTIVE_TASK_COUNT, returnBatch.getTaskCount());
        } else {
            data.remove(ACTIVE_TASK_COUNT);
        }
        data.putBoolean(SYNC_INPUT_SETTINGS, syncInputSettings);
        data.put(DECLARED_OUTPUTS, writeCounter(returnBatch.getDeclaredOutputs() ));
        data.remove("ExpectedPrimaryOutputs");
        ListTag list = new ListTag();
        for (var pending : pendingInputs) {
            CompoundTag entry = GenericStack.writeTag(pending.stack());
            if (pending.face() != null) {
                entry.putString(PENDING_INPUT_DIRECTION, pending.face().getName());
            }
            list.add(entry);
        }
        data.put(PENDING_INPUTS, list);
        productExtractionRecovery.write(data, PRODUCT_EXTRACTION_RECOVERY );
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
        productExtractionRecovery.clear();
        returnBatch.clear();
    }

    private static Map<AEKey, Long> readCounter(CompoundTag data, String key) {
        var list = data.getList(key, Tag.TAG_COMPOUND);
        Map<AEKey, Long> result = new LinkedHashMap<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            GenericStack stack = GenericStack.readTag(list.getCompound(i));
            if (stack != null && stack.amount() > 0) {
                result.merge(stack.what(), stack.amount(), PatternP2PTunnelOutputLogic::saturatingAdd);
            }
        }
        return result;
    }

    private static ListTag writeCounter(Map<AEKey, Long> counter) {
        ListTag result = new ListTag();
        for (var entry : counter.entrySet()) {
            if (entry.getValue() > 0) {
                result.add(GenericStack.writeTag(new GenericStack(entry.getKey(), entry.getValue())));
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
        mainNode.ifPresent((grid, node) -> {
            if (!pendingInputs.isEmpty()) {
                grid.getTickManager().alertDevice(node);
            }
            grid.getService(ProductExtractionGridService.class).wake(node, this);
        });
    }

    @Override
    public boolean hasProductExtractionWork() {
        var settings = output.getProductExtractionSettingsFromInput();
        return !productExtractionRecovery.isEmpty() || settings != null && settings.enabled();
    }

    @Override
    public int getProductExtractionInterval() {
        var settings = output.getProductExtractionSettingsFromInput();
        return settings == null ? ProductExtractionSettings.DEFAULT_INTERVAL : settings.interval();
    }

    @Override
    public ProductExtractionTickState tickProductExtraction() {
        if (!mainNode.isActive() || !(output.getLevel() instanceof ServerLevel level)) {
            return ProductExtractionTickState.DISABLED;
        }
        boolean recoveryProgress = drainProductExtractionRecovery();
        var endpointSettings = output.getProductExtractionSettingsFromInput();
        if (endpointSettings == null || !endpointSettings.enabled()) {
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
        var sources = getTargetCache(level, targetPos).get(targetSide).resolveStorages(this::alertRetry);
        int moved = ProductExtractor.extract(ExtractionSource.fromTypeMap(sources),
                output.getReturnInventory(), settings, retryActionSource,
                productExtractionRecovery::queue);
        return moved > 0 || recoveryProgress
                ? ProductExtractionTickState.PROGRESSED : ProductExtractionTickState.NO_PROGRESS;
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

    private record PlannedInsert(RoutedInput input, MEStorage storage) {
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
        private final ForgeBlockCapabilityCache<ICraftingMachine> craftingMachine;
        private final ForgeBlockCapabilityCache<MEStorage> directStorage;
        private final Map<AEKeyType, ExternalStorageStrategy> externalStrategies;

        private DirectionalTargetCache(ServerLevel level, BlockPos targetPos, Direction face) {
            craftingMachine = ForgeBlockCapabilityCache.create(appeng.capabilities.Capabilities.CRAFTING_MACHINE,
                    level, targetPos, face, () -> true,
                    PatternP2PTunnelOutputLogic.this::onTargetCapabilityInvalidated);
            directStorage = ForgeBlockCapabilityCache.create(appeng.capabilities.Capabilities.STORAGE,
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
        alertRetry();
    }

    private final class RetryTicker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(1, 20, false, true, TickRates.Interface.getMax());
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            boolean retryProgress = !pendingInputs.isEmpty() && mainNode.isActive() && retryPending();
            if (retryProgress) {
                return TickRateModulation.URGENT;
            }
            return pendingInputs.isEmpty() ? TickRateModulation.SLEEP : TickRateModulation.SLOWER;
        }
    }
}
