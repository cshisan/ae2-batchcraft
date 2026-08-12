package cn.ae2bc.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.logic.ReturnMode;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.logic.PatternP2PUnitConfiguration;
import cn.ae2bc.logic.ProductExtractionSettings;
import cn.ae2bc.part.PatternP2PTunnelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

public final class PatternP2PTunnelInputMenu extends AEBaseMenu {
    private static final String SET_RETURN_MODE = "setReturnMode";
    private static final String SET_BREAK_RECOVERY = "setBreakRecovery";
    private static final String SET_REDSTONE_STRENGTH = "setRedstoneStrength";
    private static final String SET_REDSTONE_MODE = "setRedstoneMode";
    private static final String SET_PULSE_WIDTH = "setPulseWidth";
    private static final String SET_PULSE_PERIOD = "setPulsePeriod";
    private static final String SET_PRODUCT_EXTRACTION_ENABLED = "setProductExtractionEnabled";
    private static final String SET_PRODUCT_EXTRACTION_INTERVAL = "setProductExtractionInterval";
    private static final String SET_PRODUCT_EXTRACTION_AMOUNT = "setProductExtractionAmount";
    private static final String RESET_TASK_STATE = "resetTaskState";

    public static final MenuType<PatternP2PTunnelInputMenu> TYPE = MenuTypeBuilder
            .create(PatternP2PTunnelInputMenu::new, PatternP2PTunnelPart.class)
            .withMenuTitle(part -> part.getPartItem().asItem().getDescription())
            .build(ResourceLocation.fromNamespaceAndPath(
                    Ae2bcMod.MOD_ID, "pattern_p2p_tunnel_input"));

    private final PatternP2PTunnelPart host;

    @GuiSync(0)
    public ReturnMode returnMode = ReturnMode.UNBLOCKED;
    @GuiSync(1) public boolean breakRecovery = true;
    @GuiSync(2) public int redstoneStrength = PatternP2PUnitConfiguration.DEFAULT_REDSTONE_STRENGTH;
    @GuiSync(3) public RedstoneOutputMode redstoneMode = RedstoneOutputMode.SINGLE_TRIGGER;
    @GuiSync(4) public int pulseWidth = PatternP2PUnitConfiguration.DEFAULT_PULSE_WIDTH;
    @GuiSync(5) public int pulsePeriod = PatternP2PUnitConfiguration.DEFAULT_PULSE_PERIOD;
    @GuiSync(6) public boolean productExtractionEnabled;
    @GuiSync(7) public int productExtractionInterval = ProductExtractionSettings.DEFAULT_INTERVAL;
    @GuiSync(8) public int productExtractionAmount = ProductExtractionSettings.DEFAULT_AMOUNT;

