package cn.ae2bc.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.logic.ReturnMode;
import cn.ae2bc.logic.ProductExtractionSettings;
import cn.ae2bc.part.PatternP2PTunnelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

public final class PatternP2PTunnelOutputMenu extends AEBaseMenu {
    private static final String SET_RETURN_MODE = "setReturnMode";
    private static final String SET_SYNC_INPUT_SETTINGS = "setSyncInputSettings";
    private static final String SET_PRODUCT_EXTRACTION_ENABLED = "setProductExtractionEnabled";
    private static final String SET_PRODUCT_EXTRACTION_INTERVAL = "setProductExtractionInterval";
    private static final String SET_PRODUCT_EXTRACTION_AMOUNT = "setProductExtractionAmount";
    private static final String RESET_TASK_STATE = "resetTaskState";

    public static final MenuType<PatternP2PTunnelOutputMenu> TYPE = MenuTypeBuilder
            .create(PatternP2PTunnelOutputMenu::new, PatternP2PTunnelPart.class)
            .withMenuTitle(part -> part.getPartItem().asItem().getDescription())
            .build(ResourceLocation.fromNamespaceAndPath(
                    Ae2bcMod.MOD_ID, "pattern_p2p_tunnel_output"));

    private final PatternP2PTunnelPart host;

    @GuiSync(0)
    public ReturnMode returnMode = ReturnMode.UNBLOCKED;
    @GuiSync(1)
    public boolean syncInputSettings;
    @GuiSync(2) public boolean productExtractionEnabled;
    @GuiSync(3) public int productExtractionInterval = ProductExtractionSettings.DEFAULT_INTERVAL;
    @GuiSync(4) public int productExtractionAmount = ProductExtractionSettings.DEFAULT_AMOUNT;

    public PatternP2PTunnelOutputMenu(int id, Inventory playerInventory, PatternP2PTunnelPart host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        registerClientAction(SET_RETURN_MODE, ReturnMode.class, this::handleSetReturnMode);
        registerClientAction(SET_SYNC_INPUT_SETTINGS, Boolean.class, this::handleSetSyncInputSettings);
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
        if (isServerSide() && host.isStandardOutput()) {
            var logic = host.getOutputLogic();
            returnMode = logic.getReturnMode();
            syncInputSettings = logic.isSyncInputSettings();
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

    public void setSyncInputSettings(boolean enabled) {
        syncInputSettings = enabled;
        sendClientAction(SET_SYNC_INPUT_SETTINGS, enabled);
    }

    public void resetTaskState() {
        sendClientAction(RESET_TASK_STATE);
    }

    public void setProductExtractionEnabled(boolean enabled) {
        productExtractionEnabled = enabled;
        sendClientAction(SET_PRODUCT_EXTRACTION_ENABLED, enabled);
    }

    public void setProductExtractionInterval(int interval) {
        productExtractionInterval = ProductExtractionSettings.clampInterval(interval);
        sendClientAction(SET_PRODUCT_EXTRACTION_INTERVAL, productExtractionInterval);
    }

    public void setProductExtractionAmount(int amount) {
        productExtractionAmount = ProductExtractionSettings.clampAmount(amount);
        sendClientAction(SET_PRODUCT_EXTRACTION_AMOUNT, productExtractionAmount);
    }


    private void handleSetReturnMode(ReturnMode mode) {
        if (isServerSide() && host.isStandardOutput()
                && mode != null && !host.getOutputLogic().isSyncInputSettings()) {
            host.getOutputLogic().setReturnMode(mode);
        }
    }

    private void handleSetSyncInputSettings(Boolean enabled) {
        if (isServerSide() && host.isStandardOutput() && enabled != null) {
            host.getOutputLogic().setSyncInputSettings(enabled);
        }
    }

    private void handleSetProductExtractionEnabled(Boolean enabled) {
        if (isServerSide() && host.isStandardOutput() && enabled != null
                && !host.getOutputLogic().isSyncInputSettings()) {
            host.getOutputLogic().setProductExtractionEnabled(enabled);
        }
    }

    private void handleSetProductExtractionInterval(Integer interval) {
        if (isServerSide() && host.isStandardOutput() && interval != null
                && !host.getOutputLogic().isSyncInputSettings()) {
            host.getOutputLogic().setProductExtractionInterval(interval);
        }
    }

    private void handleSetProductExtractionAmount(Integer amount) {
        if (isServerSide() && host.isStandardOutput() && amount != null
                && !host.getOutputLogic().isSyncInputSettings()) {
            host.getOutputLogic().setProductExtractionAmount(amount);
        }
    }

    private void handleResetTaskState() {
        if (isServerSide() && host.isStandardOutput()) {
            host.resetTaskState();
        }
    }

}
