package cn.ae2bc.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.logic.EnergyDistributionMode;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.logic.ReturnMode;
import cn.ae2bc.logic.PatternP2PUnitConfiguration;
import cn.ae2bc.logic.ProductExtractionSettings;
import cn.ae2bc.core.unit.TransferPortOutputMode;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

public final class PatternP2PUnitManagerMenu extends AEBaseMenu {
    private static final String SET_SYNC = "setSync";
    private static final String SET_RETURN_MODE = "setReturnMode";
    private static final String SET_BREAK_RECOVERY = "setBreakRecovery";
    private static final String SET_REDSTONE_STRENGTH = "setRedstoneStrength";
    private static final String SET_REDSTONE_MODE = "setRedstoneMode";
    private static final String SET_PULSE_WIDTH = "setPulseWidth";
    private static final String SET_PULSE_PERIOD = "setPulsePeriod";
    private static final String SET_ENERGY_DISTRIBUTION_MODE = "setEnergyDistributionMode";
    private static final String SET_PRODUCT_EXTRACTION_INTERVAL = "setProductExtractionInterval";
    private static final String SET_PRODUCT_EXTRACTION_AMOUNT = "setProductExtractionAmount";
    private static final String RESET_TASK_STATE = "resetTaskState";
    private static final String SET_TRANSFER_PORT_OUTPUT_MODE = "setTransferPortOutputMode";
    private static final String SET_OUTPUT_SLOT_SHARING_MODE = "setOutputSlotSharingMode";

    public static final MenuType<PatternP2PUnitManagerMenu> TYPE = MenuTypeBuilder
            .create(PatternP2PUnitManagerMenu::new, PatternP2PUnitManagerPart.class)
            .withMenuTitle(part -> part.getPartItem().asItem().getDescription())
            .build("pattern_p2p_unit_manager");

    private final PatternP2PUnitManagerPart host;
    @GuiSync(0) public boolean syncMain = true;
    @GuiSync(1) public ReturnMode returnMode = ReturnMode.UNBLOCKED;
    @GuiSync(2) public boolean breakRecovery = true;
    @GuiSync(3) public int redstoneStrength = PatternP2PUnitConfiguration.DEFAULT_REDSTONE_STRENGTH;
    @GuiSync(4) public RedstoneOutputMode redstoneMode = RedstoneOutputMode.SINGLE_TRIGGER;
    @GuiSync(5) public int pulseWidth = PatternP2PUnitConfiguration.DEFAULT_PULSE_WIDTH;
    @GuiSync(6) public int pulsePeriod = PatternP2PUnitConfiguration.DEFAULT_PULSE_PERIOD;
    @GuiSync(7) public EnergyDistributionMode energyDistributionMode = EnergyDistributionMode.EVEN;
    @GuiSync(8) public int productExtractionInterval = ProductExtractionSettings.DEFAULT_INTERVAL;
    @GuiSync(9) public int productExtractionAmount = ProductExtractionSettings.DEFAULT_AMOUNT;
    @GuiSync(10) public TransferPortOutputMode transferPortOutputMode = TransferPortOutputMode.NORMAL;
    @GuiSync(11) public OutputSlotSharingMode outputSlotSharingMode = OutputSlotSharingMode.DISABLED;

