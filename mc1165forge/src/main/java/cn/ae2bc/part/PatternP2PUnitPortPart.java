package cn.ae2bc.part;

import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.logic.ItemStackExtractionRecoveryQueue;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.implementations.IPowerChannelState;
import appeng.helpers.IPriorityHost;
import net.minecraftforge.items.ItemStackHandler;
import appeng.container.ContainerLocator;
import appeng.container.ContainerOpener;
import appeng.container.implementations.PriorityContainer;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;
import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;
import cn.ae2bc.client.model.PatternP2PUnitModelData;
import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.logic.PatternP2PUnitIdentityColors;
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import cn.ae2bc.core.energy.EnergyEndpoint;
import cn.ae2bc.core.schedule.ExtractionDeadlineGate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.ForgeHooks;
import net.minecraft.world.GameType;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.TranslationTextComponent;
import cn.ae2bc.menu.UnitPortInputConfigMenu;
import cn.ae2bc.menu.UnitPortOutputConfigMenu;

/** Unchanneled unit endpoint. Concrete machine behavior is layered onto this binding base. */
public final class PatternP2PUnitPortPart extends AEBasePart
        implements EnergyEndpoint, IGridTickable, IPowerChannelState, IPriorityHost {
    public static final int MIN_TRANSFER_PRIORITY = -9999;
    public static final int MAX_TRANSFER_PRIORITY = 9999;
    private static final ResourceLocation STATUS_OFF_MODEL_ID = new ResourceLocation(
            "appliedenergistics2", "part/p2p/p2p_tunnel_status_off");
    private static final ResourceLocation STATUS_ON_MODEL_ID = new ResourceLocation(
            "appliedenergistics2", "part/p2p/p2p_tunnel_status_on");
    private static final ResourceLocation STATUS_HAS_CHANNEL_MODEL_ID = new ResourceLocation(
            "appliedenergistics2", "part/p2p/p2p_tunnel_status_has_channel");
    private static final ResourceLocation FREQUENCY_MODEL_ID = new ResourceLocation(
            "appliedenergistics2", "part/p2p/p2p_tunnel_frequency");
    private static final ResourceLocation IDENTITY_MODEL_ID = new ResourceLocation(
            "ae2_batchcraft", "part/p2p/pattern_p2p_unit_port_identity");

    private final UnitPortType type;
    private final IPartModel modelOff;
    private final IPartModel modelOn;
    private final IPartModel modelActive;
    private UUID boundManagerId;
    private short boundFrequency;
    private boolean modelPowered;
    private boolean modelOnline;
    private PatternP2PUnitManagerPart cachedManager;
    private final IItemHandler returnHandler = new ReturnHandler();
    private final LazyOptional<IItemHandler> returnCapability = LazyOptional.of(() -> returnHandler);
    private final ExtractionDeadlineGate extractionDeadline = new ExtractionDeadlineGate();
    private final ItemStackExtractionRecoveryQueue extractionRecovery;
    private int redstonePower;
    private boolean redstoneWorldStateDirty = true;
    private long taskStartTick = Long.MIN_VALUE;
    private int transferPriority;
    private boolean singleSlot;
    private long observedTaskRevision = Long.MIN_VALUE;
    private final ItemStackHandler outputFilterMarkers;
    private final ItemStackHandler outputFilterInverter;
    private final ItemStackHandler inputFilterMarkers;
    private final ItemStackHandler inputFilterInverter;

    public PatternP2PUnitPortPart(ItemStack stack, UnitPortType type) {
        super(stack);
        this.type = type;
        this.outputFilterMarkers = new ItemStackHandler(18) {
            @Override protected void onContentsChanged(int slot) {
                getHost().markForSave();
                getHost().markForUpdate();
                alertManagerPendingRetry();
            }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
        };
        this.outputFilterInverter = new ItemStackHandler(1) {
            @Override protected void onContentsChanged(int slot) {
                getHost().markForSave();
                getHost().markForUpdate();
                alertManagerPendingRetry();
            }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return stack.getItem().getRegistryName() != null
                        && "appliedenergistics2".equals(stack.getItem().getRegistryName().getNamespace())
                        && "inverter_card".equals(stack.getItem().getRegistryName().getPath());
            }
        };
        this.inputFilterMarkers = new ItemStackHandler(18) {
            @Override protected void onContentsChanged(int slot) {
                getHost().markForSave();
                getHost().markForUpdate();
                alertManagerPendingRetry();
            }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
        };
        this.inputFilterInverter = new ItemStackHandler(1) {
            @Override protected void onContentsChanged(int slot) {
                getHost().markForSave();
                getHost().markForUpdate();
                alertManagerPendingRetry();
            }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return stack.getItem().getRegistryName() != null
                        && "appliedenergistics2".equals(stack.getItem().getRegistryName().getNamespace())
                        && "inverter_card".equals(stack.getItem().getRegistryName().getPath());
            }
        };
        this.extractionRecovery = new ItemStackExtractionRecoveryQueue(() -> getHost().markForSave());
        ResourceLocation front = modelId(type);
        this.modelOff = createModel(STATUS_OFF_MODEL_ID, front);
        this.modelOn = createModel(STATUS_ON_MODEL_ID, front);
        this.modelActive = createModel(STATUS_HAS_CHANNEL_MODEL_ID, front);
    }

    /** The port binds to a unit manager and must bypass AE2's machine-type card check. */
    @Override public boolean useStandardMemoryCard() { return false; }

    public static ResourceLocation modelId(UnitPortType type) {
        return new ResourceLocation("ae2_batchcraft", "part/p2p/pattern_p2p_unit_port_" + type.getId());
    }

    private static IPartModel createModel(ResourceLocation status, ResourceLocation front) {
        return new PartModel(true, status, FREQUENCY_MODEL_ID, front, IDENTITY_MODEL_ID);
    }

    @PartModels
    public static List<IPartModel> getModels() {
        IPartModel[] models = new IPartModel[UnitPortType.values().length * 3];
        int index = 0;
        for (UnitPortType type : UnitPortType.values()) {
            ResourceLocation front = modelId(type);
            models[index++] = createModel(STATUS_OFF_MODEL_ID, front);
            models[index++] = createModel(STATUS_ON_MODEL_ID, front);
            models[index++] = createModel(STATUS_HAS_CHANNEL_MODEL_ID, front);
        }
        return Arrays.asList(models);
    }

    @Override
    public net.minecraftforge.client.model.data.IModelData getModelData() {
        long frequency = Short.toUnsignedLong(boundFrequency);
        long unitId = Short.toUnsignedLong(PatternP2PUnitIdentityColors.encode(boundManagerId));
        if (modelPowered && modelOnline) {
            frequency |= 0x10000L;
            unitId |= 0x10000L;
        }
        return new net.minecraftforge.client.model.data.ModelDataMap.Builder()
                .withInitial(P2PTunnelFrequencyModelData.FREQUENCY, Long.valueOf(frequency))
                .withInitial(PatternP2PUnitModelData.PATTERN_P2P_UNIT_ID, Long.valueOf(unitId))
                .build();
    }

    public UnitPortType getPortType() { return type; }
    public int getTransferPriority() { return transferPriority; }
    public boolean isSingleSlot() { return singleSlot; }
    public boolean isSingleSlotEditable() {
        PatternP2PUnitManagerPart manager = findManagerForConfiguration();
        return manager == null || manager.getOutputSlotSharingMode() == OutputSlotSharingMode.FOLLOW_PORT;
    }
    public boolean getEffectiveSingleSlot() {
        return singleSlot;
    }

    public void applyManagerSingleSlot(OutputSlotSharingMode mode) {
        if (mode == null || mode == OutputSlotSharingMode.FOLLOW_PORT) return;
        boolean value = mode == OutputSlotSharingMode.ALL;
        if (singleSlot == value) return;
        singleSlot = value;
        getHost().markForSave();
        getHost().markForUpdate();
        alertManagerPendingRetry();
    }
    public void setSingleSlot(boolean value) {
        if (!isSingleSlotEditable()) return;
        if (singleSlot == value) return;
        singleSlot = value;
        getHost().markForSave(); getHost().markForUpdate();
        alertManagerPendingRetry();
    }
    public ItemStackHandler getOutputFilterMarkers() { return outputFilterMarkers; }
    public ItemStackHandler getOutputFilterInverter() { return outputFilterInverter; }
    public ItemStackHandler getInputFilterMarkers() { return inputFilterMarkers; }
    public ItemStackHandler getInputFilterInverter() { return inputFilterInverter; }

    private boolean allowsOutputFilter(ItemStack stack) {
        if (!type.acceptsTaskInput()) return true;
        boolean hasMarkers = false;
        boolean marked = false;
        for (int i = 0; i < outputFilterMarkers.getSlots(); i++) {
            ItemStack marker = outputFilterMarkers.getStackInSlot(i);
            if (!marker.isEmpty()) {
                hasMarkers = true;
                if (ItemStack.isSame(marker, stack) && ItemStack.tagMatches(marker, stack)) marked = true;
            }
        }
        boolean inverted = !outputFilterInverter.getStackInSlot(0).isEmpty();
        return !hasMarkers || (inverted ? !marked : marked);
    }

    public boolean allowsInputFilter(ItemStack stack) {
        if (!type.returnsTaskOutput()) return true;
        boolean hasMarkers = false;
        boolean marked = false;
        for (int i = 0; i < inputFilterMarkers.getSlots(); i++) {
            ItemStack marker = inputFilterMarkers.getStackInSlot(i);
            if (!marker.isEmpty()) {
                hasMarkers = true;
                if (ItemStack.isSame(marker, stack) && ItemStack.tagMatches(marker, stack)) marked = true;
            }
        }
        boolean inverted = !inputFilterInverter.getStackInSlot(0).isEmpty();
        return !hasMarkers || (inverted ? !marked : marked);
    }
    @Override public int getPriority() { return transferPriority; }
    @Override public void setPriority(int value) { setTransferPriority(value); }
    @Override public ItemStack getItemStackRepresentation() { return getItemStack(); }
    @Override public net.minecraft.inventory.container.ContainerType<?> getContainerType() { return PriorityContainer.TYPE; }
    public void setTransferPriority(int value) {
        int clamped = Math.max(MIN_TRANSFER_PRIORITY, Math.min(MAX_TRANSFER_PRIORITY, value));
        if (transferPriority == clamped) return;
        transferPriority = clamped;
        getHost().markForSave();
        getHost().markForUpdate();
        alertManagerPendingRetry();
    }
    public UUID getBoundManagerId() { return boundManagerId; }
    public short getBoundFrequency() { return (short) getBoundFrequencyUnsigned(); }
    public boolean isBoundUnitTaskActive() {
        PatternP2PUnitManagerPart manager = findManager();
        return manager != null && manager.isTaskActive();
    }
    @Override public boolean isPowered() {
        return isRemote() ? modelPowered : getProxy().isPowered();
    }
    @Override public boolean isActive() {
        return isRemote() ? modelOnline : getGridNode() != null && getGridNode().isActive();
    }
    public int getBoundFrequencyUnsigned() {
        PatternP2PUnitManagerPart manager = findManager();
        return manager == null ? Short.toUnsignedInt(boundFrequency) : manager.getFrequencyUnsigned();
    }
    public PatternP2PUnitManagerPart findManager() {
        if (boundManagerId == null || getTile().getLevel() == null) {
            cachedManager = null;
            return null;
        }
        if (cachedManager != null && isBoundTo(cachedManager)
                && cachedManager.getGridNode() != null
                && cachedManager.getGridNode().getGrid() == getGridNode().getGrid()) return cachedManager;
        cachedManager = PatternP2PTopologyGridService.find(getGridNode(), boundManagerId,
                getTile().getLevel().getGameTime());
        if (cachedManager != null && !isBoundTo(cachedManager)) cachedManager = null;
        return cachedManager;
    }

    private PatternP2PUnitManagerPart findManagerForConfiguration() {
        if (boundManagerId == null || getTile().getLevel() == null) return null;
        PatternP2PUnitManagerPart manager = PatternP2PTopologyGridService.findForConfiguration(
                getGridNode(), boundManagerId);
        return manager != null && isBoundTo(manager) ? manager : null;
    }

    public boolean isOperational() {
        PatternP2PUnitManagerPart manager = findManager();
        return manager != null && manager.isOperational();
    }

    /** Inserts one item batch into the machine next to a TRANSFER port. */
    public int insertTaskInput(PatternP2PUnitManagerPart manager, ItemStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty()
                || manager == null || !isBoundTo(manager) || !manager.isOperational()) return 0;
        if (!allowsOutputFilter(stack)) return 0;
        if (type == UnitPortType.TRANSFER) {
            ItemStack remainder = ItemHandlerHelper.insertItem(adjacentItemHandler(), stack.copy(), simulate);
            return stack.getCount() - remainder.getCount();
        }
        if (type == UnitPortType.DROP && getTile().getLevel() != null && getSide() != null) {
            if (!simulate) {
                Direction face = getSide().getFacing();
                net.minecraft.util.math.BlockPos target = getTile().getBlockPos().relative(face);
                ItemEntity entity = new ItemEntity(getTile().getLevel(), target.getX() + 0.5,
                        target.getY() + 0.5, target.getZ() + 0.5, stack.copy());
                entity.setDeltaMovement(face.getStepX() * 0.1, face.getStepY() * 0.1, face.getStepZ() * 0.1);
                if (!getTile().getLevel().addFreshEntity(entity)) return 0;
            }
            return stack.getCount();
        }
        if (type == UnitPortType.PLACE && stack.getCount() >= 1 && stack.getItem() instanceof BlockItem
                && getTile().getLevel() instanceof ServerWorld && getSide() != null) {
            Direction face = getSide().getFacing();
            BlockPos target = getTile().getBlockPos().relative(face);
            if (!getTile().getLevel().getBlockState(target).getMaterial().isReplaceable()) return 0;
            if (simulate) return 1;
            ServerWorld level = (ServerWorld) getTile().getLevel();
            FakePlayer player = placementPlayer(level);
            ItemStack previous = player.getItemInHand(Hand.MAIN_HAND);
            ItemStack placing = stack.copy();
            placing.setCount(1);
            player.setItemInHand(Hand.MAIN_HAND, placing);
            try {
                BlockRayTraceResult hit = new BlockRayTraceResult(Vector3d.atCenterOf(target),
                        face.getOpposite(), target, false);
                ActionResultType result = placing.useOn(new net.minecraft.item.BlockItemUseContext(
                        player, Hand.MAIN_HAND, placing, hit));
            return result.consumesAction() && placing.isEmpty() ? 1 : 0;
            } finally {
                player.setItemInHand(Hand.MAIN_HAND, previous);
            }
        }
        return 0;
    }

    /** Estimates aggregate item-handler capacity without changing the adjacent inventory. */
    public int estimateTransferCapacity(ItemStack stack, int fallback) {
        if (type == UnitPortType.DROP) return Integer.MAX_VALUE;
        if (type != UnitPortType.TRANSFER || stack == null || stack.isEmpty()) return fallback;
        IItemHandler handler = adjacentItemHandler();
        long total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            int limit = Math.max(0, handler.getSlotLimit(slot));
            if (limit <= 0) continue;
            ItemStack probe = stack.copy();
            probe.setCount(limit);
            ItemStack remainder = handler.insertItem(slot, probe, true);
            total += Math.max(0, limit - remainder.getCount());
            if (total >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return total > 0 ? (int) total : fallback;
    }

    public boolean matchesInput(PatternP2PUnitManagerPart manager, ItemStack stack,
            cn.ae2bc.pattern.MaterialOutputForm form) {
        return isBoundTo(manager) && stack != null && !stack.isEmpty() && form != null
                && UnitPortType.forOutputFormId(form.getId()) == type
                && form.supports(stack)
                && allowsOutputFilter(stack);
    }

    /** Forwards Forge Energy supplied by an energy tunnel to the adjacent machine. */
    @Override public boolean isEnergyEndpointAvailable() {
        return type == UnitPortType.ENERGY && isOperational();
    }

    @Override public int receiveExternalEnergy(int maxReceive, boolean simulate) {
        if (type != UnitPortType.ENERGY || maxReceive <= 0 || !isOperational()) return 0;
        IEnergyStorage target = adjacentEnergyStorage();
        return target == null || !target.canReceive() ? 0 : target.receiveEnergy(maxReceive, simulate);
    }

    public boolean isBoundTo(PatternP2PUnitManagerPart manager) {
        return manager != null && boundManagerId != null
                && boundManagerId.equals(manager.getUnitId())
                && (boundFrequency == 0 || boundFrequency == manager.getFrequency());
    }

    private FakePlayer placementPlayer(ServerWorld level) {
        if (getGridNode() != null) {
            PlayerEntity owner = appeng.core.Api.instance().registries().players()
                    .findPlayer(getGridNode().getPlayerID());
            if (owner != null) return FakePlayerFactory.get(level, owner.getGameProfile());
        }
        return FakePlayerFactory.getMinecraft(level);
    }

    private IItemHandler adjacentItemHandler() {
        if (getTile().getLevel() == null || getSide() == null) return EmptyItemHandler.INSTANCE;
        Direction face = getSide().getFacing();
        TileEntity tile = getTile().getLevel().getBlockEntity(getTile().getBlockPos().relative(face));
        return tile == null ? EmptyItemHandler.INSTANCE : tile.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite()).orElse(EmptyItemHandler.INSTANCE);
    }

    private IEnergyStorage adjacentEnergyStorage() {
        if (getTile().getLevel() == null || getSide() == null) return null;
        Direction face = getSide().getFacing();
        TileEntity tile = getTile().getLevel().getBlockEntity(getTile().getBlockPos().relative(face));
        return tile == null ? null : tile.getCapability(CapabilityEnergy.ENERGY, face.getOpposite()).orElse(null);
    }

    private int extractFromAdjacent(PatternP2PUnitManagerPart manager, int amount) {
        IItemHandler source = adjacentItemHandler();
        int moved = 0;
        int transferredSlots = 0;
        for (int slot = 0; slot < source.getSlots() && moved < amount && manager.isTaskActive()
                && transferredSlots < ProductExtractionLimits.MAX_TRANSFER_ENTRIES_PER_RUN; slot++) {
            ItemStack candidate = source.extractItem(slot, amount - moved, true);
            if (candidate.isEmpty() || !allowsInputFilter(candidate)) continue;
            ItemStack simulatedRemainder = manager.returnProduct(candidate, true);
            int accepted = candidate.getCount() - simulatedRemainder.getCount();
            if (accepted <= 0) continue;
            ItemStack extracted = source.extractItem(slot, accepted, false);
            ItemStack unexpected = manager.returnProduct(extracted, false);
            int transferred = extracted.getCount() - unexpected.getCount();
            moved = (int) Math.min((long) amount, (long) moved + transferred);
            if (transferred > 0) transferredSlots++;
            if (!unexpected.isEmpty()) {
                extractionRecovery.queue(ItemHandlerHelper.insertItem(source, unexpected, false));
            }
        }
        return moved;
    }

    private boolean drainExtractionRecovery(PatternP2PUnitManagerPart manager) {
        return extractionRecovery.drain(manager::returnProductRecovery);
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) {
        boolean sleeping = type != UnitPortType.EXTRACT && type != UnitPortType.REDSTONE
                && type != UnitPortType.COLLECT && type != UnitPortType.BREAK;
        return new TickingRequest(1, 20, sleeping, true);
    }

    public void alertTicking() {
        if (type == UnitPortType.EXTRACT) {
            extractionDeadline.wake();
        }
        try {
            getProxy().getTick().alertDevice(getGridNode());
        } catch (appeng.me.GridAccessException ignored) {
        }
        if (type == UnitPortType.RETURN) notifyNeighbors();
    }

    private void alertManagerPendingRetry() {
        if (getTile() == null || getTile().getLevel() == null
                || getTile().getLevel().isClientSide) return;
        PatternP2PUnitManagerPart manager = findManager();
        if (manager != null) manager.alertPendingRetry();
    }

    public void invalidateTaskRuntimeState() {
        taskStartTick = Long.MIN_VALUE;
        observedTaskRevision = Long.MIN_VALUE;
        redstoneWorldStateDirty = true;
        setRedstonePower(0);
        alertTicking();
    }

    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        PatternP2PUnitManagerPart manager = findManager();
        if (manager == null || !manager.isOperational() || getTile().getLevel() == null) {
            taskStartTick = Long.MIN_VALUE;
            setRedstonePower(0);
            return TickRateModulation.SLEEP;
        }
        if (type == UnitPortType.EXTRACT) {
            boolean recoveryProgress = drainExtractionRecovery(manager);
            if (!manager.isTaskActive()) {
                return recoveryProgress ? TickRateModulation.URGENT
                        : extractionRecovery.isEmpty() ? TickRateModulation.SLEEP : TickRateModulation.SLOWER;
            }
            if (!extractionDeadline.isDue(getTile().getLevel().getGameTime(), manager.getExtractionInterval())) {
                return recoveryProgress ? TickRateModulation.URGENT : TickRateModulation.SAME;
            }
            return extractFromAdjacent(manager, manager.getExtractionAmount()) > 0 || recoveryProgress
                    ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
        if (!manager.isTaskActive()) {
            taskStartTick = Long.MIN_VALUE;
            setRedstonePower(0);
            return TickRateModulation.SLEEP;
        }
        if (type == UnitPortType.REDSTONE) return tickRedstone(manager);
        if (type == UnitPortType.COLLECT) return collect(manager)
                ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        if (type == UnitPortType.BREAK) return breakAdjacent(manager)
                ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        return TickRateModulation.SLEEP;
    }

    private TickRateModulation tickRedstone(PatternP2PUnitManagerPart manager) {
        long now = getTile().getLevel().getGameTime();
        if (taskStartTick == Long.MIN_VALUE || observedTaskRevision != manager.getTaskRevision()) {
            taskStartTick = now;
            observedTaskRevision = manager.getTaskRevision();
        }
        long activeTicks = Math.max(0, now - taskStartTick);
        int next;
        switch (manager.getRedstoneMode()) {
            case CONTINUOUS: next = manager.getRedstoneStrength(); break;
            case PERIODIC_PULSE:
                next = activeTicks % manager.getPulsePeriodTicks() < manager.getPulseWidthTicks()
                        ? manager.getRedstoneStrength() : 0;
                break;
            case SINGLE_TRIGGER:
            default:
                next = activeTicks < manager.getPulseWidthTicks() ? manager.getRedstoneStrength() : 0;
                break;
        }
        setRedstonePower(next);
        if (manager.getRedstoneMode() == cn.ae2bc.logic.RedstoneOutputMode.CONTINUOUS
                || manager.getRedstoneMode() == cn.ae2bc.logic.RedstoneOutputMode.SINGLE_TRIGGER
                && activeTicks >= manager.getPulseWidthTicks()) return TickRateModulation.SLEEP;
        return TickRateModulation.URGENT;
    }

    private void setRedstonePower(int power) {
        if (type != UnitPortType.REDSTONE) {
            redstonePower = 0;
            redstoneWorldStateDirty = false;
            return;
        }
        int next = Math.max(0, Math.min(15, power));
        if (redstonePower == next && !redstoneWorldStateDirty) return;
        if (getTile().getLevel() == null) return;
        redstonePower = next;
        redstoneWorldStateDirty = false;
        notifyNeighbors();
    }

    private void notifyNeighbors() {
        if (getTile().getLevel() == null) return;
        getTile().getLevel().updateNeighborsAt(getTile().getBlockPos(),
                getTile().getLevel().getBlockState(getTile().getBlockPos()).getBlock());
    }

    private boolean collectDroppedItems(PatternP2PUnitManagerPart manager) {
        if (!(getTile().getLevel() instanceof ServerWorld) || getSide() == null) return false;
        ServerWorld level = (ServerWorld) getTile().getLevel();
        BlockPos target = getTile().getBlockPos().relative(getSide().getFacing());
        boolean moved = false;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AxisAlignedBB(target))) {
            ItemStack offered = entity.getItem().copy();
            if (offered.isEmpty() || !allowsInputFilter(offered)) continue;
            ItemStack simulated = manager.returnProduct(offered, true);
            int accepted = offered.getCount() - simulated.getCount();
            if (accepted <= 0) continue;
            ItemStack portion = offered.copy();
            portion.setCount(accepted);
            ItemStack remainder = manager.returnProduct(portion, false);
            int inserted = accepted - remainder.getCount();
            if (inserted <= 0) continue;
            ItemStack left = entity.getItem().copy();
            left.shrink(inserted);
            if (left.isEmpty()) entity.remove(); else entity.setItem(left);
            moved = true;
        }
        return moved;
    }

    private boolean collect(PatternP2PUnitManagerPart manager) {
        return collectDroppedItems(manager);
    }

    private boolean breakAdjacent(PatternP2PUnitManagerPart manager) {
        if (!(getTile().getLevel() instanceof ServerWorld) || getSide() == null) return false;
        ServerWorld level = (ServerWorld) getTile().getLevel();
        BlockPos target = getTile().getBlockPos().relative(getSide().getFacing());
        BlockState state = level.getBlockState(target);
        if (state.isAir() || state.getDestroySpeed(level, target) < 0) return false;
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target),
                player, ItemStack.EMPTY);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && !allowsInputFilter(drop)) return false;
        }
        if (manager.isBreakRecovery()) {
            for (ItemStack drop : drops) {
                if (manager.returnProduct(drop, true).getCount() != 0) return false;
            }
        }
        if (ForgeHooks.onBlockBreakEvent(level, GameType.SURVIVAL, player, target) < 0) return false;
        if (!level.setBlock(target, Blocks.AIR.defaultBlockState(), 3)) return false;
        for (ItemStack drop : drops) {
            ItemStack remainder = manager.isBreakRecovery() ? manager.returnProduct(drop, false) : drop;
            if (!remainder.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, target.getX() + 0.5,
                        target.getY() + 0.5, target.getZ() + 0.5, remainder));
            }
        }
        return true;
    }

    @Override public boolean canConnectRedstone() { return type == UnitPortType.REDSTONE; }
    @Override public int isProvidingStrongPower() { return type == UnitPortType.REDSTONE ? redstonePower : 0; }
    @Override public int isProvidingWeakPower() { return isProvidingStrongPower(); }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && type == UnitPortType.RETURN) {
            return returnCapability.cast();
        }
        return super.getCapability(capability);
    }

    @Override public boolean onPartActivate(PlayerEntity player, Hand hand, Vector3d hitPos) {
        if (bindFromMemoryCard(player, hand)) return true;
        if (hand == Hand.MAIN_HAND && (type.acceptsTaskInput() || type.returnsTaskOutput())) {
            if (!player.level.isClientSide) {
                if (type.acceptsTaskInput()) {
                    NetworkHooks.openGui((ServerPlayerEntity) player, new SimpleNamedContainerProvider(
                            (id, inventory, ignored) -> new UnitPortOutputConfigMenu(id, inventory, this),
                            new TranslationTextComponent("gui.ae2_batchcraft.unit_port_output_config.title")),
                            buffer -> {
                                buffer.writeBlockPos(getTile().getBlockPos());
                                buffer.writeByte(getSide().getFacing().ordinal());
                                buffer.writeBoolean(getEffectiveSingleSlot());
                                buffer.writeBoolean(isSingleSlotEditable());
                            });
                } else {
                    NetworkHooks.openGui((ServerPlayerEntity) player, new SimpleNamedContainerProvider(
                            (id, inventory, ignored) -> new UnitPortInputConfigMenu(id, inventory, this),
                            new TranslationTextComponent("gui.ae2_batchcraft.unit_port_input_config.title")),
                            buffer -> {
                                buffer.writeBlockPos(getTile().getBlockPos());
                                buffer.writeByte(getSide().getFacing().ordinal());
                            });
                }
            }
            return true;
        }
        return super.onPartActivate(player, hand, hitPos);
    }

    @Override public boolean onPartShiftActivate(PlayerEntity player, Hand hand, Vector3d hitPos) {
        return bindFromMemoryCard(player, hand) || super.onPartShiftActivate(player, hand, hitPos);
    }

    private boolean bindFromMemoryCard(PlayerEntity player, Hand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (hand != Hand.MAIN_HAND || !(held.getItem() instanceof IMemoryCard)) return false;
        if (player.level.isClientSide) return true;
        IMemoryCard card = (IMemoryCard) held.getItem();
        CompoundNBT data = card.getData(held);
        if (!data.hasUUID("PatternP2PUnitId") || !data.contains("freq")) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }
        UUID requestedId = data.getUUID("PatternP2PUnitId");
        short requestedFrequency = data.getShort("freq");
        PatternP2PUnitManagerPart current = findManager();
        PatternP2PUnitManagerPart requested = PatternP2PTopologyGridService.find(getGridNode(), requestedId,
                getTile().getLevel().getGameTime());
        if ((current != null && current.isTaskActive())
                || (requested != null && requested.isTaskActive())) {
            player.displayClientMessage(new TranslationTextComponent(
                    "message.ae2_batchcraft.frequency_change_during_task"), true);
            return true;
        }
        if (requested != null && requested.getFrequency() != requestedFrequency) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }
        boundManagerId = requestedId;
        boundFrequency = requestedFrequency;
        cachedManager = null;
        PatternP2PTopologyGridService.invalidate(getGridNode());
        getHost().markForSave();
        getHost().markForUpdate();
        alertManagerPendingRetry();
        card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        return true;
    }

    @Override
    public void onNeighborChanged(net.minecraft.world.IBlockReader level,
            BlockPos pos, BlockPos neighbor) {
        super.onNeighborChanged(level, pos, neighbor);
        alertTicking();
        alertManagerPendingRetry();
    }

    @Override
    public void gridChanged() {
        super.gridChanged();
        cachedManager = null;
        PatternP2PTopologyGridService.invalidate(getGridNode());
        redstoneWorldStateDirty = true;
        alertTicking();
        alertManagerPendingRetry();
        refreshModelState();
    }

    @MENetworkEventSubscribe
    public void onPowerStatusChanged(MENetworkPowerStatusChange event) {
        cachedManager = null;
        PatternP2PTopologyGridService.invalidate(getGridNode());
        redstoneWorldStateDirty = true;
        alertTicking();
        alertManagerPendingRetry();
        refreshModelState();
    }

    private void refreshModelState() {
        if (isRemote()) return;
        boolean powered = getProxy().isPowered();
        boolean online = getGridNode() != null && getGridNode().isActive();
        if (modelPowered == powered && modelOnline == online) return;
        modelPowered = powered;
        modelOnline = online;
        getHost().markForUpdate();
    }

    @Override public void readFromNBT(CompoundNBT data) {
        super.readFromNBT(data);
        boundManagerId = data.hasUUID("PatternP2PUnitId") ? data.getUUID("PatternP2PUnitId") : null;
        boundFrequency = data.getShort("PatternP2PUnitFrequency");
        transferPriority = Math.max(-9999, Math.min(9999, data.getInt("TransferPriority")));
        singleSlot = data.contains("SingleSlot")
                ? data.getBoolean("SingleSlot")
                : data.contains("AllowMultiplePatternSlots") && !data.getBoolean("AllowMultiplePatternSlots");
        if (data.contains("OutputFilterMarkers")) outputFilterMarkers.deserializeNBT(data.getCompound("OutputFilterMarkers"));
        if (data.contains("OutputFilterInverter")) outputFilterInverter.deserializeNBT(data.getCompound("OutputFilterInverter"));
        if (data.contains("InputFilterMarkers")) inputFilterMarkers.deserializeNBT(data.getCompound("InputFilterMarkers"));
        if (data.contains("InputFilterInverter")) inputFilterInverter.deserializeNBT(data.getCompound("InputFilterInverter"));
        cachedManager = null;
        redstoneWorldStateDirty = true;
        taskStartTick = Long.MIN_VALUE;
        observedTaskRevision = Long.MIN_VALUE;
        extractionRecovery.read(data, "ProductExtractionRecovery");
    }

    @Override public void writeToNBT(CompoundNBT data) {
        super.writeToNBT(data);
        if (boundManagerId != null) data.putUUID("PatternP2PUnitId", boundManagerId);
        boundFrequency = (short) getBoundFrequencyUnsigned();
        data.putShort("PatternP2PUnitFrequency", boundFrequency);
        data.putInt("TransferPriority", transferPriority);
        data.putBoolean("SingleSlot", singleSlot);
        data.put("OutputFilterMarkers", outputFilterMarkers.serializeNBT());
        data.put("OutputFilterInverter", outputFilterInverter.serializeNBT());
        data.put("InputFilterMarkers", inputFilterMarkers.serializeNBT());
        data.put("InputFilterInverter", inputFilterInverter.serializeNBT());
        extractionRecovery.write(data, "ProductExtractionRecovery");
    }

    @Override public void writeToStream(PacketBuffer data) throws java.io.IOException {
        super.writeToStream(data);
        modelPowered = getProxy().isPowered();
        modelOnline = getGridNode() != null && getGridNode().isActive();
        data.writeBoolean(boundManagerId != null);
        if (boundManagerId != null) data.writeUUID(boundManagerId);
        data.writeShort(getBoundFrequencyUnsigned());
        data.writeInt(transferPriority);
        data.writeBoolean(singleSlot);
        data.writeBoolean(modelPowered);
        data.writeBoolean(modelOnline);
    }

    @Override public boolean readFromStream(PacketBuffer data) throws java.io.IOException {
        boolean changed = super.readFromStream(data);
        UUID oldId = boundManagerId;
        short oldFrequency = boundFrequency;
        boolean oldPowered = modelPowered;
        boolean oldOnline = modelOnline;
        boundManagerId = data.readBoolean() ? data.readUUID() : null;
        boundFrequency = data.readShort();
        int oldPriority = transferPriority;
        transferPriority = Math.max(-9999, Math.min(9999, data.readInt()));
        boolean oldSingleSlot = singleSlot;
        singleSlot = data.readBoolean();
        modelPowered = data.readBoolean();
        modelOnline = data.readBoolean();
        cachedManager = null;
        return changed || oldFrequency != boundFrequency || oldPriority != transferPriority
                || oldSingleSlot != singleSlot || !Objects.equals(oldId, boundManagerId)
                || oldPowered != modelPowered || oldOnline != modelOnline;
    }

    @Override public void getBoxes(IPartCollisionHelper helper) { helper.addBox(3, 3, 13, 13, 13, 16); }
    @Override public void getDrops(List<ItemStack> drops, boolean wrenched) {
        super.getDrops(drops, wrenched);
        ItemStack inputInverter = inputFilterInverter.getStackInSlot(0);
        if (!inputInverter.isEmpty()) drops.add(inputInverter.copy());
        extractionRecovery.addDrops(drops);
    }
    @Override public float getCableConnectionLength(AECableType cable) { return 1; }
    @Override public IPartModel getStaticModels() {
        return modelPowered && modelOnline ? modelActive : modelPowered ? modelOn : modelOff;
    }

    private static final class EmptyItemHandler implements IItemHandler {
        private static final EmptyItemHandler INSTANCE = new EmptyItemHandler();
        @Override public int getSlots() { return 0; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    }

    private final class ReturnHandler implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            PatternP2PUnitManagerPart manager = findManager();
            return manager == null || !allowsInputFilter(stack)
                    ? stack : manager.returnProduct(stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return allowsInputFilter(stack); }
    }
}
