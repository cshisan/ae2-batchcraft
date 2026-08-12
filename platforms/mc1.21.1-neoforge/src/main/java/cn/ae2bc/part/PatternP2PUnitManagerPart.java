package cn.ae2bc.part;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;
import appeng.helpers.externalstorage.GenericStackFluidStorage;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import appeng.items.parts.ColoredPartItem;
import appeng.items.tools.MemoryCardItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.networking.CablePart;
import appeng.util.InteractionUtil;
import appeng.util.SettingsFrom;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.logic.PatternDispatchMetadata;
import cn.ae2bc.logic.RemoteReturnInventory;
import cn.ae2bc.logic.PatternP2PUnitIdentityColors;
import cn.ae2bc.logic.PatternP2PUnitDimensions;
import cn.ae2bc.logic.PatternP2PUnitManagerLogic;
import cn.ae2bc.logic.PatternP2PUnitPortType;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.client.model.PatternP2PUnitModelData;
import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import cn.ae2bc.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Center cable that owns a unit task and connects to the AE network on all six sides. */
public final class PatternP2PUnitManagerPart extends CablePart implements PatternTaskEndpoint {
    private static final String FREQUENCY_TAG = "PatternP2PFrequency";
    private static final String PATTERN_P2P_UNIT_ID_TAG = "PatternP2PUnitId";
    private static final IPartModel MODEL = new PartModel(
            ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "part/p2p/pattern_p2p_unit_manager"),
            ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "part/pattern_p2p_unit_manager_glass"),
            ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "part/pattern_p2p_unit_manager_frequency"));

    private final PatternP2PUnitManagerLogic logic = new PatternP2PUnitManagerLogic(getMainNode(), this);
    private final RemoteReturnInventory returnInventory = new RemoteReturnInventory(
            this::findInputReturnInventory, logic::filterReturned, logic::onReturnedStack,
            this::alertReturnProducers);
    private final GenericStackItemStorage returnItemHandler = new GenericStackItemStorage(returnInventory);
    private final GenericStackFluidStorage returnFluidHandler = new GenericStackFluidStorage(returnInventory);
    private @Nullable BlockCapabilityCache<GenericInternalInventory, Direction> returnTargetCache;
    private short frequency;
    private UUID patternP2PUnitId = UUID.randomUUID();

    public PatternP2PUnitManagerPart(ColoredPartItem<?> partItem) {
        super(partItem);
    }

    public static void registerModels() {
        PartModels.registerModels(MODEL.getModels());
    }

    public PatternP2PUnitManagerLogic getLogic() {
        return logic;
    }

    public UUID getPatternP2PUnitId() {
        return patternP2PUnitId;
    }

    public short getFrequency() {
        return frequency;
    }

    public boolean hasConfiguredFrequency() {
        return frequency != 0;
    }

    public void setFrequency(short frequency) {
        if (logic.hasTaskState()) {
            return;
        }
        if (this.frequency == frequency) {
            return;
        }
        var previousInput = findInput();
        this.frequency = frequency;
        var grid = getMainNode().getGrid();
        if (grid != null) {
            grid.getService(PatternP2PTopologyGridService.class).topologyChanged();
        }
        getHost().markForSave();
        getHost().markForUpdate();
        if (previousInput != null) {
            previousInput.getInputLogic().invalidateOutputs();
        }
        synchronizeFromInput();
        notifyBoundPortsFrequencyChanged();
        notifyInputTopologyChanged();
    }

    @Override
    public boolean isOperationalTaskEndpoint() {
        return hasConfiguredFrequency() && getMainNode().isActive();
    }

    @Override
    public boolean canAcceptTask() {
        return logic.canAcceptTask();
    }

    @Override
    public boolean isTaskActive() {
        return logic.hasTaskState();
    }

    @Override
    public boolean tryAcceptPattern(IPatternDetails pattern, PatternDispatchMetadata metadata,
                                    KeyCounter[] inputs, IActionSource source) {
        return logic.tryAcceptPattern(pattern, metadata, inputs);
    }

    @Override
    public void resetTaskState() {
        logic.resetTaskState();
    }

    public RemoteReturnInventory getReturnInventory() {
        return returnInventory;
    }

    public GenericStackItemStorage getReturnItemHandler() {
        return returnItemHandler;
    }

    public GenericStackFluidStorage getReturnFluidHandler() {
        return returnFluidHandler;
    }

    private void alertReturnProducers() {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        for (PatternP2PUnitPortPart port : grid.getService(PatternP2PTopologyGridService.class)
                .getPorts(patternP2PUnitId, PatternP2PUnitPortType.EXTRACT)) {
            port.alertTicking();
        }
    }

    public @Nullable PatternP2PTunnelPart findInput() {
        var grid = getMainNode().getGrid();
        if (grid == null || frequency == 0) {
            return null;
        }
        return grid.getService(PatternP2PTopologyGridService.class).findInput(frequency);
    }

    public void synchronizeFromInput() {
        var input = findInput();
        if (input != null) {
            var inputLogic = input.getInputLogic();
            logic.applyMainConfiguration(inputLogic.getPatternP2PUnitConfiguration(),
                    inputLogic.getPatternP2PUnitConfigurationRevision());
        }
    }

    public void notifyInputAvailabilityChanged() {
        var input = findInput();
        if (input != null) {
            input.getInputLogic().invalidateOutputAvailability();
        }
    }

    private void notifyInputTopologyChanged() {
        var input = findInput();
        if (input != null) {
            input.getInputLogic().invalidateOutputs();
        }
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        synchronizeFromInput();
        notifyInputTopologyChanged();
    }

    @Override
    public void removeFromWorld() {
        var input = findInput();
        super.removeFromWorld();
        if (input != null) {
            input.getInputLogic().invalidateOutputs();
        }
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide()) {
            MenuOpener.open(PatternP2PUnitManagerMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        if (hand == InteractionHand.MAIN_HAND && heldItem.isEmpty()) {
            return onUseWithoutItem(player, pos);
        }
        if (!(heldItem.getItem() instanceof IMemoryCard memoryCard)) {
            return super.onUseItemOn(heldItem, player, hand, pos);
        }
        if (hand == InteractionHand.OFF_HAND) {
            return false;
        }
        if (isClientSide()) {
            return true;
        }
        if (InteractionUtil.isInAlternateUseMode(player)) {
            MemoryCardItem.clearCard(heldItem);
            heldItem.set(AEComponents.EXPORTED_SETTINGS_SOURCE, getPartItem().asItem().getDescription());
            heldItem.applyComponents(exportSettings(SettingsFrom.MEMORY_CARD));
            heldItem.set(ModContent.PATTERN_P2P_UNIT_ID.get(), patternP2PUnitId);
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
        } else {
            var storedType = heldItem.get(AEComponents.EXPORTED_P2P_TYPE);
            var storedFrequency = heldItem.get(AEComponents.EXPORTED_P2P_FREQUENCY);
            if ((storedType != ModContent.PATTERN_P2P_TUNNEL_INPUT.get()
                    && !ModContent.isPatternP2PUnitManagerItem(storedType)) || storedFrequency == null) {
                memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            } else {
                if (logic.hasTaskState()) {
                    memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
                } else {
                    setFrequency(storedFrequency);
                    memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
                }
            }
        }
        return true;
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder) {
        super.exportSettings(mode, builder);
        if (mode == SettingsFrom.MEMORY_CARD) {
            builder.set(AEComponents.EXPORTED_P2P_TYPE, getPartItem().asItem());
            if (frequency != 0) {
                builder.set(AEComponents.EXPORTED_P2P_FREQUENCY, frequency);
            }
        }
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        frequency = data.getShort(FREQUENCY_TAG);
        patternP2PUnitId = data.hasUUID(PATTERN_P2P_UNIT_ID_TAG) ? data.getUUID(PATTERN_P2P_UNIT_ID_TAG) : UUID.randomUUID();
        logic.readFromNBT(data, registries);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putShort(FREQUENCY_TAG, frequency);
        data.putUUID(PATTERN_P2P_UNIT_ID_TAG, patternP2PUnitId);
        logic.writeToNBT(data, registries);
    }

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeShort(frequency);
        data.writeUUID(patternP2PUnitId);
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        short previous = frequency;
        UUID previousPatternP2PUnitId = patternP2PUnitId;
        frequency = data.readShort();
        patternP2PUnitId = data.readUUID();
        return changed || previous != frequency || !previousPatternP2PUnitId.equals(patternP2PUnitId);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        logic.addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        logic.clearContent();
    }

    private @Nullable GenericInternalInventory findInputReturnInventory() {
        if (!isOperationalTaskEndpoint()) {
            return null;
        }
        var input = findInput();
        if (input == null || !input.getMainNode().isActive()) {
            return null;
        }
        Direction side = input.getSide();
        if (side == null || !(input.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        var targetPos = input.getBlockEntity().getBlockPos().relative(side);
        Direction targetSide = side.getOpposite();
        if (returnTargetCache == null || returnTargetCache.level() != level
                || !returnTargetCache.pos().equals(targetPos)
                || returnTargetCache.context() != targetSide) {
            returnTargetCache = BlockCapabilityCache.create(
                    AECapabilities.GENERIC_INTERNAL_INV, level, targetPos, targetSide);
        }
        return returnTargetCache.getCapability();
    }

    @Override
    public AECableType getCableConnectionType() {
        return AECableType.SMART;
    }

    @Override
    public boolean changeColor(AEColor newColor, Player who) {
        if (newColor == getCableColor()) {
            return false;
        }
        if (isClientSide()) {
            return true;
        }
        setPartItem(ModContent.getPatternP2PUnitManager(newColor));
        getMainNode().setGridColor(newColor);
        getHost().partChanged();
        getHost().markForUpdate();
        getHost().markForSave();
        return true;
    }

    @Override
    public void getBoxes(IPartCollisionHelper helper, Predicate<@Nullable Direction> filterConnections) {
        updateConnections();
        addNonDenseBoxes(helper, filterConnections,
                PatternP2PUnitDimensions.FRAME_MIN, PatternP2PUnitDimensions.FRAME_MAX);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }

    @Override
    public ModelData getModelData() {
        long value = Short.toUnsignedLong(frequency);
        if (isActive() && isPowered()) {
            value |= 0x10000L;
        }
        long patternP2PUnitValue = Short.toUnsignedLong(PatternP2PUnitIdentityColors.encode(patternP2PUnitId));
        if (isActive() && isPowered()) {
            patternP2PUnitValue |= 0x10000L;
        }
        return ModelData.builder()
                .with(P2PTunnelFrequencyModelData.FREQUENCY, value)
                .with(PatternP2PUnitModelData.PATTERN_P2P_UNIT_ID, patternP2PUnitValue)
                .build();
    }

    private void notifyBoundPortsFrequencyChanged() {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        for (PatternP2PUnitPortPart port : grid.getService(PatternP2PTopologyGridService.class)
                .getPorts(patternP2PUnitId)) {
            port.getHost().markForUpdate();
        }
    }
}
