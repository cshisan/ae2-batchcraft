package cn.ae2bc.part;

import cn.ae2bc.logic.InterfaceReturnFlusher;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.logic.ReturnBatchTracker;
import cn.ae2bc.menu.PatternP2PTunnelMenu;
import cn.ae2bc.pattern.PatternEncodingState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import appeng.api.config.PowerUnits;
import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.core.schedule.ExtractionDeadlineGate;
import cn.ae2bc.core.energy.EnergyEndpoint;
import cn.ae2bc.pattern.InputDirectionCodec;
import cn.ae2bc.pattern.MaterialOutputConfigCodec;
import cn.ae2bc.pattern.MaterialOutputForm;
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.logic.ReturnMode;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.registry.ModContent;
import appeng.me.GridAccessException;
import appeng.parts.p2p.CapabilityP2PTunnelPart;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.util.Hand;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * AE2 8 adapter. AE2 8 has no PatternProvider, so the input endpoint is exposed
 * as an ICraftingMachine through the CableBusTileEntity mixin.
 */
public final class PatternP2PTunnelPart
        extends CapabilityP2PTunnelPart<PatternP2PTunnelPart, IItemHandler>
        implements ICraftingMachine, IGridTickable, EnergyEndpoint {
    private static final String EXTRACTION_ENABLED = "Ae2bcExtractionEnabled";
    private static final String EXTRACTION_INTERVAL = "Ae2bcExtractionInterval";
    private static final String EXTRACTION_AMOUNT = "Ae2bcExtractionAmount";
    private static final String RETURN_BATCH_MODE = "Ae2bcReturnBatchMode";
    private static final String RETURN_BATCH_PATTERN = "Ae2bcReturnBatchPattern";
    private static final String RETURN_BATCH_TASK_COUNT = "Ae2bcReturnBatchTaskCount";
    private static final String RETURN_BATCH_OUTPUTS = "Ae2bcReturnBatchOutputs";
    private static final String RETURN_BATCH_PRIMARY = "Ae2bcReturnBatchPrimary";
    private static final String RETURN_BATCH_EXPECTED_PRIMARY = "Ae2bcReturnBatchExpectedPrimary";
    private final boolean output;
    private boolean extractionEnabled;
    private int extractionInterval = ProductExtractionLimits.DEFAULT_INTERVAL;
    private int extractionAmount = ProductExtractionLimits.DEFAULT_AMOUNT;
    private ReturnMode returnMode = ReturnMode.UNBLOCKED;
    private boolean breakRecovery = true;
    private RedstoneOutputMode redstoneMode = RedstoneOutputMode.SINGLE_TRIGGER;
    private int redstoneStrength = 15;
    private int pulseWidthTicks = 2;
    private int pulsePeriodTicks = 20;
    private boolean syncInputSettings = true;
    private final ExtractionDeadlineGate extractionDeadline = new ExtractionDeadlineGate();
    private final ExtractionDeadlineGate returnRecoveryDeadline = new ExtractionDeadlineGate();
    private ItemStack blockedReturnProbe = ItemStack.EMPTY;
    private int roundRobinCursor;
    private final ReturnBatchTracker<StackKey, StackKey> returnBatch = new ReturnBatchTracker<StackKey, StackKey>();
    private static final net.minecraft.util.ResourceLocation INPUT_MODEL_ID =
            new net.minecraft.util.ResourceLocation("ae2_batchcraft", "part/p2p/pattern_p2p_tunnel_input");
    private static final net.minecraft.util.ResourceLocation OUTPUT_MODEL_ID =
            new net.minecraft.util.ResourceLocation("ae2_batchcraft", "part/p2p/pattern_p2p_tunnel_output");
    private static final net.minecraft.util.ResourceLocation STATUS_OFF_MODEL_ID =
            new net.minecraft.util.ResourceLocation("appliedenergistics2", "part/p2p/p2p_tunnel_status_off");
    private static final net.minecraft.util.ResourceLocation STATUS_ON_MODEL_ID =
            new net.minecraft.util.ResourceLocation("appliedenergistics2", "part/p2p/p2p_tunnel_status_on");
    private static final net.minecraft.util.ResourceLocation STATUS_HAS_CHANNEL_MODEL_ID =
            new net.minecraft.util.ResourceLocation("appliedenergistics2", "part/p2p/p2p_tunnel_status_has_channel");
    private static final net.minecraft.util.ResourceLocation FREQUENCY_MODEL_ID =
            new net.minecraft.util.ResourceLocation("appliedenergistics2", "part/p2p/p2p_tunnel_frequency");
    private static final IPartModel INPUT_MODEL_OFF = new PartModel(
            STATUS_OFF_MODEL_ID, FREQUENCY_MODEL_ID, INPUT_MODEL_ID);
    private static final IPartModel INPUT_MODEL_ON = new PartModel(
            STATUS_ON_MODEL_ID, FREQUENCY_MODEL_ID, INPUT_MODEL_ID);
    private static final IPartModel INPUT_MODEL_HAS_CHANNEL = new PartModel(
            STATUS_HAS_CHANNEL_MODEL_ID, FREQUENCY_MODEL_ID, INPUT_MODEL_ID);
    private static final IPartModel OUTPUT_MODEL_OFF = new PartModel(
            STATUS_OFF_MODEL_ID, FREQUENCY_MODEL_ID, OUTPUT_MODEL_ID);
    private static final IPartModel OUTPUT_MODEL_ON = new PartModel(
            STATUS_ON_MODEL_ID, FREQUENCY_MODEL_ID, OUTPUT_MODEL_ID);
    private static final IPartModel OUTPUT_MODEL_HAS_CHANNEL = new PartModel(
            STATUS_HAS_CHANNEL_MODEL_ID, FREQUENCY_MODEL_ID, OUTPUT_MODEL_ID);

    @PartModels
    public static List<IPartModel> getModels() {
        return Arrays.asList(INPUT_MODEL_OFF, INPUT_MODEL_ON, INPUT_MODEL_HAS_CHANNEL,
                OUTPUT_MODEL_OFF, OUTPUT_MODEL_ON, OUTPUT_MODEL_HAS_CHANNEL);
    }

    @Override
    public IPartModel getStaticModels() {
        IPartModel off = output ? OUTPUT_MODEL_OFF : INPUT_MODEL_OFF;
        IPartModel on = output ? OUTPUT_MODEL_ON : INPUT_MODEL_ON;
        IPartModel hasChannel = output ? OUTPUT_MODEL_HAS_CHANNEL : INPUT_MODEL_HAS_CHANNEL;
        if (isPowered() && isActive()) {
            return hasChannel;
        }
        return isPowered() ? on : off;
    }
    public PatternP2PTunnelPart(ItemStack stack, boolean output) {
        super(stack, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        this.output = output;
        if (output) {
            getProxy().setFlags();
        }
        this.inputHandler = new EndpointHandler();
        this.outputHandler = new EndpointHandler();
        this.emptyHandler = new EmptyHandler();
    }

    @Override
    public void getBoxes(IPartCollisionHelper helper) {
        helper.addBox(2, 2, 2, 14, 14, 14);
    }

    @Override
    public boolean isOutput() {
        return output;
    }

    @Override
    public float getPowerDrainPerTick() {
        return 1.0f;
    }

    @Override
    public boolean onPartActivate(PlayerEntity player, Hand hand, Vector3d hitPos) {
        if (handleMemoryCard(player, hand, false)) {
            return true;
        }
        // AE2 otherwise attunes this custom endpoint into a built-in P2P tunnel.
        if (appeng.core.Api.instance().registries().p2pTunnel()
                .getTunnelTypeByItem(player.getItemInHand(hand)) != null) {
            return false;
        }
        if (super.onPartActivate(player, hand, hitPos)) {
            return true;
        }
        if (!player.level.isClientSide && player instanceof ServerPlayerEntity) {
            NetworkHooks.openGui((ServerPlayerEntity) player, new SimpleNamedContainerProvider(
                    (id, inventory, ignored) -> new PatternP2PTunnelMenu(id, inventory, this),
                    new TranslationTextComponent(output
                            ? "item.ae2_batchcraft.pattern_p2p_tunnel_output"
                            : "item.ae2_batchcraft.pattern_p2p_tunnel_input")), buffer -> {
                        buffer.writeBlockPos(getTile().getBlockPos());
                        buffer.writeByte(getSide().getFacing().ordinal());
                        buffer.writeBoolean(output);
                        buffer.writeBoolean(extractionEnabled);
                        writeSettings(buffer, getUnitSettings());
                        buffer.writeBoolean(syncInputSettings);
                    });
        }
        return true;
    }

    @Override
    public boolean onPartShiftActivate(PlayerEntity player, Hand hand, Vector3d hitPos) {
        return handleMemoryCard(player, hand, true) || super.onPartShiftActivate(player, hand, hitPos);
    }

    /**
     * AE2's default card load removes and recreates the part from the saved input item.
     * Input and output endpoints are separate items here, so load the frequency in place.
     */
    private boolean handleMemoryCard(PlayerEntity player, Hand hand, boolean save) {
        ItemStack held = player.getItemInHand(hand);
        if (hand != Hand.MAIN_HAND || !(held.getItem() instanceof IMemoryCard)) {
            return false;
        }
        if (player.level.isClientSide) {
            return true;
        }

        IMemoryCard card = (IMemoryCard) held.getItem();
        try {
            if (save && !output) {
                short frequency = getFrequency();
                boolean generated = frequency == 0;
                if (generated) {
                    frequency = getProxy().getP2P().newFrequency();
                }
                getProxy().getP2P().updateFreq(this, frequency);
                CompoundNBT data = ModContent.PATTERN_P2P_INPUT.get().getDefaultInstance()
                        .save(new CompoundNBT());
                data.putShort("freq", frequency);
                card.setMemoryCardContents(held,
                        "item.ae2_batchcraft.pattern_p2p_tunnel_input", data);
                card.notifyUser(player, generated
                        ? MemoryCardMessages.SETTINGS_RESET : MemoryCardMessages.SETTINGS_SAVED);
                return true;
            }
            if (!save && output) {
                CompoundNBT data = card.getData(held);
                if (!data.contains("freq") || !isSupportedMemoryCardSource(data)) {
                    card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
                    return true;
                }
                getProxy().getP2P().updateFreq(this, data.getShort("freq"));
                onTunnelNetworkChange();
                getHost().markForSave();
                card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
                return true;
            }
        } catch (GridAccessException ignored) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }

        card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
        return true;
    }

    private static boolean isSupportedMemoryCardSource(CompoundNBT data) {
        if (data.hasUUID("PatternP2PUnitId")) {
            return true;
        }
        ItemStack source = ItemStack.of(data);
        if (source.isEmpty() || source.getItem() == ModContent.PATTERN_P2P_INPUT.get()) {
            return !source.isEmpty();
        }
        for (net.minecraftforge.fml.RegistryObject<cn.ae2bc.item.PatternP2PUnitManagerItem<PatternP2PUnitManagerPart>> item
                : ModContent.UNIT_MANAGERS.values()) {
            if (source.getItem() == item.get()) {
                return true;
            }
        }
        return false;
    }

    public PatternP2PUnitSettings getUnitSettings() {
        return new PatternP2PUnitSettings(returnMode, breakRecovery, extractionInterval,
                extractionAmount, redstoneMode, redstoneStrength, pulseWidthTicks, pulsePeriodTicks);
    }

    public void setInputSettings(boolean enabled, PatternP2PUnitSettings settings) {
        if (output || settings == null) return;
        extractionEnabled = enabled;
        applySettings(settings);
        extractionDeadline.wake();
        getHost().markForSave();
        getHost().markForUpdate();
        synchronizeUnitManagers();
        wakeOutputs();
    }

    private void synchronizeUnitManagers() {
        if (output || getGridNode() == null || getGridNode().getGrid() == null) return;
        for (IGridNode node : getGridNode().getGrid().getNodes()) {
            Object machine = node.getMachine();
            if (machine instanceof PatternP2PUnitManagerPart) {
                PatternP2PUnitManagerPart manager = (PatternP2PUnitManagerPart) machine;
                if (manager.getFrequency() == getFrequency()) {
                    manager.applyMainConfiguration(getUnitSettings());
                }
            }
        }
    }

    public void setOutputSettings(ReturnMode mode, boolean sync) {
        if (!output) return;
        returnMode = mode == null ? ReturnMode.UNBLOCKED : mode;
        syncInputSettings = sync;
        getHost().markForSave();
        getHost().markForUpdate();
    }

    public boolean isSyncInputSettings() { return syncInputSettings; }
    public ReturnMode getReturnMode() {
        PatternP2PTunnelPart input = output && syncInputSettings ? getInput() : null;
        return input == null ? returnMode : input.returnMode;
    }

    public void resetTaskState() {
        if (output) {
            clearOutputReturnBatch();
            PatternP2PTunnelPart input = getInput();
            if (input != null) input.resetTaskState();
            return;
        }
        for (PatternP2PTunnelPart endpoint : outputs()) {
            endpoint.clearOutputReturnBatch();
        }
        if (getGridNode() == null || getGridNode().getGrid() == null) return;
        for (IGridNode node : getGridNode().getGrid().getNodes()) {
            Object machine = node.getMachine();
            if (machine instanceof PatternP2PUnitManagerPart
                    && ((PatternP2PUnitManagerPart) machine).getFrequency() == getFrequency()) {
                ((PatternP2PUnitManagerPart) machine).resetTaskState();
            }
        }
    }

    private void applySettings(PatternP2PUnitSettings settings) {
        returnMode = settings.getReturnMode();
        breakRecovery = settings.isBreakRecovery();
        extractionInterval = settings.getExtractionInterval();
        extractionAmount = settings.getExtractionAmount();
        redstoneMode = settings.getRedstoneMode();
        redstoneStrength = settings.getRedstoneStrength();
        pulseWidthTicks = settings.getPulseWidthTicks();
        pulsePeriodTicks = settings.getPulsePeriodTicks();
    }

    private static void writeSettings(net.minecraft.network.PacketBuffer buffer,
            PatternP2PUnitSettings settings) {
        buffer.writeByte(settings.getReturnMode().getId());
        buffer.writeBoolean(settings.isBreakRecovery());
        buffer.writeInt(settings.getExtractionInterval());
        buffer.writeInt(settings.getExtractionAmount());
        buffer.writeByte(settings.getRedstoneMode().getId());
        buffer.writeByte(settings.getRedstoneStrength());
        buffer.writeInt(settings.getPulseWidthTicks());
        buffer.writeInt(settings.getPulsePeriodTicks());
    }

    public void setExtractionSettings(boolean enabled, int interval, int amount) {
        extractionEnabled = enabled;
        extractionInterval = ProductExtractionLimits.clampInterval(interval);
        extractionAmount = ProductExtractionLimits.clampAmount(amount);
        extractionDeadline.wake();
        getHost().markForSave();
        getHost().markForUpdate();
        if (!output) {
            synchronizeUnitManagers();
            wakeOutputs();
        }
    }

    public boolean isExtractionEnabled() {
        return extractionEnabled;
    }

    public int getExtractionInterval() {
        return extractionInterval;
    }

    public int getExtractionAmount() {
        return extractionAmount;
    }

    public boolean isEnergyOutputAvailable() {
        return isOutput() && isActive();
    }

    @Override public boolean isEnergyEndpointAvailable() { return isEnergyOutputAvailable(); }

    @Override public int receiveExternalEnergy(int maxReceive, boolean simulate) {
        if (!isEnergyOutputAvailable() || maxReceive <= 0) {
            return 0;
        }
        Direction face = getSide().getFacing();
        TileEntity tile = getTile().getLevel().getBlockEntity(getTile().getBlockPos().relative(face));
        if (tile == null) {
            return 0;
        }
        IEnergyStorage target = tile.getCapability(CapabilityEnergy.ENERGY, face.getOpposite()).orElse(null);
        return target == null || !target.canReceive() ? 0 : target.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public void readFromNBT(CompoundNBT data) {
        super.readFromNBT(data);
        extractionEnabled = data.getBoolean(EXTRACTION_ENABLED);
        extractionInterval = data.contains(EXTRACTION_INTERVAL)
                ? ProductExtractionLimits.clampInterval(data.getInt(EXTRACTION_INTERVAL))
                : ProductExtractionLimits.DEFAULT_INTERVAL;
        extractionAmount = data.contains(EXTRACTION_AMOUNT)
                ? ProductExtractionLimits.clampAmount(data.getInt(EXTRACTION_AMOUNT))
                : ProductExtractionLimits.DEFAULT_AMOUNT;
        returnMode = data.contains("Ae2bcReturnMode")
                ? ReturnMode.fromId(data.getInt("Ae2bcReturnMode")) : ReturnMode.UNBLOCKED;
        breakRecovery = !data.contains("Ae2bcBreakRecovery") || data.getBoolean("Ae2bcBreakRecovery");
        redstoneMode = RedstoneOutputMode.fromId(data.getInt("Ae2bcRedstoneMode"));
        redstoneStrength = data.contains("Ae2bcRedstoneStrength")
                ? Math.max(0, Math.min(15, data.getInt("Ae2bcRedstoneStrength"))) : 15;
        pulsePeriodTicks = data.contains("Ae2bcPulsePeriod")
                ? Math.max(1, data.getInt("Ae2bcPulsePeriod")) : 20;
        pulseWidthTicks = data.contains("Ae2bcPulseWidth")
                ? Math.max(1, Math.min(pulsePeriodTicks, data.getInt("Ae2bcPulseWidth"))) : 2;
        syncInputSettings = !data.contains("Ae2bcSyncInputSettings")
                || data.getBoolean("Ae2bcSyncInputSettings");
        roundRobinCursor = data.getInt("Ae2bcRoundRobinCursor");
        readOutputReturnBatch(data);
    }

    @Override
    public void writeToNBT(CompoundNBT data) {
        super.writeToNBT(data);
        data.putBoolean(EXTRACTION_ENABLED, extractionEnabled);
        data.putInt(EXTRACTION_INTERVAL, extractionInterval);
        data.putInt(EXTRACTION_AMOUNT, extractionAmount);
        data.putInt("Ae2bcReturnMode", returnMode.getId());
        data.putBoolean("Ae2bcBreakRecovery", breakRecovery);
        data.putInt("Ae2bcRedstoneMode", redstoneMode.getId());
        data.putInt("Ae2bcRedstoneStrength", redstoneStrength);
        data.putInt("Ae2bcPulseWidth", pulseWidthTicks);
        data.putInt("Ae2bcPulsePeriod", pulsePeriodTicks);
        data.putBoolean("Ae2bcSyncInputSettings", syncInputSettings);
        data.putInt("Ae2bcRoundRobinCursor", roundRobinCursor);
        writeOutputReturnBatch(data);
    }

    @Nonnull
    @Override
    public TickingRequest getTickingRequest(@Nonnull IGridNode node) {
        return new TickingRequest(1, ProductExtractionLimits.MAX_INTERVAL, false, false);
    }

    @Nonnull
    @Override
    public TickRateModulation tickingRequest(@Nonnull IGridNode node, int ticksSinceLastCall) {
        if (!isActive()) {
            return TickRateModulation.SLOWER;
        }
        if (!isOutput()) {
            return probeBlockedReturn() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
        PatternP2PTunnelPart input = getInput();
        if (input == null || !input.extractionEnabled) {
            return TickRateModulation.SLOWER;
        }
        long now = getTile().getLevel().getGameTime();
        if (!extractionDeadline.isDue(now, input.extractionInterval)) {
            return TickRateModulation.SAME;
        }
        return extractFromAdjacent(input.extractionAmount) > 0
                ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    @Override
    public boolean acceptsPlans() {
        return !isOutput() && isActive() && (hasOutputEndpoint() || hasUnitManagerForFrequency());
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails details, CraftingInventory table, Direction ejectionDirection) {
        if (!acceptsPlans() || details == null || table == null) {
            return false;
        }
        List<PatternP2PTunnelPart> outputs = outputs();
        List<PatternP2PUnitManagerPart> managers = unitManagersForFrequency();
        int endpointCount = outputs.size() + managers.size();
        if (endpointCount == 0) {
            return false;
        }
        long[] packed = PatternEncodingState.read(details.getPattern());
        long[] directions = MaterialOutputConfigCodec.directionWords(packed);
        ManagerDispatch managerDispatch = managers.isEmpty()
                ? null : createManagerDispatch(details, table, packed);
        int inputCount = countInputs(table);
        final int outputCount = outputs.size();
        int acceptedIndex = cn.ae2bc.core.dispatch.RoundRobinSelector.select(
                roundRobinCursor, endpointCount, candidateIndex -> {
            if (candidateIndex < outputCount) {
                return tryAcceptOutput(outputs.get(candidateIndex), details, table, directions);
            }
            return managerDispatch != null && tryAcceptManager(
                    managers.get(candidateIndex - outputCount), managerDispatch, table);
        });
        if (acceptedIndex < 0) {
            return false;
        }
        int moved = inputCount - countInputs(table);
        if (moved <= 0) {
            return false;
        }
        roundRobinCursor = cn.ae2bc.core.dispatch.RoundRobinPolicy.advance(
                acceptedIndex, endpointCount);
        getHost().markForSave();
        queueTunnelDrain(PowerUnits.RF, moved);
        return true;
    }

    private ManagerDispatch createManagerDispatch(ICraftingPatternDetails details,
            CraftingInventory table, long[] packed) {
        List<ItemStack> inputs = new ArrayList<ItemStack>();
        List<UnitPortType> targetTypes = new ArrayList<UnitPortType>();
        long[] forms = MaterialOutputConfigCodec.formWords(packed);
        for (int slot = 0; slot < table.getContainerSize(); slot++) {
            ItemStack stack = table.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            MaterialOutputForm form = MaterialOutputForm.fromId(
                    MaterialOutputConfigCodec.getOutputFormId(forms, slot));
            if (!form.supports(stack)) {
                return null;
            }
            inputs.add(stack.copy());
            targetTypes.add(UnitPortType.forOutputFormId(form.getId()));
        }
        if (inputs.isEmpty()) {
            return null;
        }
        ItemStack primaryOutput = ItemStack.EMPTY;
        long primaryAmount = 0;
        List<ItemStack> outputIdentities = new ArrayList<ItemStack>();
        List<IAEItemStack> declaredOutputs = details.getOutputs();
        if (declaredOutputs != null && !declaredOutputs.isEmpty()) {
            for (IAEItemStack output : declaredOutputs) {
                if (output != null) {
                    outputIdentities.add(output.createItemStack());
                }
            }
            if (declaredOutputs.get(0) != null) {
                primaryOutput = declaredOutputs.get(0).createItemStack();
                primaryAmount = declaredOutputs.get(0).getStackSize();
            }
        }
        return new ManagerDispatch(inputs, targetTypes, primaryOutput, primaryAmount, outputIdentities);
    }

    private boolean tryAcceptManager(PatternP2PUnitManagerPart manager, ManagerDispatch dispatch,
            CraftingInventory table) {
        for (int i = 0; i < dispatch.inputs.size(); i++) {
            if (!manager.canAcceptInput(dispatch.inputs.get(i), dispatch.targetTypes.get(i))) {
                return false;
            }
        }
        if (!manager.acceptInputs(dispatch.inputs, dispatch.targetTypes, dispatch.primaryOutput,
                dispatch.primaryAmount, dispatch.outputIdentities)) {
            return false;
        }
        clearInputs(table);
        return true;
    }

    private boolean tryAcceptOutput(PatternP2PTunnelPart output, ICraftingPatternDetails details,
            CraftingInventory table, long[] directions) {
        if (!output.isOutput() || !output.isActive() || !output.canAcceptOutputTask(details)) {
            return false;
        }
        // Probe the complete task before committing any slot to this one machine.
        for (int slot = 0; slot < table.getContainerSize(); slot++) {
            ItemStack stack = table.getItem(slot);
            if (!stack.isEmpty() && !output.adjacentInsert(stack, directions, slot, true).isEmpty()) {
                return false;
            }
        }
        OutputReturnMetadata metadata = outputReturnMetadata(details);
        if (!output.beginOutputTask(details, metadata)) {
            return false;
        }
        int moved = 0;
        for (int slot = 0; slot < table.getContainerSize(); slot++) {
            ItemStack stack = table.getItem(slot);
            if (!stack.isEmpty()) {
                ItemStack remainder = output.adjacentInsert(stack, directions, slot, false);
                moved += stack.getCount() - remainder.getCount();
                table.setItem(slot, remainder);
            }
        }
        if (moved <= 0) {
            output.rollbackOutputTask(metadata.primaryAmount);
            return false;
        }
        output.getHost().markForSave();
        return true;
    }

    private boolean canAcceptOutputTask(ICraftingPatternDetails details) {
        OutputReturnMetadata metadata = outputReturnMetadata(details);
        return returnBatch.canAccept(stackKey(details.getPattern()),
                metadata.outputs, metadata.primaryKey, metadata.primaryAmount);
    }

    private boolean beginOutputTask(ICraftingPatternDetails details, OutputReturnMetadata metadata) {
        return returnBatch.begin(stackKey(details.getPattern()),
                metadata.outputs, metadata.primaryKey, metadata.primaryAmount);
    }

    private void rollbackOutputTask(long primaryAmount) {
        returnBatch.rollback(primaryAmount);
        getHost().markForSave();
    }

    private void clearOutputReturnBatch() {
        if (!returnBatch.isActive()) {
            return;
        }
        returnBatch.clear();
        getHost().markForSave();
    }

    private ItemStack returnOutputProduct(ItemStack stack, boolean simulate) {
        if (!output || stack == null || stack.isEmpty()) {
            return stack;
        }
        long allowed = returnBatch.filter(stackKey(stack), stack.getCount(), getReturnMode());
        if (allowed <= 0) {
            return stack;
        }
        PatternP2PTunnelPart input = getInput();
        if (input == null) {
            return stack;
        }
        ItemStack offered = stack.copy();
        offered.setCount((int) Math.min(Integer.MAX_VALUE, allowed));
        ItemStack remainder = input.returnToAdjacent(offered, simulate);
        int accepted = offered.getCount() - remainder.getCount();
        if (!simulate && accepted > 0) {
            returnBatch.returned(stackKey(stack), accepted);
            getHost().markForSave();
        }
        if (accepted <= 0) {
            return stack;
        }
        ItemStack result = stack.copy();
        result.shrink(accepted);
        return result.isEmpty() ? ItemStack.EMPTY : result;
    }

    private static OutputReturnMetadata outputReturnMetadata(ICraftingPatternDetails details) {
        Map<StackKey, Long> outputs = new LinkedHashMap<StackKey, Long>();
        StackKey primaryKey = null;
        long primaryAmount = 0;
        List<IAEItemStack> declared = details.getOutputs();
        if (declared != null) {
            for (IAEItemStack output : declared) {
                if (output == null || output.getStackSize() <= 0) {
                    continue;
                }
                StackKey key = stackKey(output.createItemStack());
                if (key == null) {
                    continue;
                }
                Long previous = outputs.get(key);
                long amount = output.getStackSize();
                outputs.put(key, previous == null ? amount : saturatingAdd(previous.longValue(), amount));
                if (primaryKey == null) {
                    primaryKey = key;
                    primaryAmount = amount;
                }
            }
        }
        return new OutputReturnMetadata(outputs, primaryKey, primaryAmount);
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static StackKey stackKey(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : new StackKey(stack);
    }

    private void readOutputReturnBatch(CompoundNBT data) {
        returnBatch.clear();
        if (!data.contains(RETURN_BATCH_TASK_COUNT)) {
            return;
        }
        Map<StackKey, Long> outputs = new LinkedHashMap<StackKey, Long>();
        ListNBT list = data.getList(RETURN_BATCH_OUTPUTS, 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundNBT entry = list.getCompound(i);
            StackKey key = stackKey(ItemStack.of(entry.getCompound("Stack")));
            long amount = entry.getLong("Amount");
            if (key != null && amount > 0) {
                outputs.put(key, amount);
            }
        }
        returnBatch.load(stackKey(ItemStack.of(data.getCompound(RETURN_BATCH_PATTERN))),
                data.getInt(RETURN_BATCH_TASK_COUNT), outputs,
                stackKey(ItemStack.of(data.getCompound(RETURN_BATCH_PRIMARY))),
                data.getLong(RETURN_BATCH_EXPECTED_PRIMARY));
    }

    private void writeOutputReturnBatch(CompoundNBT data) {
        if (!returnBatch.isActive()) {
            data.remove(RETURN_BATCH_MODE);
            data.remove(RETURN_BATCH_PATTERN);
            data.remove(RETURN_BATCH_TASK_COUNT);
            data.remove(RETURN_BATCH_OUTPUTS);
            data.remove(RETURN_BATCH_PRIMARY);
            data.remove(RETURN_BATCH_EXPECTED_PRIMARY);
            data.remove("Ae2bcReturnBatchExpectedPrimaries");
            return;
        }
        data.remove(RETURN_BATCH_MODE);
        data.put(RETURN_BATCH_PATTERN, returnBatch.getPattern().toStack().save(new CompoundNBT()));
        data.putInt(RETURN_BATCH_TASK_COUNT, returnBatch.getTaskCount());
        ListNBT outputs = new ListNBT();
        for (Map.Entry<StackKey, Long> output : returnBatch.getDeclaredOutputs().entrySet()) {
            CompoundNBT entry = new CompoundNBT();
            entry.put("Stack", output.getKey().toStack().save(new CompoundNBT()));
            entry.putLong("Amount", output.getValue().longValue());
            outputs.add(entry);
        }
        data.put(RETURN_BATCH_OUTPUTS, outputs);
        data.put(RETURN_BATCH_PRIMARY, returnBatch.getPrimaryKey().toStack().save(new CompoundNBT()));
        data.putLong(RETURN_BATCH_EXPECTED_PRIMARY, returnBatch.getExpectedPrimary());
        data.remove("Ae2bcReturnBatchExpectedPrimaries");
    }

    private static int countInputs(CraftingInventory table) {
        int count = 0;
        for (int slot = 0; slot < table.getContainerSize(); slot++) {
            count += table.getItem(slot).getCount();
        }
        return count;
    }

    private static void clearInputs(CraftingInventory table) {
        for (int slot = 0; slot < table.getContainerSize(); slot++) {
            table.setItem(slot, ItemStack.EMPTY);
        }
    }

    private boolean hasOutputEndpoint() {
        try {
            return !getOutputs().isEmpty();
        } catch (GridAccessException ignored) {
            return false;
        }
    }

    private boolean hasUnitManagerForFrequency() {
        return !unitManagersForFrequency().isEmpty();
    }

    private List<PatternP2PUnitManagerPart> unitManagersForFrequency() {
        if (getTile().getLevel() == null || getGridNode() == null) {
            return java.util.Collections.emptyList();
        }
        return PatternP2PTopologyGridService.findByFrequency(getGridNode(), getFrequency(),
                getTile().getLevel().getGameTime());
    }

    private List<PatternP2PTunnelPart> outputs() {
        List<PatternP2PTunnelPart> result = new ArrayList<PatternP2PTunnelPart>();
        try {
            for (PatternP2PTunnelPart part : getOutputs()) {
                result.add(part);
            }
        } catch (GridAccessException ignored) {
        }
        return result;
    }

    private ItemStack adjacentInsert(ItemStack source, long[] directions, int inputSlot, boolean simulate) {
        int direction = InputDirectionCodec.getDirectionOrdinal(directions, inputSlot);
        Direction outputSide = getSide().getFacing();
        TileEntity tile = getTile().getLevel().getBlockEntity(getTile().getBlockPos().relative(outputSide));
        if (tile == null) {
            return source;
        }
        Direction targetFace = direction < 0 ? outputSide.getOpposite() : Direction.values()[direction];
        IItemHandler handler = tile.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, targetFace).orElse(emptyHandler);
        return ItemHandlerHelper.insertItem(handler, source, simulate);
    }

    private static final class ManagerDispatch {
        private final List<ItemStack> inputs;
        private final List<UnitPortType> targetTypes;
        private final ItemStack primaryOutput;
        private final long primaryAmount;
        private final List<ItemStack> outputIdentities;

        private ManagerDispatch(List<ItemStack> inputs, List<UnitPortType> targetTypes,
                ItemStack primaryOutput, long primaryAmount, List<ItemStack> outputIdentities) {
            this.inputs = inputs;
            this.targetTypes = targetTypes;
            this.primaryOutput = primaryOutput;
            this.primaryAmount = primaryAmount;
            this.outputIdentities = outputIdentities;
        }
    }

    private static final class OutputReturnMetadata {
        private final Map<StackKey, Long> outputs;
        private final StackKey primaryKey;
        private final long primaryAmount;

        private OutputReturnMetadata(Map<StackKey, Long> outputs, StackKey primaryKey, long primaryAmount) {
            this.outputs = outputs;
            this.primaryKey = primaryKey;
            this.primaryAmount = primaryAmount;
        }
    }

    private static final class StackKey {
        private final CompoundNBT serialized;

        private StackKey(ItemStack stack) {
            ItemStack identity = stack.copy();
            identity.setCount(1);
            serialized = identity.save(new CompoundNBT());
        }

        private ItemStack toStack() {
            return ItemStack.of(serialized.copy());
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof StackKey
                    && serialized.equals(((StackKey) other).serialized);
        }

        @Override
        public int hashCode() {
            return serialized.hashCode();
        }
    }

    private final class EndpointHandler implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Nonnull @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Nonnull @Override public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!isOutput()) {
                return stack;
            }
            return returnOutputProduct(stack, simulate);
        }
        @Nonnull @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, @Nonnull ItemStack stack) { return true; }
    }

    ItemStack returnToAdjacent(ItemStack stack, boolean simulate) {
        Direction face = getSide().getFacing();
        TileEntity tile = getTile().getLevel().getBlockEntity(getTile().getBlockPos().relative(face));
        if (tile == null) {
            return stack;
        }
        IItemHandler handler = tile.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite()).orElse(emptyHandler);
        ItemStack remainder = InterfaceReturnFlusher.insert(
                tile, face.getOpposite(), handler, stack, simulate);
        if (!remainder.isEmpty()) {
            blockedReturnProbe = remainder.copy();
            blockedReturnProbe.setCount(1);
            returnRecoveryDeadline.wake();
            wakeSelf();
        }
        if (!simulate) {
            queueTunnelDrain(PowerUnits.RF, stack.getCount() - remainder.getCount());
        }
        return remainder;
    }

    private boolean probeBlockedReturn() {
        if (blockedReturnProbe.isEmpty()) {
            return false;
        }
        long now = getTile().getLevel().getGameTime();
        if (!returnRecoveryDeadline.isDue(now, 20)) {
            return true;
        }
        ItemStack remainder = ItemHandlerHelper.insertItem(adjacentHandler(), blockedReturnProbe, true);
        if (!remainder.isEmpty()) {
            return true;
        }
        blockedReturnProbe = ItemStack.EMPTY;
        wakeOutputs();
        return false;
    }

    private void wakeSelf() {
        try {
            getProxy().getTick().wakeDevice(getGridNode());
        } catch (GridAccessException ignored) {
        }
    }

    private void wakeOutputs() {
        for (PatternP2PTunnelPart output : outputs()) {
            output.extractionDeadline.wake();
            try {
                getProxy().getTick().wakeDevice(output.getGridNode());
            } catch (GridAccessException ignored) {
                // Other outputs on the frequency may still be available.
            }
        }
    }

    private IItemHandler adjacentHandler() {
        Direction face = getSide().getFacing();
        TileEntity tile = getTile().getLevel().getBlockEntity(getTile().getBlockPos().relative(face));
        return tile == null ? emptyHandler : tile.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite()).orElse(emptyHandler);
    }

    private int extractFromAdjacent(int amount) {
        IItemHandler source = adjacentHandler();
        PatternP2PTunnelPart input = getInput();
        if (input == null) {
            return 0;
        }
        int moved = 0;
        for (int slot = 0; slot < source.getSlots() && moved < amount; slot++) {
            ItemStack candidate = source.extractItem(slot, amount - moved, true);
            if (candidate.isEmpty()) {
                continue;
            }
            ItemStack returnRemainder = returnOutputProduct(candidate, true);
            int accepted = candidate.getCount() - returnRemainder.getCount();
            if (accepted <= 0) {
                continue;
            }
            ItemStack extracted = source.extractItem(slot, accepted, false);
            ItemStack unexpectedRemainder = returnOutputProduct(extracted, false);
            moved += extracted.getCount() - unexpectedRemainder.getCount();
            if (!unexpectedRemainder.isEmpty()) {
                ItemHandlerHelper.insertItem(source, unexpectedRemainder, false);
            }
        }
        return moved;
    }

    private static final class EmptyHandler implements IItemHandler {
        @Override public int getSlots() { return 0; }
        @Nonnull @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Nonnull @Override public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) { return stack; }
        @Nonnull @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 0; }
        @Override public boolean isItemValid(int slot, @Nonnull ItemStack stack) { return false; }
    }
}