    public PatternP2PTunnelInputMenu(int id, Inventory playerInventory, PatternP2PTunnelPart host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        registerClientAction(SET_RETURN_MODE, ReturnMode.class, this::handleSetReturnMode);
        registerClientAction(SET_BREAK_RECOVERY, Boolean.class, value -> updateConfiguration(
                configuration().withBreakRecovery(value)));
        registerClientAction(SET_REDSTONE_STRENGTH, Integer.class, value -> updateConfiguration(
                configuration().withRedstone(value, redstoneMode, pulseWidth, pulsePeriod)));
        registerClientAction(SET_REDSTONE_MODE, RedstoneOutputMode.class, value -> updateConfiguration(
                configuration().withRedstone(redstoneStrength, value, pulseWidth, pulsePeriod)));
        registerClientAction(SET_PULSE_WIDTH, Integer.class, value -> updateConfiguration(
                configuration().withRedstone(redstoneStrength, redstoneMode, value, pulsePeriod)));
        registerClientAction(SET_PULSE_PERIOD, Integer.class, value -> updateConfiguration(
                configuration().withRedstone(redstoneStrength, redstoneMode, pulseWidth, value)));
        registerClientAction(SET_PRODUCT_EXTRACTION_ENABLED, Boolean.class,
                this::handleSetProductExtractionEnabled);
        registerClientAction(SET_PRODUCT_EXTRACTION_INTERVAL, Integer.class,
                this::handleSetProductExtractionInterval);
        registerClientAction(SET_PRODUCT_EXTRACTION_AMOUNT, Integer.class,
                this::handleSetProductExtractionAmount);
        registerClientAction(RESET_TASK_STATE, this::handleResetTaskState);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && !host.isOutput()) {
            var logic = host.getInputLogic();
            returnMode = logic.getReturnMode();
            copyConfiguration(logic.getPatternP2PUnitConfiguration());
            var extraction = logic.getProductExtractionSettings();
            productExtractionEnabled = extraction.enabled();
            productExtractionInterval = extraction.interval();
            productExtractionAmount = extraction.amount();
        }
        super.broadcastChanges();
    }

    public void setReturnMode(ReturnMode mode) {
        returnMode = mode;
        sendClientAction(SET_RETURN_MODE, mode);
    }

    public void setBreakRecovery(boolean value) { breakRecovery = value; sendClientAction(SET_BREAK_RECOVERY, value); }
    public void setRedstoneStrength(int value) {
        redstoneStrength = Math.clamp(value, 0, 15);
        sendClientAction(SET_REDSTONE_STRENGTH, redstoneStrength);
    }
    public void setRedstoneMode(RedstoneOutputMode value) { redstoneMode = value; sendClientAction(SET_REDSTONE_MODE, value); }
    public void setPulseWidth(int value) {
        pulseWidth = Math.clamp(value, 1, pulsePeriod);
        sendClientAction(SET_PULSE_WIDTH, pulseWidth);
    }
    public void setPulsePeriod(int value) {
        pulsePeriod = Math.clamp(value, 1, PatternP2PUnitConfiguration.MAX_PULSE_TICKS);
        pulseWidth = Math.min(pulseWidth, pulsePeriod);
        sendClientAction(SET_PULSE_PERIOD, pulsePeriod);
    }

    public void setProductExtractionEnabled(boolean value) {
        productExtractionEnabled = value;
        sendClientAction(SET_PRODUCT_EXTRACTION_ENABLED, value);
    }

    public void setProductExtractionInterval(int value) {
        productExtractionInterval = ProductExtractionSettings.clampInterval(value);
        sendClientAction(SET_PRODUCT_EXTRACTION_INTERVAL, productExtractionInterval);
    }

    public void setProductExtractionAmount(int value) {
        productExtractionAmount = ProductExtractionSettings.clampAmount(value);
        sendClientAction(SET_PRODUCT_EXTRACTION_AMOUNT, productExtractionAmount);
    }

    public void resetTaskState() {
        sendClientAction(RESET_TASK_STATE);
    }

    private void handleSetReturnMode(ReturnMode mode) {
        if (isServerSide() && !host.isOutput() && mode != null) {
            host.getInputLogic().setReturnMode(mode);
        }
    }

    private void handleResetTaskState() {
        if (isServerSide() && !host.isOutput()) {
            host.getInputLogic().resetAllTaskStates();
        }
    }

    private void handleSetProductExtractionEnabled(Boolean value) {
        if (isServerSide() && !host.isOutput() && value != null) {
            host.getInputLogic().setProductExtractionEnabled(value);
        }
    }

    private void handleSetProductExtractionInterval(Integer value) {
        if (isServerSide() && !host.isOutput() && value != null) {
            host.getInputLogic().setProductExtractionInterval(value);
        }
    }

    private void handleSetProductExtractionAmount(Integer value) {
        if (isServerSide() && !host.isOutput() && value != null) {
            host.getInputLogic().setProductExtractionAmount(value);
        }
    }

    private PatternP2PUnitConfiguration configuration() {
        return new PatternP2PUnitConfiguration(returnMode, breakRecovery, redstoneStrength,
                redstoneMode, pulseWidth, pulsePeriod,
                productExtractionInterval, productExtractionAmount);
    }

    private void updateConfiguration(PatternP2PUnitConfiguration value) {
        if (isServerSide() && !host.isOutput()) host.getInputLogic().setPatternP2PUnitConfiguration(value);
    }

    private void copyConfiguration(PatternP2PUnitConfiguration value) {
        returnMode = value.returnMode();
        breakRecovery = value.breakRecovery();
        redstoneStrength = value.redstoneStrength();
        redstoneMode = value.redstoneMode();
        pulseWidth = value.pulseWidthTicks();
        pulsePeriod = value.pulsePeriodTicks();
        productExtractionInterval = value.productExtractionInterval();
        productExtractionAmount = value.productExtractionAmount();
    }

}
