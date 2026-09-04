package cn.ae2bc.part;


import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.behaviors.PlacementStrategy;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
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
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import appeng.helpers.IPriorityHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PriorityMenu;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import appeng.core.definitions.AEItems;
import cn.ae2bc.menu.UnitPortOutputConfigMenu;
import cn.ae2bc.menu.UnitPortInputConfigMenu;
import appeng.menu.locator.MenuLocators;
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
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
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
import cn.ae2bc.platform.ItemData;
import net.minecraft.core.Direction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.energy.IEnergyStorage;
import cn.ae2bc.platform.ForgeBlockCapabilityCache;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.client.model.data.ModelData;
import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A unit endpoint. Task ports are gated by their manager; energy ports operate continuously. */
public final class PatternP2PUnitPortPart extends AEBasePart implements IGridTickable, ProductExtractionTask, IPriorityHost {
    public static final int MIN_TRANSFER_PRIORITY = -9999;
    public static final int MAX_TRANSFER_PRIORITY = 9999;
    private static final String PRODUCT_EXTRACTION_RECOVERY = "ProductExtractionRecovery";
    private static final String BOUND_FREQUENCY_TAG = "BoundFrequency";
    private static final ResourceLocation IDENTITY_MODEL = new ResourceLocation(
            Ae2bcMod.MOD_ID, "part/p2p/pattern_p2p_unit_port_identity");
    private static final Map<UnitPortType, PatternP2PUnitPortModels> MODELS = createModels();

    private final UnitPortType type;
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
    private @Nullable ForgeBlockCapabilityCache<IEnergyStorage> energyTargetCache;
    private @Nullable ForgeBlockCapabilityCache<MEStorage> extractionTargetCache;
    private int redstonePower;
    private boolean redstoneWorldStateDirty = true;
    private long taskStartTick = Long.MIN_VALUE;
    private int transferPriority;
    /** Whether this port may serve more than one encoded pattern slot. */
    private boolean singleSlot;
    private final GenericStackInv outputFilterMarkers;
    private final AppEngInternalInventory outputFilterInverter;
    private final GenericStackInv inputFilterMarkers;
    private final AppEngInternalInventory inputFilterInverter;

    public PatternP2PUnitPortPart(IPartItem<?> partItem, UnitPortType type) {
        super(partItem);
        this.type = type;
        InternalInventoryHost filterHost = new InternalInventoryHost() {
            @Override public void onChangeInventory(appeng.api.inventories.InternalInventory inv, int slot) {
                onFilterChanged();
            }
            @Override public void saveChanges() {
                onFilterChanged();
            }
            @Override public boolean isClientSide() { return PatternP2PUnitPortPart.this.isClientSide(); }
        };
        this.outputFilterMarkers = new MarkerInventory(this::onFilterChanged, 18);
        this.outputFilterInverter = new AppEngInternalInventory(filterHost, 1, 1,
                new IAEItemFilter() {
                    @Override public boolean allowInsert(appeng.api.inventories.InternalInventory inv,
                                                           int slot, ItemStack stack) {
                        return stack.getItem() == AEItems.INVERTER_CARD.asItem();
                    }
                });
        this.inputFilterMarkers = new MarkerInventory(this::onFilterChanged, 18);
        this.inputFilterInverter = new AppEngInternalInventory(filterHost, 1, 1,
                new IAEItemFilter() {
                    @Override public boolean allowInsert(appeng.api.inventories.InternalInventory inv,
                                                           int slot, ItemStack stack) {
                        return stack.getItem() == AEItems.INVERTER_CARD.asItem();
                    }
                });
        this.productExtractionRecovery = new ExtractionRecoveryQueue(() -> getHost().markForSave());
        getMainNode().addService(IGridTickable.class, this);
    }

    private void onFilterChanged() {
        getHost().markForSave();
        getHost().markForUpdate();
        PatternP2PUnitManagerPart manager = getManager();
        if (manager != null) manager.getLogic().alertPendingRetry();
        wake();
    }

