package cn.ae2bc.part;

import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.logic.ItemStackExtractionRecoveryQueue;
import cn.ae2bc.platform.AnnihilationPlaneBreakStrategy;

import java.util.Objects;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.AEApi;
import appeng.helpers.IPriorityHost;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;
import cn.ae2bc.logic.PatternP2PUnitIdentityColors;
import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import cn.ae2bc.core.energy.EnergyEndpoint;
import cn.ae2bc.core.schedule.ExtractionDeadlineGate;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.common.capabilities.Capability;

/** rv6 unchanneled endpoint with durable manager binding. */
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
            "appliedenergistics2", "part/builtin/p2p_tunnel_frequency");
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
    private final AnnihilationPlaneBreakStrategy breakStrategy;

    public PatternP2PUnitPortPart(ItemStack stack, UnitPortType type) {
        super(stack);
        this.type = type;
        this.outputFilterMarkers = new ItemStackHandler(18) {
            @Override protected void onContentsChanged(int slot) { getHost().markForSave(); getHost().markForUpdate(); }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
        };
        this.outputFilterInverter = new ItemStackHandler(1) {
            @Override protected void onContentsChanged(int slot) { getHost().markForSave(); getHost().markForUpdate(); }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return AEApi.instance().definitions().materials().cardInverter().isSameAs(stack);
            }
        };
        this.inputFilterMarkers = new ItemStackHandler(18) {
            @Override protected void onContentsChanged(int slot) { getHost().markForSave(); getHost().markForUpdate(); }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
        };
        this.inputFilterInverter = new ItemStackHandler(1) {
            @Override protected void onContentsChanged(int slot) { getHost().markForSave(); getHost().markForUpdate(); }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return AEApi.instance().definitions().materials().cardInverter().isSameAs(stack);
            }
        };
        this.extractionRecovery = new ItemStackExtractionRecoveryQueue(() -> getHost().markForSave());
        this.breakStrategy = new AnnihilationPlaneBreakStrategy(this);
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
    public Long getRenderFlag() {
        long frequency = Short.toUnsignedLong(boundFrequency);
        long unitId = Short.toUnsignedLong(PatternP2PUnitIdentityColors.encode(boundManagerId));
        if (modelPowered && modelOnline) {
            frequency |= 0x10000L;
            unitId |= 0x10000L;
        }
        return Long.valueOf(frequency | unitId << 17);
    }
    public UnitPortType getPortType() { return type; }
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
                if (ItemHandlerHelper.canItemStacksStack(marker, stack)) marked = true;
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
                if (ItemHandlerHelper.canItemStacksStack(marker, stack)) marked = true;
            }
        }
        boolean inverted = !inputFilterInverter.getStackInSlot(0).isEmpty();
        return !hasMarkers || (inverted ? !marked : marked);
    }
    public int getTransferPriority() { return transferPriority; }
    public boolean isSingleSlot() { return singleSlot; }
    public boolean isSingleSlotEditable() {
        PatternP2PUnitManagerPart manager = findManager();
        return manager == null || manager.getOutputSlotSharingMode() == OutputSlotSharingMode.FOLLOW_PORT;
    }

    /** Returns whether this port is currently inheriting the input-side configuration. */
    public boolean isUsingMainConfiguration() {
        PatternP2PUnitManagerPart manager = findManager();
        return manager != null && manager.isSyncMainConfiguration();
    }
    public boolean getEffectiveSingleSlot() {
        return singleSlot;
    }
    public void applyManagerSingleSlot(OutputSlotSharingMode mode) {
        if (mode == null || mode == OutputSlotSharingMode.FOLLOW_PORT) return;
        boolean value = mode == OutputSlotSharingMode.ALL;
        if (singleSlot == value) return;
        singleSlot = value;
        saveChanges();
        getHost().markForUpdate();
    }
    public void setSingleSlot(boolean value) {
        if (!isSingleSlotEditable()) return;
        if (singleSlot == value) return;
        singleSlot = value;
        saveChanges(); getHost().markForUpdate();
    }
    @Override public int getPriority() { return transferPriority; }
    @Override public void setPriority(int value) { setTransferPriority(value); }
    @Override public GuiBridge getGuiBridge() { return GuiBridge.GUI_PRIORITY; }
    @Override public ItemStack getItemStackRepresentation() { return getItemStack(); }
    public void setTransferPriority(int value) { transferPriority = Math.max(-9999, Math.min(9999, value)); saveChanges(); getHost().markForUpdate(); }
    public UUID getBoundManagerId() { return boundManagerId; }
    public short getBoundFrequency() { return (short) getBoundFrequencyUnsigned(); }
    public boolean isBoundUnitTaskActive() {
        PatternP2PUnitManagerPart manager = findManager();
        return manager != null && manager.isTaskActive();
    }
    @Override public boolean isPowered() {
        return getTile().getWorld() != null && getTile().getWorld().isRemote
                ? modelPowered : getProxy().isPowered();
    }
    @Override public boolean isActive() {
        return getTile().getWorld() != null && getTile().getWorld().isRemote
                ? modelOnline : getGridNode() != null && getGridNode().isActive();
    }
    public int getBoundFrequencyUnsigned() {
        PatternP2PUnitManagerPart manager = findManager();
        return manager == null ? Short.toUnsignedInt(boundFrequency) : manager.getFrequencyUnsigned();
    }
    public PatternP2PUnitManagerPart findManager() {
        if (boundManagerId == null || getTile().getWorld() == null) {
            cachedManager = null;
            return null;
        }
        cachedManager = PatternP2PTopologyGridService.find(getGridNode(), boundManagerId,
                getTile().getWorld().getTotalWorldTime());
        return cachedManager;
    }

    public boolean isOperational() {
        PatternP2PUnitManagerPart manager = findManager();
        return manager != null && manager.isOperational();
    }

    public int insertTaskInput(PatternP2PUnitManagerPart manager, ItemStack stack, boolean simulate) {
        cn.ae2bc.pattern.MaterialOutputForm form = type == UnitPortType.DROP
                ? cn.ae2bc.pattern.MaterialOutputForm.DROP
                : type == UnitPortType.PLACE
                        ? cn.ae2bc.pattern.MaterialOutputForm.PLACE
                        : cn.ae2bc.pattern.MaterialOutputForm.NORMAL;
        return insertTaskInput(manager, stack, form, simulate);
    }

    public int insertTaskInput(PatternP2PUnitManagerPart manager, ItemStack stack,
            cn.ae2bc.pattern.MaterialOutputForm form, boolean simulate) {
        if (!matchesInput(manager, stack, form) || !manager.isOperational()) return 0;
        if (type == UnitPortType.TRANSFER) {
            ItemStack remainder = ItemHandlerHelper.insertItem(adjacentItemHandler(), stack.copy(), simulate);
            return stack.getCount() - remainder.getCount();
        }
        if (type == UnitPortType.DROP && getTile().getWorld() != null && getSide() != null) {
            EnumFacing face = getSide().getFacing();
            net.minecraft.util.math.BlockPos target = getTile().getPos().offset(face);
            if (!getTile().getWorld().isBlockLoaded(target)) return 0;
            if (!simulate) {
                EntityItem entity = new EntityItem(getTile().getWorld(), target.getX() + 0.5,
                        target.getY() + 0.5, target.getZ() + 0.5, stack.copy());
                entity.motionX = face.getXOffset() * 0.1;
                entity.motionY = face.getYOffset() * 0.1;
                entity.motionZ = face.getZOffset() * 0.1;
                if (!getTile().getWorld().spawnEntity(entity)) return 0;
            }
            return stack.getCount();
        }
        if (type == UnitPortType.PLACE && stack.getCount() > 0 && stack.getItem() instanceof ItemBlock
                && getTile().getWorld() instanceof WorldServer && getSide() != null) {
            EnumFacing face = getSide().getFacing();
            BlockPos target = getTile().getPos().offset(face);
            if (!getTile().getWorld().getBlockState(target).getMaterial().isReplaceable()) return 0;
            if (simulate) return 1;
            WorldServer level = (WorldServer) getTile().getWorld();
            FakePlayer player = FakePlayerFactory.getMinecraft(level);
            ItemStack previous = player.getHeldItem(EnumHand.MAIN_HAND);
            ItemStack placing = stack.copy();
            // A placement consumes one item; the remaining stack stays pending for
            // the same encoded slot and can be retried on a later tick.
            placing.setCount(1);
            player.setHeldItem(EnumHand.MAIN_HAND, placing);
            try {
                EnumActionResult result = placing.getItem().onItemUse(player, level, target,
                        EnumHand.MAIN_HAND, face.getOpposite(), 0.5f, 0.5f, 0.5f);
                return result == EnumActionResult.SUCCESS && placing.isEmpty() ? 1 : 0;
            } finally {
                player.setHeldItem(EnumHand.MAIN_HAND, previous);
            }
        }
        return 0;
    }

    public boolean matchesInput(PatternP2PUnitManagerPart manager, ItemStack stack,
            cn.ae2bc.pattern.MaterialOutputForm form) {
        return isBoundTo(manager) && stack != null && !stack.isEmpty() && form != null
                && UnitPortType.forOutputFormId(form.getId()) == type
                && form.supports(stack)
                && allowsOutputFilter(stack);
    }

    /** Compatibility overload for callers that only have a port-local check. */
    public boolean matchesInput(ItemStack stack, cn.ae2bc.pattern.MaterialOutputForm form) {
        return stack != null && !stack.isEmpty() && form != null
                && UnitPortType.forOutputFormId(form.getId()) == type
                && form.supports(stack) && allowsOutputFilter(stack);
    }

    /** Estimates the aggregate capacity of the adjacent item handler for one item type. */
    public int estimateTransferCapacity(ItemStack what, int fallback) {
        if (type != UnitPortType.TRANSFER || what == null || what.isEmpty()) return fallback;
        IItemHandler handler = adjacentItemHandler();
        long total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack existing = handler.getStackInSlot(slot);
            int limit = Math.max(0, handler.getSlotLimit(slot));
            if (existing.isEmpty()) {
                total += limit;
            } else if (ItemStack.areItemsEqual(existing, what)
                    && ItemStack.areItemStackTagsEqual(existing, what)) {
                ItemStack probe = what.copy();
                probe.setCount(limit);
                ItemStack remainder = handler.insertItem(slot, probe, true);
                total += Math.max(0, limit - remainder.getCount());
            }
            if (total >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return total > 0 ? (int) Math.min(Integer.MAX_VALUE, total) : fallback;
    }

    @Override public boolean isEnergyEndpointAvailable() {
        return type == UnitPortType.ENERGY && isOperational();
    }

    @Override public int receiveExternalEnergy(int maxReceive, boolean simulate) {
        if (type != UnitPortType.ENERGY || maxReceive <= 0 || !isOperational()) return 0;
        IEnergyStorage target = adjacentEnergyStorage();
        return target == null || !target.canReceive() ? 0 : target.receiveEnergy(maxReceive, simulate);
    }

    public boolean isBoundTo(PatternP2PUnitManagerPart manager) {
        return manager != null && boundManagerId != null && boundManagerId.equals(manager.getUnitId())
                && (boundFrequency == 0 || boundFrequency == manager.getFrequency());
    }

    private IItemHandler adjacentItemHandler() {
        if (getTile().getWorld() == null || getSide() == null) return EmptyItemHandler.INSTANCE;
        EnumFacing face = getSide().getFacing();
        TileEntity tile = getTile().getWorld().getTileEntity(getTile().getPos().offset(face));
        if (tile == null) return EmptyItemHandler.INSTANCE;
        IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite());
        return handler == null ? EmptyItemHandler.INSTANCE : handler;
    }

    private IEnergyStorage adjacentEnergyStorage() {
        if (getTile().getWorld() == null || getSide() == null) return null;
        EnumFacing face = getSide().getFacing();
        TileEntity tile = getTile().getWorld().getTileEntity(getTile().getPos().offset(face));
        return tile == null ? null : tile.getCapability(CapabilityEnergy.ENERGY, face.getOpposite());
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

    public void invalidateTaskRuntimeState() {
        breakStrategy.reset();
        taskStartTick = Long.MIN_VALUE;
        observedTaskRevision = Long.MIN_VALUE;
        redstoneWorldStateDirty = true;
        setRedstonePower(0);
        alertTicking();
    }

    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        PatternP2PUnitManagerPart manager = findManager();
        if (manager == null || !manager.isOperational() || getTile().getWorld() == null) {
            breakStrategy.reset();
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
            if (!extractionDeadline.isDue(getTile().getWorld().getTotalWorldTime(), manager.getExtractionInterval())) {
                return recoveryProgress ? TickRateModulation.URGENT : TickRateModulation.SAME;
            }
            return extractFromAdjacent(manager, manager.getExtractionAmount()) > 0 || recoveryProgress
                    ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
        if (!manager.isTaskActive()) {
            breakStrategy.reset();
            taskStartTick = Long.MIN_VALUE;
            setRedstonePower(0);
            return TickRateModulation.SLEEP;
        }
        if (type == UnitPortType.REDSTONE) return tickRedstone(manager);
        if (type == UnitPortType.COLLECT) return collect(manager)
                ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        if (type == UnitPortType.BREAK) return breakStrategy.tick(manager);
        return TickRateModulation.SLEEP;
    }

    private TickRateModulation tickRedstone(PatternP2PUnitManagerPart manager) {
        long now = getTile().getWorld().getTotalWorldTime();
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
        if (getTile().getWorld() == null) return;
        redstonePower = next;
        redstoneWorldStateDirty = false;
        notifyNeighbors();
    }
    private void notifyNeighbors() {
        if (getTile().getWorld() == null) return;
        getTile().getWorld().notifyNeighborsOfStateChange(getTile().getPos(),
                getTile().getWorld().getBlockState(getTile().getPos()).getBlock(), false);
    }

    private boolean collectDroppedItems(PatternP2PUnitManagerPart manager) {
        if (!(getTile().getWorld() instanceof WorldServer) || getSide() == null) return false;
        WorldServer level = (WorldServer) getTile().getWorld();
        BlockPos target = getTile().getPos().offset(getSide().getFacing());
        boolean moved = false;
        for (EntityItem entity : level.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(target))) {
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
            if (left.isEmpty()) entity.setDead(); else entity.setItem(left);
            moved = true;
        }
        return moved;
    }

    private boolean collect(PatternP2PUnitManagerPart manager) {
        return collectDroppedItems(manager);
    }

    @Override public boolean canConnectRedstone() { return type == UnitPortType.REDSTONE; }
    @Override public int isProvidingStrongPower() { return type == UnitPortType.REDSTONE ? redstonePower : 0; }
    @Override public int isProvidingWeakPower() { return isProvidingStrongPower(); }

    @Override public boolean hasCapability(Capability<?> capability) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && type == UnitPortType.RETURN
                || super.hasCapability(capability);
    }
    @Override public <T> T getCapability(Capability<T> capability) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && type == UnitPortType.RETURN) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(returnHandler);
        }
        return super.getCapability(capability);
    }

    @Override public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d hit) {
        if (bindFromMemoryCard(player, hand)) return true;
        if (hand == EnumHand.MAIN_HAND && (type.acceptsTaskInput() || type.returnsTaskOutput())) {
            if (!player.world.isRemote) {
                player.openGui(cn.ae2bc.Ae2bcMod.INSTANCE,
                        (type.acceptsTaskInput()
                                ? cn.ae2bc.Ae2bcMod.GUI_UNIT_PORT_OUTPUT_BASE
                                : cn.ae2bc.Ae2bcMod.GUI_UNIT_PORT_INPUT_BASE)
                                + getSide().getFacing().ordinal(),
                        player.world, getTile().getPos().getX(), getTile().getPos().getY(),
                        getTile().getPos().getZ());
            }
            return true;
        }
        return super.onPartActivate(player, hand, hit);
    }
    @Override public boolean onPartShiftActivate(EntityPlayer player, EnumHand hand, Vec3d hit) {
        return bindFromMemoryCard(player, hand) || super.onPartShiftActivate(player, hand, hit);
    }
    private boolean bindFromMemoryCard(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (hand != EnumHand.MAIN_HAND || !(held.getItem() instanceof IMemoryCard)) return false;
        if (player.world.isRemote) return true;
        IMemoryCard card = (IMemoryCard) held.getItem();
        NBTTagCompound data = card.getData(held);
        if (!data.hasUniqueId("PatternP2PUnitId") || !data.hasKey("freq")) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }
        UUID requestedId = data.getUniqueId("PatternP2PUnitId");
        short requestedFrequency = data.getShort("freq");
        PatternP2PUnitManagerPart current = findManager();
        PatternP2PUnitManagerPart requested = PatternP2PTopologyGridService.find(getGridNode(), requestedId,
                getTile().getWorld().getTotalWorldTime());
        if ((current != null && current.isTaskActive())
                || (requested != null && requested.isTaskActive())) {
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
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
        saveChanges();
        getHost().markForUpdate();
        card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        return true;
    }

    @Override
    public void gridChanged() {
        super.gridChanged();
        cachedManager = null;
        PatternP2PTopologyGridService.invalidate(getGridNode());
        redstoneWorldStateDirty = true;
        alertTicking();
        refreshModelState();
    }

    @MENetworkEventSubscribe
    public void onPowerStatusChanged(MENetworkPowerStatusChange event) {
        cachedManager = null;
        PatternP2PTopologyGridService.invalidate(getGridNode());
        redstoneWorldStateDirty = true;
        alertTicking();
        refreshModelState();
    }

    private void refreshModelState() {
        if (getTile() == null || getTile().getWorld() == null || getTile().getWorld().isRemote) return;
        boolean powered = getProxy().isPowered();
        boolean online = getGridNode() != null && getGridNode().isActive();
        if (modelPowered == powered && modelOnline == online) return;
        modelPowered = powered;
        modelOnline = online;
        getHost().markForUpdate();
    }

    @Override public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        boundManagerId = data.hasUniqueId("PatternP2PUnitId") ? data.getUniqueId("PatternP2PUnitId") : null;
        boundFrequency = data.getShort("PatternP2PUnitFrequency");
        transferPriority = Math.max(-9999, Math.min(9999, data.getInteger("TransferPriority")));
        singleSlot = data.hasKey("SingleSlot") ? data.getBoolean("SingleSlot")
                : data.hasKey("AllowMultiplePatternSlots") && !data.getBoolean("AllowMultiplePatternSlots");
        if (data.hasKey("OutputFilterMarkers")) outputFilterMarkers.deserializeNBT(data.getCompoundTag("OutputFilterMarkers"));
        if (data.hasKey("OutputFilterInverter")) outputFilterInverter.deserializeNBT(data.getCompoundTag("OutputFilterInverter"));
        if (data.hasKey("InputFilterMarkers")) inputFilterMarkers.deserializeNBT(data.getCompoundTag("InputFilterMarkers"));
        if (data.hasKey("InputFilterInverter")) inputFilterInverter.deserializeNBT(data.getCompoundTag("InputFilterInverter"));
        cachedManager = null;
        redstoneWorldStateDirty = true;
        taskStartTick = Long.MIN_VALUE;
        observedTaskRevision = Long.MIN_VALUE;
        extractionRecovery.read(data, "ProductExtractionRecovery");
    }
    @Override public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        if (boundManagerId != null) data.setUniqueId("PatternP2PUnitId", boundManagerId);
        boundFrequency = (short) getBoundFrequencyUnsigned();
        data.setShort("PatternP2PUnitFrequency", boundFrequency);
        data.setInteger("TransferPriority", transferPriority);
        data.setBoolean("SingleSlot", singleSlot);
        data.setTag("OutputFilterMarkers", outputFilterMarkers.serializeNBT());
        data.setTag("OutputFilterInverter", outputFilterInverter.serializeNBT());
        data.setTag("InputFilterMarkers", inputFilterMarkers.serializeNBT());
        data.setTag("InputFilterInverter", inputFilterInverter.serializeNBT());
        extractionRecovery.write(data, "ProductExtractionRecovery");
    }
    @Override public void writeToStream(ByteBuf data) throws java.io.IOException {
        super.writeToStream(data);
        modelPowered = getProxy().isPowered();
        modelOnline = getGridNode() != null && getGridNode().isActive();
        data.writeBoolean(boundManagerId != null);
        if (boundManagerId != null) {
            data.writeLong(boundManagerId.getMostSignificantBits());
            data.writeLong(boundManagerId.getLeastSignificantBits());
        }
        data.writeShort(getBoundFrequencyUnsigned());
        data.writeInt(transferPriority);
        data.writeBoolean(singleSlot);
        data.writeBoolean(modelPowered);
        data.writeBoolean(modelOnline);
    }
    @Override public boolean readFromStream(ByteBuf data) throws java.io.IOException {
        boolean changed = super.readFromStream(data);
        UUID oldId = boundManagerId;
        short oldFrequency = boundFrequency;
        boolean oldPowered = modelPowered;
        boolean oldOnline = modelOnline;
        boundManagerId = data.readBoolean() ? new UUID(data.readLong(), data.readLong()) : null;
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

    private final IItemHandler returnHandler = new IItemHandler() {
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
    };
}