    public PatternP2PUnitManagerMenu(int id, Inventory inventory, PatternP2PUnitManagerPart host) {
        super(TYPE, id, inventory, host);
        this.host = host;
        registerClientAction(SET_SYNC, Boolean.class, value -> {
            if (isServerSide() && value != null) {
                host.getLogic().setSyncMainConfiguration(value);
            }
        });
        registerClientAction(SET_RETURN_MODE, ReturnMode.class, value -> update(configuration().withReturnMode(value)));
        registerClientAction(SET_BREAK_RECOVERY, Boolean.class, value -> update(configuration().withBreakRecovery(value)));
        registerClientAction(SET_REDSTONE_STRENGTH, Integer.class, value -> update(
                configuration().withRedstone(value, redstoneMode, pulseWidth, pulsePeriod)));
        registerClientAction(SET_REDSTONE_MODE, RedstoneOutputMode.class, value -> update(
                configuration().withRedstone(redstoneStrength, value, pulseWidth, pulsePeriod)));
        registerClientAction(SET_PULSE_WIDTH, Integer.class, value -> update(
                configuration().withRedstone(redstoneStrength, redstoneMode, value, pulsePeriod)));
        registerClientAction(SET_PULSE_PERIOD, Integer.class, value -> update(
                configuration().withRedstone(redstoneStrength, redstoneMode, pulseWidth, value)));
        registerClientAction(SET_ENERGY_DISTRIBUTION_MODE, EnergyDistributionMode.class,
                value -> update(configuration().withEnergyDistributionMode(value)));
        registerClientAction(SET_PRODUCT_EXTRACTION_INTERVAL, Integer.class, value -> update(
                configuration().withProductExtraction(value, productExtractionAmount)));
        registerClientAction(SET_PRODUCT_EXTRACTION_AMOUNT, Integer.class, value -> update(
                configuration().withProductExtraction(productExtractionInterval, value)));
        registerClientAction(RESET_TASK_STATE, this::handleResetTaskState);
        registerClientAction(SET_TRANSFER_PORT_OUTPUT_MODE, TransferPortOutputMode.class,
                value -> update(configuration().withTransferPortOutputMode(value)));
        registerClientAction(SET_OUTPUT_SLOT_SHARING_MODE, OutputSlotSharingMode.class,
                value -> update(configuration().withOutputSlotSharingMode(value)));
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            var logic = host.getLogic();
            syncMain = logic.isSyncMainConfiguration();
            copy(logic.getEffectiveConfiguration());
        }
        super.broadcastChanges();
    }

    public void setSyncMain(boolean value) { syncMain = value; sendClientAction(SET_SYNC, value); }
    public void setReturnMode(ReturnMode value) { returnMode = value; sendClientAction(SET_RETURN_MODE, value); }
    public void setBreakRecovery(boolean value) { breakRecovery = value; sendClientAction(SET_BREAK_RECOVERY, value); }
    public void setRedstoneStrength(int value) {
        redstoneStrength = cn.ae2bc.logic.Numbers.clamp(value, 0, 15);
        sendClientAction(SET_REDSTONE_STRENGTH, redstoneStrength);
    }
    public void setRedstoneMode(RedstoneOutputMode value) { redstoneMode = value; sendClientAction(SET_REDSTONE_MODE, value); }
    public void setPulseWidth(int value) {
        pulseWidth = cn.ae2bc.logic.Numbers.clamp(value, 1, pulsePeriod);
        sendClientAction(SET_PULSE_WIDTH, pulseWidth);
    }
    public void setPulsePeriod(int value) {
        pulsePeriod = cn.ae2bc.logic.Numbers.clamp(value, 1, PatternP2PUnitConfiguration.MAX_PULSE_TICKS);
        pulseWidth = Math.min(pulseWidth, pulsePeriod);
        sendClientAction(SET_PULSE_PERIOD, pulsePeriod);
    }
    public void setEnergyDistributionMode(EnergyDistributionMode value) {
        energyDistributionMode = value;
        sendClientAction(SET_ENERGY_DISTRIBUTION_MODE, value);
    }
    public void setProductExtractionInterval(int value) {
        productExtractionInterval = ProductExtractionSettings.clampInterval(value);
        sendClientAction(SET_PRODUCT_EXTRACTION_INTERVAL, productExtractionInterval);
    }
    public void setProductExtractionAmount(int value) {
        productExtractionAmount = ProductExtractionSettings.clampAmount(value);
        sendClientAction(SET_PRODUCT_EXTRACTION_AMOUNT, productExtractionAmount);
    }
    public void resetTaskState() { sendClientAction(RESET_TASK_STATE); }
    public void setTransferPortOutputMode(TransferPortOutputMode value) {
        transferPortOutputMode = value;
        sendClientAction(SET_TRANSFER_PORT_OUTPUT_MODE, value);
    }

    public void setOutputSlotSharingMode(OutputSlotSharingMode value) {
        if (value != null) {
            outputSlotSharingMode = value;
            sendClientAction(SET_OUTPUT_SLOT_SHARING_MODE, value);
        }
    }

    private void handleResetTaskState() {
        if (isServerSide()) host.resetTaskState();
    }

    private PatternP2PUnitConfiguration configuration() {
        return new PatternP2PUnitConfiguration(returnMode, breakRecovery, redstoneStrength,
                redstoneMode, pulseWidth, pulsePeriod,
                productExtractionInterval, productExtractionAmount, transferPortOutputMode,
                outputSlotSharingMode, energyDistributionMode);
    }

    private void update(PatternP2PUnitConfiguration value) {
        if (isServerSide()) host.getLogic().setLocalConfiguration(value);
    }

    private void copy(PatternP2PUnitConfiguration value) {
        returnMode = value.returnMode();
        breakRecovery = value.breakRecovery();
        redstoneStrength = value.redstoneStrength();
        redstoneMode = value.redstoneMode();
        pulseWidth = value.pulseWidthTicks();
        pulsePeriod = value.pulsePeriodTicks();
        productExtractionInterval = value.productExtractionInterval();
        productExtractionAmount = value.productExtractionAmount();
        transferPortOutputMode = value.transferPortOutputMode();
        outputSlotSharingMode = value.outputSlotSharingMode();
        energyDistributionMode = value.energyDistributionMode();
    }
}