    private static Map<UnitPortType, PatternP2PUnitPortModels> createModels() {
        Map<UnitPortType, PatternP2PUnitPortModels> result = new EnumMap<>(UnitPortType.class);
        for (UnitPortType type : UnitPortType.values()) {
            ResourceLocation front = new ResourceLocation(
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

    public UnitPortType getType() {
        return type;
    }

    public GenericStackInv getOutputFilterMarkers() { return outputFilterMarkers; }
    public AppEngInternalInventory getOutputFilterInverter() { return outputFilterInverter; }
    public GenericStackInv getInputFilterMarkers() { return inputFilterMarkers; }
    public AppEngInternalInventory getInputFilterInverter() { return inputFilterInverter; }

    private boolean allowsOutputFilter(AEKey what) {
        if (!type.acceptsTaskInput()) return true;
        return allowsFilter(outputFilterMarkers, outputFilterInverter, what);
    }

    private boolean allowsInputFilter(AEKey what) {
        if (!type.returnsTaskOutput()) return true;
        return allowsFilter(inputFilterMarkers, inputFilterInverter, what);
    }

    private static boolean allowsFilter(GenericStackInv markers,
                                        AppEngInternalInventory inverter, AEKey what) {
        boolean marked = false;
        for (int i = 0; i < markers.size(); i++) {
            AEKey marker = markers.getKey(i);
            if (what.equals(marker)) {
                marked = true;
                break;
            }
        }
        boolean inverted = !inverter.getStackInSlot(0).isEmpty();
        return markers.isEmpty() || (inverted ? !marked : marked);
    }

    public int getTransferPriority() {
        return transferPriority;
    }

    public boolean isSingleSlot() {
        return singleSlot;
    }

    public boolean isSingleSlotEditable() {
        PatternP2PUnitManagerPart manager = getManager();
        return manager == null || manager.getLogic().getEffectiveConfiguration().outputSlotSharingMode()
                == OutputSlotSharingMode.FOLLOW_PORT;
    }

    public boolean getEffectiveSingleSlot() {
        return singleSlot;
    }

    public void applyManagerSingleSlot(OutputSlotSharingMode mode) {
        if (mode == null || mode == OutputSlotSharingMode.FOLLOW_PORT) {
            return;
        }
        boolean value = mode == OutputSlotSharingMode.ALL;
        if (singleSlot == value) {
            return;
        }
        singleSlot = value;
        getHost().markForSave();
        getHost().markForUpdate();
        PatternP2PUnitManagerPart manager = getManager();
        if (manager != null) {
            manager.getLogic().alertPendingRetry();
        }
    }

    public void setSingleSlot(boolean value) {
        if (!isSingleSlotEditable()) {
            return;
        }
        if (singleSlot != value) {
            singleSlot = value;
            getHost().markForSave();
            getHost().markForUpdate();
            PatternP2PUnitManagerPart manager = getManager();
            if (manager != null) {
                manager.getLogic().alertPendingRetry();
            }
        }
    }

    /** Cheap, side-effect-free candidate filter used before priority ordering. */
    public boolean matchesInput(PatternP2PUnitManagerPart manager, GenericStack stack,
                                MaterialOutputForm form) {
        return isBoundTo(manager) && stack != null && stack.amount() > 0 && form != null
                && UnitPortType.forOutputFormId(form.getId()) == type
                && form.supports(stack.what()) && allowsOutputFilter(stack.what());
    }

    @Override public int getPriority() { return transferPriority; }
    @Override public void setPriority(int value) { setTransferPriority(value); }
    @Override public ItemStack getMainMenuIcon() { return getPartItem().asItem().getDefaultInstance(); }
    @Override public void returnToMainMenu(Player player, ISubMenu subMenu) { player.closeContainer(); }

    public void setTransferPriority(int value) {
        int clamped = Math.max(MIN_TRANSFER_PRIORITY, Math.min(MAX_TRANSFER_PRIORITY, value));
        if (transferPriority != clamped) {
            transferPriority = clamped;
            getHost().markForSave();
            getHost().markForUpdate();
            PatternP2PUnitManagerPart manager = getManager();
            if (manager != null) manager.getLogic().alertPendingRetry();
        }
    }

    public long estimateTransferCapacity(AEKey what, long fallback) {
        if (type != UnitPortType.TRANSFER || !(what instanceof AEItemKey key)
                || !(getLevel() instanceof ServerLevel level) || getSide() == null) {
            return fallback;
        }
        var adjacent = level.getBlockEntity(getBlockEntity().getBlockPos().relative(getSide()));
        if (adjacent == null) return fallback;
        LazyOptional<IItemHandler> optional = adjacent.getCapability(ForgeCapabilities.ITEM_HANDLER,
                getSide().getOpposite());
        if (!optional.isPresent()) return fallback;
        IItemHandler handler = optional.orElse(null);
        if (handler == null) return fallback;
        ItemStack probe = key.toStack(1);
        long total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack existing = handler.getStackInSlot(slot);
            int limit = Math.max(0, handler.getSlotLimit(slot));
            if (existing.isEmpty()) total += limit;
            else if (ItemStack.isSameItemSameTags(existing, probe)) {
                ItemStack remainder = handler.insertItem(slot, probe.copyWithCount(limit), true);
                total += Math.max(0, limit - remainder.getCount());
            }
        }
        return total > 0 ? total : fallback;
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
        if (!matchesInput(manager, stack, form)) {
            return 0;
        }
        // Admission probes happen before task activation; world mutation does not.
        if (mode == Actionable.MODULATE && !manager.getLogic().isTaskOperational()) {
            return 0;
        }
        return switch (type) {
            case DROP, PLACE -> getPlacementStrategy().placeInWorld(
                    stack.what(), stack.amount(), mode, type == UnitPortType.DROP);
            case TRANSFER -> insertIntoTarget(stack.what(), stack.amount(), mode);
            default -> 0;
        };
    }

    private long insertIntoTarget(AEKey what, long amount, Actionable mode) {
        var strategy = getExternalStrategies().get(what.getType());
        if (strategy == null) {
            return 0;
        }
        MEStorage storage = strategy.createWrapper(false, this::wakeForTargetChange);
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
                    getBlockEntity(), java.util.Collections.emptyMap(),
                    getMainNode().getNode().getOwningPlayerProfileId());
        }
        return breakStrategies == null ? List.of() : breakStrategies;
    }

