package cn.ae2bc.menu;

import appeng.api.inventories.InternalInventory;
import appeng.api.storage.ISubMenuHost;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.FakeSlot;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.extension.PatternProviderExtractionExtension;
import cn.ae2bc.logic.ProductExtractionSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public final class ProductExtractionMenu extends AEBaseMenu implements ISubMenu {
    public static final SlotSemantic MARKER_SLOT = SlotSemantics.register("AE2_BATCHCRAFT_PRODUCT_MARKER", false);
    private static final String SET_INTERVAL = "setInterval";
    private static final String SET_AMOUNT = "setAmount";
    private static final String SET_WHITELIST = "setWhitelist";

    public static final MenuType<ProductExtractionMenu> TYPE = MenuTypeBuilder
            .create(ProductExtractionMenu::new, PatternProviderLogicHost.class)
            .withMenuTitle(host -> host.getMainMenuIcon().getHoverName())
            .build(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "product_extraction"));

    private final PatternProviderLogicHost host;

    @GuiSync(0)
    public int interval = ProductExtractionSettings.DEFAULT_INTERVAL;
    @GuiSync(1)
    public int amount = ProductExtractionSettings.DEFAULT_AMOUNT;
    @GuiSync(2)
    public boolean whitelist;
    @GuiSync(3)
    public boolean cardInstalled;

    public ProductExtractionMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        InternalInventory markerInventory = extraction().ae2bc$getProductExtractionMarkers().createMenuWrapper();
        for (int i = 0; i < ProductExtractionSettings.MARKER_SLOT_COUNT; i++) {
            addSlot(new MarkerSlot(markerInventory, i), MARKER_SLOT);
        }
        createPlayerInventorySlots(playerInventory);
        registerClientAction(SET_INTERVAL, Integer.class, this::handleSetInterval);
        registerClientAction(SET_AMOUNT, Integer.class, this::handleSetAmount);
        registerClientAction(SET_WHITELIST, Boolean.class, this::handleSetWhitelist);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            var settings = extraction().ae2bc$getProductExtractionSettings();
            interval = settings.interval();
            amount = settings.amount();
            whitelist = settings.whitelist();
            cardInstalled = settings.enabled();
        }
        super.broadcastChanges();
    }

    public void setInterval(int value) {
        interval = ProductExtractionSettings.clampInterval(value);
        sendClientAction(SET_INTERVAL, interval);
    }

    public void setAmount(int value) {
        amount = ProductExtractionSettings.clampAmount(value);
        sendClientAction(SET_AMOUNT, amount);
    }

    public void setWhitelist(boolean value) {
        whitelist = value;
        sendClientAction(SET_WHITELIST, value);
    }

    @Override
    public ISubMenuHost getHost() {
        return host;
    }

    private PatternProviderExtractionExtension extraction() {
        return (PatternProviderExtractionExtension) host.getLogic();
    }

    private void handleSetInterval(Integer value) {
        if (isServerSide() && value != null && extraction().ae2bc$hasProductExtractionCard()) {
            extraction().ae2bc$setProductExtractionInterval(value);
        }
    }

    private void handleSetAmount(Integer value) {
        if (isServerSide() && value != null && extraction().ae2bc$hasProductExtractionCard()) {
            extraction().ae2bc$setProductExtractionAmount(value);
        }
    }

    private void handleSetWhitelist(Boolean value) {
        if (isServerSide() && value != null && extraction().ae2bc$hasProductExtractionCard()) {
            extraction().ae2bc$setProductExtractionWhitelist(value);
        }
    }

    private static final class MarkerSlot extends FakeSlot {
        private MarkerSlot(InternalInventory inventory, int slot) {
            super(inventory, slot);
        }

        @Override
        public void set(ItemStack stack) {
            super.set(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }

        @Override
        public void increase(ItemStack stack) {
            set(stack);
        }

        @Override
        public void decrease(ItemStack stack) {
            set(stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
    }
}
