package cn.ae2bc.part;

import appeng.api.AECapabilities;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.behaviors.PlacementStrategy;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackFluidStorage;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import appeng.api.util.AECableType;
import appeng.core.settings.TickRates;
import appeng.me.helpers.MachineSource;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;
import appeng.parts.p2p.P2PModels;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.parts.automation.FluidPickupStrategy;
import appeng.util.Platform;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.logic.PatternP2PUnitIdentityColors;
import cn.ae2bc.logic.PatternP2PUnitPortType;
import cn.ae2bc.logic.PatternP2PEnergyGridService;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.logic.ProductExtractionTickState;
import cn.ae2bc.logic.ProductExtractionGridService;
import cn.ae2bc.logic.ProductExtractionTask;
import cn.ae2bc.logic.ProductExtractionSettings;
import cn.ae2bc.logic.ProductExtractor;
import cn.ae2bc.logic.ExtractionSource;
import cn.ae2bc.logic.ExtractionRecoveryQueue;
import cn.ae2bc.client.model.PatternP2PUnitModelData;
import cn.ae2bc.pattern.MaterialOutputForm;
import cn.ae2bc.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.client.model.data.ModelData;
import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A unit endpoint. Task ports are gated by their manager; energy ports operate continuously. */
public final class PatternP2PUnitPortPart extends AEBasePart implements IGridTickable, ProductExtractionTask {
    private static final String PRODUCT_EXTRACTION_RECOVERY = "ProductExtractionRecovery";
    private static final String BOUND_FREQUENCY_TAG = "BoundFrequency";
    private static final ResourceLocation IDENTITY_MODEL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "part/p2p/pattern_p2p_unit_port_identity");
    private static final Map<PatternP2PUnitPortType, PatternP2PUnitPortModels> MODELS = createModels();

    private final PatternP2PUnitPortType type;
    private final IActionSource actionSource = new MachineSource(this);
    private final PortReturnInventory returnInventory = new PortReturnInventory();
    private final IItemHandler returnItemHandler = new GenericStackItemStorage(returnInventory);
    private final IFluidHandler returnFluidHandler = new GenericStackFluidStorage(returnInventory);
    private final ExtractionRecoveryQueue productExtractionRecovery;
    private @Nullable UUID boundPatternP2PUnitId;
    private @Nullable PatternP2PUnitManagerPart cachedManager;
    private short boundFrequency;
    private @Nullable PlacementStrategy placementStrategy;
    private @Nullable List<PickupStrategy> breakStrategies;
    private @Nullable PickupStrategy collectFluidStrategy;
    private @Nullable Map<appeng.api.stacks.AEKeyType, ExternalStorageStrategy> externalStrategies;
    private @Nullable BlockCapabilityCache<IEnergyStorage, Direction> energyTargetCache;
    private @Nullable BlockCapabilityCache<MEStorage, Direction> extractionTargetCache;
    private int redstonePower;
    private boolean redstoneWorldStateDirty = true;
    private long taskStartTick = Long.MIN_VALUE;

    public PatternP2PUnitPortPart(IPartItem<?> partItem, PatternP2PUnitPortType type) {
        super(partItem);
        this.type = type;
        this.productExtractionRecovery = new ExtractionRecoveryQueue(() -> getHost().markForSave());
        getMainNode().addService(IGridTickable.class, this);
    }

    private static Map<PatternP2PUnitPortType, PatternP2PUnitPortModels> createModels() {
        Map<PatternP2PUnitPortType, PatternP2PUnitPortModels> result = new EnumMap<>(PatternP2PUnitPortType.class);
        for (PatternP2PUnitPortType type : PatternP2PUnitPortType.values()) {
            ResourceLocation front = ResourceLocation.fromNamespaceAndPath(
                    Ae2bcMod.MOD_ID, "part/p2p/pattern_p2p_unit_port_" + type.name().toLowerCase());
            result.put(type, new PatternP2PUnitPortModels(front));
        }
        return result;
    }

    public static void registerModels() {
        PartModels.registerModels(MODELS.values().stream()
                .flatMap(models -> models.models().stream())
                .flatMap(model -> model.getModels().stream())
                .toList());
    }

    public PatternP2PUnitPortType getType() {
        return type;
    }

    public @Nullable UUID getBoundPatternP2PUnitId() {
        return boundPatternP2PUnitId;
    }

    public short getBoundFrequency() {
        PatternP2PUnitManagerPart manager = getManager();
        return manager == null ? boundFrequency : manager.getFrequency();
    }

    public boolean isBoundUnitTaskActive() {
        PatternP2PUnitManagerPart manager = getManager();
        return manager != null && manager.isTaskActive();
    }

    public boolean isBoundTo(PatternP2PUnitManagerPart manager) {
        return boundPatternP2PUnitId != null
                && boundPatternP2PUnitId.equals(manager.getPatternP2PUnitId())
                && (boundFrequency == 0 || boundFrequency == manager.getFrequency());
    }

    public long insertInput(PatternP2PUnitManagerPart manager, GenericStack stack,
                            MaterialOutputForm form, Actionable mode) {
        if (!isBoundTo(manager) || stack == null || stack.amount() <= 0
                || PatternP2PUnitPortType.forOutputForm(form) != type || !form.supports(stack.what())) {
            return 0;
        }
        // Admission probes happen before task activation; world mutation does not.
        if (mode == Actionable.MODULATE && !manager.getLogic().isTaskOperational()) {
            return 0;
        }
        return switch (type) {
            case DROP, PLACE -> getPlacementStrategy().placeInWorld(
                    stack.what(), stack.amount(), mode, type == PatternP2PUnitPortType.DROP);
            case TRANSFER -> insertIntoTarget(stack.what(), stack.amount(), mode);
            default -> 0;
        };
    }

    private long insertIntoTarget(AEKey what, long amount, Actionable mode) {
        var strategy = getExternalStrategies().get(what.getType());
        if (strategy == null) {
            return 0;
        }
        MEStorage storage = strategy.createWrapper(false, this::wake);
        return storage == null ? 0 : storage.insert(what, amount, mode, actionSource);
    }

    private PlacementStrategy getPlacementStrategy() {
        if (placementStrategy == null && getLevel() instanceof ServerLevel level && getSide() != null) {
            var target = getBlockEntity().getBlockPos().relative(getSide());
            placementStrategy = StackWorldBehaviors.createPlacementStrategies(level, target,
                    getSide().getOpposite(), getBlockEntity(), getMainNode().getNode().getOwningPlayerProfileId());
        }
        return placementStrategy == null ? PlacementStrategy.noop() : placementStrategy;
    }

    private Map<appeng.api.stacks.AEKeyType, ExternalStorageStrategy> getExternalStrategies() {
        if (externalStrategies == null && getLevel() instanceof ServerLevel level && getSide() != null) {
            externalStrategies = StackWorldBehaviors.createExternalStorageStrategies(
                    level, getBlockEntity().getBlockPos().relative(getSide()), getSide().getOpposite());
        }
        return externalStrategies == null ? Map.of() : externalStrategies;
    }

    private List<PickupStrategy> getBreakStrategies() {
        if (breakStrategies == null && getLevel() instanceof ServerLevel level && getSide() != null) {
            breakStrategies = StackWorldBehaviors.createPickupStrategies(level,
                    getBlockEntity().getBlockPos().relative(getSide()), getSide().getOpposite(),
                    getBlockEntity(), ItemEnchantments.EMPTY,
                    getMainNode().getNode().getOwningPlayerProfileId());
        }
        return breakStrategies == null ? List.of() : breakStrategies;
    }

    private @Nullable PickupStrategy getCollectFluidStrategy() {
        if (collectFluidStrategy == null && getLevel() instanceof ServerLevel level && getSide() != null) {
            collectFluidStrategy = new FluidPickupStrategy(level,
                    getBlockEntity().getBlockPos().relative(getSide()), getSide().getOpposite(),
                    getBlockEntity(), ItemEnchantments.EMPTY,
                    getMainNode().getNode().getOwningPlayerProfileId());
        }
        return collectFluidStrategy;
    }

    private @Nullable PatternP2PUnitManagerPart getManager() {
        var grid = getMainNode().getGrid();
        if (grid == null || boundPatternP2PUnitId == null) {
            cachedManager = null;
            return null;
        }
        if (cachedManager != null && cachedManager.getMainNode().getGrid() == grid
                && isBoundTo(cachedManager)) {
            return cachedManager;
        }
        cachedManager = null;
        cachedManager = grid.getService(PatternP2PTopologyGridService.class).findManager(boundPatternP2PUnitId);
        if (cachedManager != null && !isBoundTo(cachedManager)) {
            cachedManager = null;
        }
        return cachedManager;
    }

    private boolean runBreakStrategies(PatternP2PUnitManagerPart manager) {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        boolean changed = false;
        for (PickupStrategy strategy : getBreakStrategies()) {
            strategy.reset();
            PickupStrategy.Result result = strategy.tryPickup(grid.getEnergyService(),
                    (what, amount, mode) -> handleCollected(manager, what, amount, mode));
            changed |= result == PickupStrategy.Result.PICKED_UP;
        }
        return changed;
    }

    private boolean collectDroppedItems(PatternP2PUnitManagerPart manager) {
        if (!(getLevel() instanceof ServerLevel level) || getSide() == null) {
            return false;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        boolean changed = false;
        var target = getBlockEntity().getBlockPos().relative(getSide());
        List<PickupStrategy> strategies = getBreakStrategies();
        for (PickupStrategy strategy : strategies) {
            strategy.reset();
        }
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(target))) {
            for (PickupStrategy strategy : strategies) {
                if (strategy.canPickUpEntity(entity)) {
                    changed |= strategy.pickUpEntity(grid.getEnergyService(),
                            (what, amount, mode) -> manager.getLogic()
                                    .insertReturned(what, amount, mode), entity);
                    break;
                }
            }
        }
        return changed;
    }

    private boolean collectSourceFluid(PatternP2PUnitManagerPart manager) {
        var grid = getMainNode().getGrid();
        PickupStrategy strategy = getCollectFluidStrategy();
        if (grid == null || strategy == null) {
            return false;
        }
        strategy.reset();
        return strategy.tryPickup(grid.getEnergyService(),
                (what, amount, mode) -> handleCollected(manager, what, amount, mode))
                == PickupStrategy.Result.PICKED_UP;
    }

    private boolean collect(PatternP2PUnitManagerPart manager) {
        return collectDroppedItems(manager) | collectSourceFluid(manager);
    }

    private long handleCollected(PatternP2PUnitManagerPart manager, AEKey what, long amount, Actionable mode) {
        var configuration = manager.getLogic().getEffectiveConfiguration();
        long accepted = manager.getLogic().simulateReturned(what, amount);
        if (accepted < amount || mode == Actionable.SIMULATE) {
            return accepted;
        }
        if (configuration.breakRecovery() || what instanceof AEFluidKey) {
            return manager.getLogic().insertReturned(what, amount, Actionable.MODULATE);
        }
        if (what instanceof AEItemKey itemKey && getLevel() instanceof ServerLevel level) {
            List<ItemStack> drops = new java.util.ArrayList<>();
            itemKey.addDrops(amount, drops, level, getBlockEntity().getBlockPos().relative(getSide()));
            Platform.spawnDrops(level, getBlockEntity().getBlockPos().relative(getSide()), drops);
            return amount;
        }
        return 0;
    }

    private boolean updateRedstone(PatternP2PUnitManagerPart manager) {
        var settings = manager.getLogic().getEffectiveConfiguration();
        long activeTicks = Math.max(0, getLevel().getGameTime() - taskStartTick);
        int next = switch (settings.redstoneMode()) {
            case CONTINUOUS -> settings.redstoneStrength();
            case SINGLE_TRIGGER -> activeTicks < settings.pulseWidthTicks() ? settings.redstoneStrength() : 0;
            case PERIODIC_PULSE -> activeTicks % settings.pulsePeriodTicks() < settings.pulseWidthTicks()
                    ? settings.redstoneStrength() : 0;
        };
        setRedstonePower(next);
        return settings.redstoneMode() == RedstoneOutputMode.PERIODIC_PULSE
                || settings.redstoneMode() == RedstoneOutputMode.SINGLE_TRIGGER
                && activeTicks <= settings.pulseWidthTicks();
    }

    @Override
    public boolean hasProductExtractionWork() {
        if (type != PatternP2PUnitPortType.EXTRACT) {
            return false;
        }
        var manager = getManager();
        return manager != null && (!productExtractionRecovery.isEmpty()
                || manager.getLogic().isTaskOperational());
    }

    @Override
    public int getProductExtractionInterval() {
        var manager = getManager();
        return manager == null ? ProductExtractionSettings.DEFAULT_INTERVAL
                : manager.getLogic().getEffectiveConfiguration().productExtractionInterval();
    }

    @Override
    public ProductExtractionTickState tickProductExtraction() {
        var manager = getManager();
        if (manager == null || !(getLevel() instanceof ServerLevel level)) {
            return ProductExtractionTickState.DISABLED;
        }
        boolean recoveryProgress = !productExtractionRecovery.isEmpty()
                && drainProductExtractionRecovery(manager);
        if (!manager.getLogic().isTaskOperational()) {
            if (recoveryProgress) {
                return ProductExtractionTickState.PROGRESSED;
            }
            return productExtractionRecovery.isEmpty()
                    ? ProductExtractionTickState.DISABLED : ProductExtractionTickState.NO_PROGRESS;
        }
        var configuration = manager.getLogic().getEffectiveConfiguration();
        int interval = configuration.productExtractionInterval();
        int amount = configuration.productExtractionAmount();
        int moved = ProductExtractor.extract(ExtractionSource.fromTypeMap(resolveExtractionSources(level)),
                returnInventory,
                new ProductExtractionSettings(true, interval, amount, false, java.util.Set.of()),
                actionSource, productExtractionRecovery::queue);
        return moved > 0 || recoveryProgress
                ? ProductExtractionTickState.PROGRESSED : ProductExtractionTickState.NO_PROGRESS;
    }

    private Map<AEKeyType, MEStorage> resolveExtractionSources(ServerLevel level) {
        Direction side = getSide();
        if (side == null) {
            return Map.of();
        }
        var targetPos = getBlockEntity().getBlockPos().relative(side);
        Direction targetSide = side.getOpposite();
        if (extractionTargetCache == null
                || extractionTargetCache.level() != level
                || !extractionTargetCache.pos().equals(targetPos)
                || extractionTargetCache.context() != targetSide) {
            extractionTargetCache = BlockCapabilityCache.create(
                    AECapabilities.ME_STORAGE, level, targetPos, targetSide);
        }
        MEStorage direct = extractionTargetCache.getCapability();
        Map<AEKeyType, MEStorage> result = new IdentityHashMap<>();
        if (direct != null) {
            for (var keyType : AEKeyTypes.getAll()) {
                result.put(keyType, direct);
            }
            return result;
        }
        for (var entry : getExternalStrategies().entrySet()) {
            MEStorage wrapper = entry.getValue().createWrapper(false, this::wake);
            if (wrapper != null) {
                result.put(entry.getKey(), wrapper);
            }
        }
        return result;
    }

    private boolean drainProductExtractionRecovery(PatternP2PUnitManagerPart manager) {
        return productExtractionRecovery.drain(manager.getLogic()::insertProductExtractionRecovery);
    }

    private void setRedstonePower(int power) {
        if (type != PatternP2PUnitPortType.REDSTONE) {
            redstonePower = 0;
            redstoneWorldStateDirty = false;
            return;
        }
        int clamped = Math.clamp(power, 0, 15);
        if (redstonePower != clamped || redstoneWorldStateDirty) {
            if (getLevel() == null) {
                return;
            }
            redstonePower = clamped;
            redstoneWorldStateDirty = false;
            Platform.notifyBlocksOfNeighbors(getLevel(), getBlockEntity().getBlockPos());
        }
    }

    public void invalidateTaskRuntimeState() {
        taskStartTick = Long.MIN_VALUE;
        redstoneWorldStateDirty = true;
        setRedstonePower(0);
        wake();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, false, 5);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        PatternP2PUnitManagerPart manager = getManager();
        if (manager == null) {
            taskStartTick = Long.MIN_VALUE;
            setRedstonePower(0);
            return TickRateModulation.SLEEP;
        }
        if (!manager.getLogic().isTaskOperational()) {
            taskStartTick = Long.MIN_VALUE;
            setRedstonePower(0);
            return TickRateModulation.IDLE;
        }
        if (taskStartTick == Long.MIN_VALUE) {
            taskStartTick = getLevel().getGameTime();
        }
        boolean changed = switch (type) {
            case BREAK -> runBreakStrategies(manager);
            case COLLECT -> collect(manager);
            case EXTRACT -> false;
            case ENERGY -> false;
            case REDSTONE -> {
                yield updateRedstone(manager);
            }
            default -> false;
        };
        if (type == PatternP2PUnitPortType.EXTRACT) {
            return TickRateModulation.SLEEP;
        }
        return changed ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    /**
     * Accepts energy supplied by a pattern P2P energy tunnel and forwards it to the
     * adjacent machine. It deliberately does not use the subnet's AE energy service.
     */
    public int receiveExternalEnergy(int maxReceive, boolean simulate) {
        if (!type.acceptsExternalEnergy() || maxReceive <= 0) {
            return 0;
        }
        PatternP2PUnitManagerPart manager = getManager();
        if (manager == null) {
            return 0;
        }

        Direction side = getSide();
        if (side == null || !(getLevel() instanceof ServerLevel level)) {
            return 0;
        }

        var targetPos = getBlockEntity().getBlockPos().relative(side);
        Direction targetSide = side.getOpposite();
        if (energyTargetCache == null
                || energyTargetCache.level() != level
                || !energyTargetCache.pos().equals(targetPos)
                || energyTargetCache.context() != targetSide) {
            energyTargetCache = BlockCapabilityCache.create(
                    Capabilities.EnergyStorage.BLOCK, level, targetPos, targetSide);
        }
        IEnergyStorage target = energyTargetCache.getCapability();
        if (target == null || !target.canReceive()) {
            return 0;
        }
        return target.receiveEnergy(maxReceive, simulate);
    }

    public boolean isReturnPort() {
        return type == PatternP2PUnitPortType.RETURN || type == PatternP2PUnitPortType.COLLECT;
    }

    private boolean canReturnProductsInternally() {
        return isReturnPort() || type == PatternP2PUnitPortType.EXTRACT;
    }

    public GenericInternalInventory getReturnInventory() {
        return returnInventory;
    }

    public MEStorage getReturnStorage() {
        return returnInventory;
    }

    public IItemHandler getReturnItemHandler() {
        return returnItemHandler;
    }

    public IFluidHandler getReturnFluidHandler() {
        return returnFluidHandler;
    }

    @Override
    public boolean canConnectRedstone() {
        return type == PatternP2PUnitPortType.REDSTONE;
    }

    @Override
    public int isProvidingStrongPower() {
        return type == PatternP2PUnitPortType.REDSTONE ? redstonePower : 0;
    }

    @Override
    public int isProvidingWeakPower() {
        return isProvidingStrongPower();
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        boundPatternP2PUnitId = data.hasUUID("PatternP2PUnitId") ? data.getUUID("PatternP2PUnitId") : null;
        boundFrequency = data.getShort(BOUND_FREQUENCY_TAG);
        cachedManager = null;
        redstoneWorldStateDirty = true;
        taskStartTick = Long.MIN_VALUE;
        productExtractionRecovery.read(data, PRODUCT_EXTRACTION_RECOVERY, registries);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        if (boundPatternP2PUnitId != null) {
            data.putUUID("PatternP2PUnitId", boundPatternP2PUnitId);
        } else {
            data.remove("PatternP2PUnitId");
        }
        data.putShort(BOUND_FREQUENCY_TAG, boundFrequency);
        productExtractionRecovery.write(data, PRODUCT_EXTRACTION_RECOVERY, registries);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        productExtractionRecovery.addDrops(drops, getLevel(), getBlockEntity().getBlockPos());
    }

    @Override
    public void clearContent() {
        super.clearContent();
        productExtractionRecovery.clear();
    }

    @Override
    public boolean useStandardMemoryCard() {
        return false;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        if (!(heldItem.getItem() instanceof IMemoryCard card) || hand == InteractionHand.OFF_HAND) {
            return super.onUseItemOn(heldItem, player, hand, pos);
        }
        if (isClientSide()) {
            return true;
        }
        UUID patternP2PUnitId = heldItem.get(ModContent.PATTERN_P2P_UNIT_ID.get());
        Short requestedFrequency = heldItem.get(AEComponents.EXPORTED_P2P_FREQUENCY);
        if (patternP2PUnitId == null || requestedFrequency == null) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }
        var grid = getMainNode().getGrid();
        var currentManager = getManager();
        var requestedManager = grid == null ? null
                : grid.getService(PatternP2PTopologyGridService.class).findManager(patternP2PUnitId);
        if ((currentManager != null && currentManager.isTaskActive())
                || (requestedManager != null && (requestedManager.isTaskActive()
                || requestedManager.getFrequency() != requestedFrequency))) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }
        boundPatternP2PUnitId = patternP2PUnitId;
        boundFrequency = requestedFrequency;
        cachedManager = null;
        if (grid != null) {
            grid.getService(PatternP2PEnergyGridService.class).topologyChanged();
            grid.getService(PatternP2PTopologyGridService.class).topologyChanged();
        }
        getHost().markForSave();
        getHost().markForUpdate();
        wake();
        card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        return true;
    }

    private void wake() {
        getMainNode().ifPresent((grid, node) -> {
            grid.getTickManager().alertDevice(node);
            if (type == PatternP2PUnitPortType.EXTRACT) {
                grid.getService(ProductExtractionGridService.class).wake(node, this);
            }
        });
    }

    public void alertTicking() {
        wake();
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        cachedManager = null;
        super.onMainNodeStateChanged(reason);
        wake();
    }

    @Override
    public void removeFromWorld() {
        cachedManager = null;
        super.removeFromWorld();
    }

    @Override
    public void onNeighborChanged(net.minecraft.world.level.BlockGetter level,
                                  net.minecraft.core.BlockPos pos, net.minecraft.core.BlockPos neighbor) {
        placementStrategy = null;
        breakStrategies = null;
        collectFluidStrategy = null;
        externalStrategies = null;
        energyTargetCache = null;
        extractionTargetCache = null;
        var grid = getMainNode().getGrid();
        if (grid != null && type.acceptsExternalEnergy()) {
            grid.getService(PatternP2PEnergyGridService.class).demandChanged();
        }
        wake();
    }

    @Override
    public void getBoxes(IPartCollisionHelper helper) {
        helper.addBox(3, 3, 13, 13, 13, 16);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 1;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.get(type).getModel(isPowered(), isActive());
    }

    @Override
    public ModelData getModelData() {
        long frequencyValue = Short.toUnsignedLong(boundFrequency);
        long patternP2PUnitValue = Short.toUnsignedLong(PatternP2PUnitIdentityColors.encode(boundPatternP2PUnitId));
        if (isActive() && isPowered()) {
            frequencyValue |= 0x10000L;
            patternP2PUnitValue |= 0x10000L;
        }
        return ModelData.builder()
                .with(P2PTunnelFrequencyModelData.FREQUENCY, frequencyValue)
                .with(PatternP2PUnitModelData.PATTERN_P2P_UNIT_ID, patternP2PUnitValue)
                .build();
    }

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(boundPatternP2PUnitId != null);
        if (boundPatternP2PUnitId != null) {
            data.writeUUID(boundPatternP2PUnitId);
        }
        data.writeShort(getBoundFrequency());
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        UUID previous = boundPatternP2PUnitId;
        short previousFrequency = boundFrequency;
        boundPatternP2PUnitId = data.readBoolean() ? data.readUUID() : null;
        cachedManager = null;
        boundFrequency = data.readShort();
        return changed || previousFrequency != boundFrequency
                || !java.util.Objects.equals(previous, boundPatternP2PUnitId);
    }

    private record PatternP2PUnitPortModels(IPartModel off, IPartModel on, IPartModel channel) {
        private PatternP2PUnitPortModels(ResourceLocation front) {
            this(new PartModel(P2PModels.MODEL_STATUS_OFF, P2PModels.MODEL_FREQUENCY, front, IDENTITY_MODEL),
                    new PartModel(P2PModels.MODEL_STATUS_ON, P2PModels.MODEL_FREQUENCY, front, IDENTITY_MODEL),
                    new PartModel(P2PModels.MODEL_STATUS_HAS_CHANNEL, P2PModels.MODEL_FREQUENCY, front,
                            IDENTITY_MODEL));
        }

        private IPartModel getModel(boolean hasPower, boolean hasChannel) {
            return hasPower && hasChannel ? channel : hasPower ? on : off;
        }

        private List<IPartModel> models() {
            return List.of(off, on, channel);
        }
    }

    private final class PortReturnInventory implements GenericInternalInventory, MEStorage {
        @Override public int size() { return 9; }
        @Override public @Nullable GenericStack getStack(int slot) { return null; }
        @Override public @Nullable AEKey getKey(int slot) { return null; }
        @Override public long getAmount(int slot) { return 0; }
        @Override public long getMaxAmount(AEKey key) { return getCapacity(key.getType()); }
        @Override public long getCapacity(appeng.api.stacks.AEKeyType keyType) {
            return GenericSlotCapacities.getMap().getOrDefault(keyType, Long.MAX_VALUE);
        }
        @Override public boolean canInsert() {
            PatternP2PUnitManagerPart manager = getManager();
            return canReturnProductsInternally() && manager != null && manager.getLogic().isTaskOperational();
        }
        @Override public boolean canExtract() { return false; }
        @Override public void setStack(int slot, @Nullable GenericStack newStack) { }
        @Override public boolean isSupportedType(appeng.api.stacks.AEKeyType type) { return true; }
        @Override public boolean isAllowedIn(int slot, AEKey what) {
            PatternP2PUnitManagerPart manager = getManager();
            return canReturnProductsInternally() && manager != null
                    && manager.getLogic().simulateReturned(what, 1) > 0;
        }
        @Override public long insert(int slot, AEKey what, long amount, Actionable mode) {
            PatternP2PUnitManagerPart manager = getManager();
            return canReturnProductsInternally() && manager != null
                    ? manager.getLogic().insertReturned(what, amount, mode) : 0;
        }
        @Override public long extract(int slot, AEKey what, long amount, Actionable mode) { return 0; }
        @Override public void beginBatch() { }
        @Override public void endBatch() { }
        @Override public void endBatchSuppressed() { }
        @Override public void onChange() { }
        @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return insert(0, what, amount, mode);
        }
        @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) { return 0; }
        @Override public void getAvailableStacks(appeng.api.stacks.KeyCounter out) { }
        @Override public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.translatable("item.ae2_batchcraft.pattern_p2p_unit_port_return");
        }
    }
}