    private @Nullable PickupStrategy getCollectFluidStrategy() {
        if (collectFluidStrategy == null && getLevel() instanceof ServerLevel level && getSide() != null) {
            collectFluidStrategy = new FluidPickupStrategy(level,
                    getBlockEntity().getBlockPos().relative(getSide()), getSide().getOpposite(),
                    getBlockEntity(), java.util.Collections.emptyMap(),
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
            AEItemKey entityKey = AEItemKey.of(entity.getItem());
            if (entityKey == null || !allowsInputFilter(entityKey)) {
                continue;
            }
            for (PickupStrategy strategy : strategies) {
                if (strategy.canPickUpEntity(entity)) {
                    changed |= strategy.pickUpEntity(grid.getEnergyService(),
                            (what, amount, mode) -> handleCollected(manager, what, amount, mode), entity);
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
        if (!allowsInputFilter(what)) {
            return 0;
        }
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
    public <T> LazyOptional<T> getCapability(Capability<T> capability) {
        if (isReturnPort()) {
            if (capability == appeng.capabilities.Capabilities.GENERIC_INTERNAL_INV
                    || capability == appeng.capabilities.Capabilities.STORAGE) {
                return LazyOptional.of(() -> returnInventory).cast();
            }
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return LazyOptional.of(() -> returnItemHandler).cast();
            }
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                return LazyOptional.of(() -> returnFluidHandler).cast();
            }
        }
        return super.getCapability(capability);
    }

    @Override
    public boolean hasProductExtractionWork() {
        if (type != UnitPortType.EXTRACT) {
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
        manager.getLogic().beginProductExtractionFilter();
        int moved;
        try {
            moved = ProductExtractor.extract(ExtractionSource.fromTypeMap(resolveExtractionSources(level)),
                    returnInventory,
                    new ProductExtractionSettings(true, interval, amount, false, java.util.Set.of()),
                    actionSource, productExtractionRecovery::queue);
        } finally {
            manager.getLogic().endProductExtractionFilter();
        }
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
            extractionTargetCache = ForgeBlockCapabilityCache.create(
                    appeng.capabilities.Capabilities.STORAGE, level, targetPos, targetSide);
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
        if (type != UnitPortType.REDSTONE) {
            redstonePower = 0;
            redstoneWorldStateDirty = false;
            return;
        }
        int clamped = cn.ae2bc.logic.Numbers.clamp(power, 0, 15);
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
        return new TickingRequest(1, 20, false, true, 5);
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
        if (type == UnitPortType.EXTRACT) {
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
            energyTargetCache = ForgeBlockCapabilityCache.create(
                    ForgeCapabilities.ENERGY, level, targetPos, targetSide);
        }
        IEnergyStorage target = energyTargetCache.getCapability();
        if (target == null || !target.canReceive()) {
            return 0;
        }
        return target.receiveEnergy(maxReceive, simulate);
    }

    public boolean isReturnPort() {
        return type == UnitPortType.RETURN || type == UnitPortType.COLLECT;
    }

    private boolean canReturnProductsInternally() {
        return isReturnPort() || type == UnitPortType.EXTRACT;
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
        return type == UnitPortType.REDSTONE;
    }

    @Override
    public int isProvidingStrongPower() {
        return type == UnitPortType.REDSTONE ? redstonePower : 0;
    }

    @Override
    public int isProvidingWeakPower() {
        return isProvidingStrongPower();
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data );
        boundPatternP2PUnitId = data.hasUUID("PatternP2PUnitId") ? data.getUUID("PatternP2PUnitId") : null;
        boundFrequency = data.getShort(BOUND_FREQUENCY_TAG);
        transferPriority = Math.max(MIN_TRANSFER_PRIORITY, Math.min(MAX_TRANSFER_PRIORITY, data.getInt("TransferPriority")));
        // Migrate the former multi-slot flag once; the new field stores the
        // positive single-slot restriction used by all current versions.
        singleSlot = data.contains("SingleSlot")
                ? data.getBoolean("SingleSlot")
                : data.contains("AllowMultiplePatternSlots")
                && !data.getBoolean("AllowMultiplePatternSlots");
        readFilterMarkers(outputFilterMarkers, data, "OutputFilterMarkers");
        outputFilterInverter.readFromNBT(data, "OutputFilterInverter");
        readFilterMarkers(inputFilterMarkers, data, "InputFilterMarkers");
        inputFilterInverter.readFromNBT(data, "InputFilterInverter");
        cachedManager = null;
        redstoneWorldStateDirty = true;
        taskStartTick = Long.MIN_VALUE;
        productExtractionRecovery.read(data, PRODUCT_EXTRACTION_RECOVERY );
    }

    @Override
    public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data );
        if (boundPatternP2PUnitId != null) {
            data.putUUID("PatternP2PUnitId", boundPatternP2PUnitId);
        } else {
            data.remove("PatternP2PUnitId");
        }
        data.putShort(BOUND_FREQUENCY_TAG, boundFrequency);
        data.putInt("TransferPriority", transferPriority);
        data.putBoolean("SingleSlot", singleSlot);
        outputFilterMarkers.writeToChildTag(data, "OutputFilterMarkers");
        outputFilterInverter.writeToNBT(data, "OutputFilterInverter");
        inputFilterMarkers.writeToChildTag(data, "InputFilterMarkers");
        inputFilterInverter.writeToNBT(data, "InputFilterInverter");
        productExtractionRecovery.write(data, PRODUCT_EXTRACTION_RECOVERY );
    }

    private static void readFilterMarkers(GenericStackInv markers, CompoundTag data, String name) {
        markers.beginBatch();
        try {
            if (hasLegacyItemMarkers(data, name)) {
                markers.clear();
                AppEngInternalInventory legacy = new AppEngInternalInventory(markers.size());
                legacy.readFromNBT(data, name);
                for (int slot = 0; slot < legacy.size(); slot++) {
                    AEItemKey key = AEItemKey.of(legacy.getStackInSlot(slot));
                    if (key != null) {
                        markers.setStack(slot, new GenericStack(key, 1));
                    }
                }
            } else {
                markers.readFromChildTag(data, name);
            }
        } finally {
            markers.endBatchSuppressed();
        }
    }

    private static boolean hasLegacyItemMarkers(CompoundTag data, String name) {
        if (!data.contains(name, Tag.TAG_LIST)) {
            return false;
        }
        for (Tag entry : data.getList(name, Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag marker && marker.contains("Slot", Tag.TAG_INT)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        ItemStack inputInverter = inputFilterInverter.getStackInSlot(0);
        if (!inputInverter.isEmpty()) {
            drops.add(inputInverter.copy());
        }
        productExtractionRecovery.addDrops(drops, getLevel(), getBlockEntity().getBlockPos());
    }

    @Override
    public void clearContent() {
        super.clearContent();
        inputFilterMarkers.clear();
        inputFilterInverter.clear();
        productExtractionRecovery.clear();
    }

    @Override
    public boolean useStandardMemoryCard() {
        return false;
    }

    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        return handlePartUse(player, hand, pos, false);
    }

    @Override
    public boolean onPartShiftActivate(Player player, InteractionHand hand, Vec3 pos) {
        return handlePartUse(player, hand, pos, true);
    }

    private boolean handlePartUse(Player player, InteractionHand hand, Vec3 pos, boolean alternateUse) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (!(heldItem.getItem() instanceof IMemoryCard card) || hand == InteractionHand.OFF_HAND) {
            if (hand == InteractionHand.MAIN_HAND && (type.acceptsTaskInput() || type.returnsTaskOutput())) {
                if (!isClientSide()) {
                    MenuOpener.open(type.acceptsTaskInput()
                                    ? UnitPortOutputConfigMenu.TYPE : UnitPortInputConfigMenu.TYPE,
                            player, MenuLocators.forPart(this));
                }
                return true;
            }
            return alternateUse
                    ? super.onPartShiftActivate(player, hand, pos)
                    : super.onPartActivate(player, hand, pos);
        }
        if (isClientSide()) {
            return true;
        }
        CompoundTag cardData = card.getData(heldItem);
        UUID patternP2PUnitId = ItemData.getUnitId(heldItem);
        if (patternP2PUnitId == null || !ItemData.hasP2PFrequency(cardData)) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }
        short requestedFrequency = ItemData.getP2PFrequency(cardData);
        var grid = getMainNode().getGrid();
        var currentManager = getManager();
        var requestedManager = grid == null ? null
                : grid.getService(PatternP2PTopologyGridService.class).findManager(patternP2PUnitId);
        if ((currentManager != null && currentManager.isTaskActive())
                || (requestedManager != null && requestedManager.isTaskActive())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.ae2_batchcraft.frequency_change_during_task"), true);
            return true;
        }
        if (requestedManager != null && requestedManager.getFrequency() != requestedFrequency) {
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
            if (type == UnitPortType.EXTRACT) {
                grid.getService(ProductExtractionGridService.class).wake(node, this);
            }
        });
    }

    private void wakeForTargetChange() {
        wake();
        PatternP2PUnitManagerPart manager = getManager();
        if (manager != null) {
            manager.getLogic().alertPendingRetry();
        }
    }

    public void alertTicking() {
        wakeForTargetChange();
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        cachedManager = null;
        super.onMainNodeStateChanged(reason);
        wakeForTargetChange();
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
        wakeForTargetChange();
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
    public void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(boundPatternP2PUnitId != null);
        if (boundPatternP2PUnitId != null) {
            data.writeUUID(boundPatternP2PUnitId);
        }
        data.writeShort(getBoundFrequency());
        data.writeInt(transferPriority);
    }

    @Override
    public boolean readFromStream(FriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        UUID previous = boundPatternP2PUnitId;
        short previousFrequency = boundFrequency;
        boundPatternP2PUnitId = data.readBoolean() ? data.readUUID() : null;
        cachedManager = null;
        boundFrequency = data.readShort();
        int previousPriority = transferPriority;
        transferPriority = Math.max(MIN_TRANSFER_PRIORITY, Math.min(MAX_TRANSFER_PRIORITY, data.readInt()));
        return changed || previousFrequency != boundFrequency
                || previousPriority != transferPriority
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

    private static final class MarkerInventory extends GenericStackInv {
        private MarkerInventory(Runnable listener, int size) {
            super(listener, Mode.CONFIG_TYPES, size);
        }

        @Override
        public void setStack(int slot, @Nullable GenericStack stack) {
            super.setStack(slot, stack == null ? null : new GenericStack(stack.what(), 0));
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
        @Override public boolean isAllowed(AEKey what) {
            PatternP2PUnitManagerPart manager = getManager();
            return canReturnProductsInternally() && allowsInputFilter(what) && manager != null
                    && manager.getLogic().simulateReturned(what, 1) > 0;
        }
        @Override public long insert(int slot, AEKey what, long amount, Actionable mode) {
            PatternP2PUnitManagerPart manager = getManager();
            return canReturnProductsInternally() && allowsInputFilter(what) && manager != null
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
