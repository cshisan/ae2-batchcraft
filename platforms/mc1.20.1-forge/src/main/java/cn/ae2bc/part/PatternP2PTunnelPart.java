package cn.ae2bc.part;


import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.features.P2PTunnelAttunement;
import appeng.api.networking.IGridNodeListener;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartHelper;
import appeng.api.parts.PartModels;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackFluidStorage;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import appeng.items.tools.MemoryCardItem;
import appeng.me.service.P2PService;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.P2PTunnelPart;
import appeng.util.InteractionUtil;
import appeng.util.SettingsFrom;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.placer.ComponentPlacerItem;
import cn.ae2bc.logic.PatternP2PTunnelInputLogic;
import cn.ae2bc.logic.PatternP2PTunnelOutputLogic;
import cn.ae2bc.logic.PatternP2PEnergyGridService;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.logic.RemoteReturnInventory;
import cn.ae2bc.logic.EndpointProductExtractionSettings;
import cn.ae2bc.menu.PatternP2PTunnelInputMenu;
import cn.ae2bc.menu.PatternP2PTunnelOutputMenu;
import cn.ae2bc.platform.ItemData;
import cn.ae2bc.registry.ModContent;
import net.minecraft.core.Direction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import cn.ae2bc.platform.ForgeBlockCapabilityCache;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PatternP2PTunnelPart extends P2PTunnelPart<PatternP2PTunnelPart>
        implements ICraftingMachine, PatternTaskEndpoint {
    private static final P2PModels INPUT_MODELS = new P2PModels(
            new ResourceLocation(Ae2bcMod.MOD_ID, "part/p2p/pattern_p2p_tunnel_input"));
    private static final P2PModels OUTPUT_MODELS = new P2PModels(
            new ResourceLocation(Ae2bcMod.MOD_ID, "part/p2p/pattern_p2p_tunnel_output"));
    public enum EndpointKind {
        INPUT,
        OUTPUT
    }

    private final EndpointKind endpointKind;
    private final PatternP2PTunnelInputLogic inputLogic;
    private final PatternP2PTunnelOutputLogic outputLogic;
    private final RemoteReturnInventory returnInventory;
    private final GenericStackItemStorage returnItemHandler;
    private final GenericStackFluidStorage returnFluidHandler;
    private ForgeBlockCapabilityCache<IEnergyStorage> energyTargetCache;
    private boolean energyTargetIsEnergyTunnel;
    private boolean energyTargetResolved;
    private ForgeBlockCapabilityCache<GenericInternalInventory> returnTargetCache;

    public PatternP2PTunnelPart(IPartItem<?> partItem, boolean output) {
        this(partItem, output ? EndpointKind.OUTPUT : EndpointKind.INPUT);
    }

    private PatternP2PTunnelPart(IPartItem<?> partItem, EndpointKind endpointKind) {
        super(partItem);
        this.endpointKind = endpointKind;
        if (endpointKind != EndpointKind.INPUT) {
            getMainNode().setFlags();
        }
        this.inputLogic = endpointKind == EndpointKind.INPUT
                ? new PatternP2PTunnelInputLogic(getMainNode(), this) : null;
        this.outputLogic = endpointKind == EndpointKind.OUTPUT
                ? new PatternP2PTunnelOutputLogic(getMainNode(), this) : null;
        this.returnInventory = new RemoteReturnInventory(this::findInputReturnInventory,
                (what, amount) -> outputLogic != null ? outputLogic.filterReturnAmount(what, amount)
                        : 0,
                stack -> {
                    if (outputLogic != null) {
                        outputLogic.onReturnedStack(stack);
                    }
                }, this::alertReturnProducer);
        this.returnItemHandler = new GenericStackItemStorage(returnInventory);
        this.returnFluidHandler = new GenericStackFluidStorage(returnInventory);
    }

    public static void registerModels() {
        PartModels.registerModels(INPUT_MODELS.getModels().stream()
                .flatMap(model -> model.getModels().stream())
                .toList());
        PartModels.registerModels(OUTPUT_MODELS.getModels().stream()
                .flatMap(model -> model.getModels().stream())
                .toList());
    }

    @Override
    public boolean isOutput() {
        return endpointKind == EndpointKind.OUTPUT;
    }

    public boolean isStandardOutput() {
        return isOutput();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability) {
        if (!isOutput() && capability == appeng.capabilities.Capabilities.CRAFTING_MACHINE) {
            return LazyOptional.of(() -> this).cast();
        }
        if (isStandardOutput()) {
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

    public boolean isOperationalOutput() {
        return isStandardOutput() && hasConfiguredFrequency() && getMainNode().isActive();
    }

    public boolean isOperationalTaskEndpoint() {
        return isOutput() && hasConfiguredFrequency() && getMainNode().isActive();
    }

    public boolean hasConfiguredFrequency() {
        return getFrequency() != 0;
    }

    public PatternP2PTunnelInputLogic getInputLogic() {
        if (inputLogic == null) {
            throw new IllegalStateException("Output endpoint has no input logic");
        }
        return inputLogic;
    }

    public PatternP2PTunnelOutputLogic getOutputLogic() {
        if (outputLogic == null) {
            throw new IllegalStateException("Input endpoint has no output logic");
        }
        return outputLogic;
    }

    @Override
    public boolean canAcceptTask() {
        return outputLogic != null && outputLogic.canAcceptTask();
    }

    @Override
    public boolean isTaskActive() {
        return outputLogic != null && outputLogic.isTaskActive();
    }

    @Override
    public boolean tryAcceptPattern(IPatternDetails pattern, cn.ae2bc.logic.PatternDispatchMetadata metadata,
                                    KeyCounter[] inputs, appeng.api.networking.security.IActionSource source) {
        return outputLogic != null && outputLogic.tryAcceptPattern(pattern, metadata, inputs, source);
    }

    @Override
    public void resetTaskState() {
        if (outputLogic != null) {
            outputLogic.resetTaskState();
        }
    }

    public RemoteReturnInventory getReturnInventory() {
        return returnInventory;
    }

    private void alertReturnProducer() {
        if (outputLogic != null) {
            outputLogic.alertRetry();
        }
    }

    public @Nullable EndpointProductExtractionSettings getProductExtractionSettingsFromInput() {
        if (!isStandardOutput()) {
            return null;
        }
        var input = getInput();
        if (input == null || input.isOutput() || !input.hasConfiguredFrequency()) {
            return null;
        }
        return input.getInputLogic().getProductExtractionSettings();
    }

    public GenericStackItemStorage getReturnItemHandler() {
        return returnItemHandler;
    }

    public GenericStackFluidStorage getReturnFluidHandler() {
        return returnFluidHandler;
    }

    public int receiveExternalEnergy(int maxReceive, boolean simulate) {
        if (!isOperationalOutput() || maxReceive <= 0) {
            return 0;
        }

        Direction side = getSide();
        var level = getLevel();
        if (side == null || !(level instanceof ServerLevel serverLevel)) {
            return 0;
        }

        var targetPos = getBlockEntity().getBlockPos().relative(side);
        Direction targetSide = side.getOpposite();
        if (!energyTargetResolved) {
            var targetHost = PartHelper.getPartHost(level, targetPos);
            energyTargetIsEnergyTunnel = targetHost != null
                    && targetHost.getPart(targetSide) instanceof PatternP2PTunnelEnergyPart;
            energyTargetResolved = true;
        }
        if (energyTargetIsEnergyTunnel) {
            return 0;
        }

        if (energyTargetCache == null
                || energyTargetCache.level() != serverLevel
                || !energyTargetCache.pos().equals(targetPos)
                || energyTargetCache.context() != targetSide) {
            energyTargetCache = ForgeBlockCapabilityCache.create(
                    ForgeCapabilities.ENERGY, serverLevel, targetPos, targetSide);
        }
        var target = energyTargetCache.getCapability();
        if (target == null || !target.canReceive()) {
            return 0;
        }
        return target.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        var item = ModContent.PATTERN_P2P_TUNNEL_INPUT.get();
        return new PatternContainerGroup(AEItemKey.of(item), item.getDescription(), List.of());
    }

    @Override
    public boolean acceptsPlans() {
        return inputLogic != null && inputLogic.hasAvailableOutput();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        return inputLogic != null && inputLogic.pushPattern(patternDetails, inputs);
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data );
        if (inputLogic != null) {
            inputLogic.readFromNBT(data );
        }
        if (outputLogic != null) {
            outputLogic.readFromNBT(data );
        }
    }

    @Override
    public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data );
        if (inputLogic != null) {
            inputLogic.writeToNBT(data );
        }
        if (outputLogic != null) {
            outputLogic.writeToNBT(data );
        }
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        if (outputLogic != null) {
            outputLogic.addDrops(drops);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        if (outputLogic != null) {
            outputLogic.clearContent();
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) {
            return;
        }

        // Cable-bus hits are handled by IPart.onUseWithoutItem/onUseItemOn.
        if (PartHelper.getPartHost(event.getLevel(), event.getPos()) != null) {
            return;
        }

        var tunnel = findTunnelBehindHitBlock(event);
        if (tunnel == null
                || !event.getLevel().mayInteract(event.getEntity(),
                tunnel.getBlockEntity().getBlockPos())) {
            return;
        }

        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
        if (!event.getLevel().isClientSide()) {
            tunnel.openConfigurationMenu(event.getEntity());
        }
    }

    private static PatternP2PTunnelPart findTunnelBehindHitBlock(
            PlayerInteractEvent.RightClickBlock event) {
        var clickedFace = event.getFace();
        if (clickedFace == null || !isInsideP2PProjection(event, clickedFace)) {
            return null;
        }

        var host = PartHelper.getPartHost(event.getLevel(), event.getPos().relative(clickedFace));
        if (host == null) {
            return null;
        }

        var part = host.getPart(clickedFace.getOpposite());
        return part instanceof PatternP2PTunnelPart tunnel ? tunnel : null;
    }

    private static boolean isInsideP2PProjection(PlayerInteractEvent.RightClickBlock event, Direction face) {
        var blockPos = event.getPos();
        var localHit = event.getHitVec().getLocation().subtract(
                blockPos.getX(), blockPos.getY(), blockPos.getZ());
        return switch (face.getAxis()) {
            case X -> isInsidePanelRange(localHit.y) && isInsidePanelRange(localHit.z);
            case Y -> isInsidePanelRange(localHit.x) && isInsidePanelRange(localHit.z);
            case Z -> isInsidePanelRange(localHit.x) && isInsidePanelRange(localHit.y);
        };
    }

    private static boolean isInsidePanelRange(double coordinate) {
        return coordinate >= 2.0 / 16.0 && coordinate <= 14.0 / 16.0;
    }

    private void openConfigurationMenu(Player player) {
        if (isStandardOutput()) {
            MenuOpener.open(PatternP2PTunnelOutputMenu.TYPE, player, MenuLocators.forPart(this));
        } else {
            MenuOpener.open(PatternP2PTunnelInputMenu.TYPE, player, MenuLocators.forPart(this));
        }
    }

    @Override
    public void onTunnelConfigChange() {
        super.onTunnelConfigChange();
        handleTunnelChange();
    }

    @Override
    public void onTunnelNetworkChange() {
        super.onTunnelNetworkChange();
        handleTunnelChange();
    }

    private void handleTunnelChange() {
        if (outputLogic != null) {
            synchronizeFromInput();
            outputLogic.alertRetry();
            notifyInputTopologyChanged();
        } else if (inputLogic != null) {
            inputLogic.invalidateOutputs();
            inputLogic.synchronizeSettings();
        }
        var grid = getMainNode().getGrid();
        if (grid != null) {
            var energyService = grid.getService(PatternP2PEnergyGridService.class);
            energyService.topologyChanged();
            grid.getService(PatternP2PTopologyGridService.class).topologyChanged();
            if (isStandardOutput()) {
                energyService.synchronizeOutputGroupMode(this, null);
            }
        }
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);

        // Endpoints can become active after the P2P topology was restored. Do not retain
        // an availability result that was computed while the other endpoint was offline.
        if (outputLogic != null) {
            outputLogic.alertRetry();
            notifyInputAvailabilityChanged();
        } else if (inputLogic != null) {
            inputLogic.invalidateOutputs();
        }

        var grid = getMainNode().getGrid();
        if (grid != null) {
            grid.getService(PatternP2PEnergyGridService.class).demandChanged();
        }
    }

    @Override
    public void onNeighborChanged(net.minecraft.world.level.BlockGetter level,
                                  net.minecraft.core.BlockPos pos, net.minecraft.core.BlockPos neighbor) {
        energyTargetCache = null;
        energyTargetResolved = false;
        energyTargetIsEnergyTunnel = false;
        var grid = getMainNode().getGrid();
        if (grid != null) {
            grid.getService(PatternP2PEnergyGridService.class).demandChanged();
        }
    }

    public void notifyInputAvailabilityChanged() {
        var input = getInput();
        if (input != null && !input.isOutput()) {
            input.getInputLogic().invalidateOutputAvailability();
        }
    }

    private void notifyInputTopologyChanged() {
        var input = getInput();
        if (input != null && !input.isOutput()) {
            input.getInputLogic().invalidateOutputs();
        }
    }

    public void synchronizeFromInput() {
        var input = getInput();
        if (input != null && !input.isOutput() && input.hasConfiguredFrequency()) {
            var settings = input.getInputLogic();
            if (outputLogic != null && outputLogic.isSyncInputSettings()) {
                outputLogic.applyInputSettings(settings.getReturnMode());
            }
        }
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
        if (heldItem.getItem() instanceof ComponentPlacerItem) {
            return true;
        }
        // The base P2P implementation turns a matching cable into an AE2 tunnel type.
        // Pattern P2P tunnels must retain their custom type when wiring their rear face.
        if (!P2PTunnelAttunement.getTunnelPartByTriggerItem(heldItem).isEmpty()) {
            return false;
        }
        if (!(heldItem.getItem() instanceof IMemoryCard memoryCard)) {
            if (hand != InteractionHand.MAIN_HAND) return false;
            if (!isClientSide()) openConfigurationMenu(player);
            return true;
        }
        if (hand == InteractionHand.OFF_HAND) {
            return false;
        }
        if (isClientSide()) {
            return true;
        }

        if (!isOutput() && alternateUse) {
            saveFrequencyToCard(heldItem, memoryCard, player);
        } else if (isOutput() && !alternateUse) {
            loadFrequencyFromCard(heldItem, memoryCard, player);
        } else {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
        }
        return true;
    }

    private void saveFrequencyToCard(ItemStack card, IMemoryCard memoryCard, Player player) {
        if (hasActiveFrequencyTask()) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return;
        }

        short frequency = getFrequency();
        boolean generatedFrequency = frequency == 0;
        var p2p = P2PService.get(grid);
        if (generatedFrequency) {
            frequency = p2p.newFrequency();
        }
        p2p.updateFreq(this, frequency);
        onTunnelConfigChange();

        CompoundTag data = new CompoundTag();
        exportSettings(SettingsFrom.MEMORY_CARD, data);
        memoryCard.setMemoryCardContents(card, getPartItem().asItem().getDescriptionId(), data);
        memoryCard.notifyUser(player, generatedFrequency
                ? MemoryCardMessages.SETTINGS_RESET
                : MemoryCardMessages.SETTINGS_SAVED);
    }

    private void loadFrequencyFromCard(ItemStack card, IMemoryCard memoryCard, Player player) {
        if (hasActiveFrequencyTask()) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return;
        }
        CompoundTag data = memoryCard.getData(card);
        ResourceLocation storedType = ResourceLocation.tryParse(data.getString("p2pType"));
        if (!ModContent.isPatternP2PFrequencySource(storedType) || !ItemData.hasP2PFrequency(data)) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return;
        }
        ItemData.normalizeP2PFrequency(data);
        importSettings(SettingsFrom.MEMORY_CARD, data, player);
        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
    }

    private boolean hasActiveFrequencyTask() {
        if (isTaskActive()) {
            return true;
        }
        if (isOutput() || !hasConfiguredFrequency()) {
            return false;
        }
        var grid = getMainNode().getGrid();
        return grid != null && grid.getService(PatternP2PTopologyGridService.class)
                .getOutputs(getFrequency()).stream().anyMatch(PatternP2PTunnelPart::isTaskActive);
    }

    private GenericInternalInventory findInputReturnInventory() {
        if (!isOperationalTaskEndpoint()) {
            return null;
        }

        var input = getInput();
        if (input == null || input.isOutput() || !input.getMainNode().isActive()) {
            return null;
        }

        Direction side = input.getSide();
        var level = input.getLevel();
        if (side == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        var targetPos = input.getBlockEntity().getBlockPos().relative(side);
        Direction targetSide = side.getOpposite();
        if (returnTargetCache == null
                || returnTargetCache.level() != serverLevel
                || !returnTargetCache.pos().equals(targetPos)
                || returnTargetCache.context() != targetSide) {
            returnTargetCache = ForgeBlockCapabilityCache.create(
                    appeng.capabilities.Capabilities.GENERIC_INTERNAL_INV, serverLevel, targetPos, targetSide);
        }
        return returnTargetCache.getCapability();
    }

    @Override
    public IPartModel getStaticModels() {
        return endpointKind == EndpointKind.INPUT
                ? INPUT_MODELS.getModel(isPowered(), isActive())
                : OUTPUT_MODELS.getModel(isPowered(), isActive());
    }

}
