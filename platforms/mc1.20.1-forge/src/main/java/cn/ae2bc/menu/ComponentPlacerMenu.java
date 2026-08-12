package cn.ae2bc.menu;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.api.inventories.InternalInventory;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.p2p.P2PTunnelPart;
import appeng.util.Platform;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.placer.ComponentPlacerItem;
import cn.ae2bc.placer.ComponentPlacementService;
import cn.ae2bc.placer.ComponentPlacerMenuHost;
import cn.ae2bc.placer.ComponentPlacerSelection;
import cn.ae2bc.placer.ComponentPlacerSettings;
import cn.ae2bc.registry.ModContent;
import cn.ae2bc.platform.ItemData;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ComponentPlacerMenu extends AEBaseMenu {
    public static final SlotSemantic CABLE_MARKER_SLOT = SlotSemantics.register(
            "AE2_BATCHCRAFT_CABLE_MARKER", false);
    public static final SlotSemantic PART_MARKER_SLOT = SlotSemantics.register(
            "AE2_BATCHCRAFT_PART_MARKER", false);

    private static final String SET_DIRECTION = "setDirection";
    private static final String ADJUST_X = "adjustX";
    private static final String ADJUST_Y = "adjustY";
    private static final String ADJUST_Z = "adjustZ";
    private static final String RESET_OFFSETS = "resetOffsets";
    private static final String CLEAR_SELECTION = "clearSelection";
    private static final String EXECUTE = "execute";
    private static final String LOAD_FREQUENCY = "loadFrequency";
    private static final String RESET_FREQUENCY = "resetFrequency";

    public static final MenuType<ComponentPlacerMenu> TYPE = MenuTypeBuilder
            .create(ComponentPlacerMenu::new, ComponentPlacerMenuHost.class)
            .withMenuTitle(host -> host.getItemStack().getHoverName())
            .build(ModContent.COMPONENT_PLACER_ID);

    private final ComponentPlacerMenuHost host;

    @GuiSync(0)
    public Direction direction = Direction.UP;
    @GuiSync(1)
    public int offsetX;
    @GuiSync(2)
    public int offsetY;
    @GuiSync(3)
    public int offsetZ;
    @GuiSync(4)
    public ComponentPlacerSelection.Validation selectionState = ComponentPlacerSelection.Validation.INCOMPLETE;
    @GuiSync(5)
    public int sizeX;
    @GuiSync(6)
    public int sizeY;
    @GuiSync(7)
    public int sizeZ;
    @GuiSync(8)
    public boolean hasCable;
    @GuiSync(9)
    public boolean hasPart;
    @GuiSync(10)
    public boolean hasSelection;
    @GuiSync(11)
    public int frequency;
    @GuiSync(12)
    public boolean aeConnected;

    public ComponentPlacerMenu(int id, Inventory playerInventory, ComponentPlacerMenuHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        addSlot(new CableMarkerSlot(host.getCableFilter(), Component.translatable(
                "gui.ae2_batchcraft.component_placer.cable.tooltip")), CABLE_MARKER_SLOT);
        addSlot(new CableMarkerSlot(host.getPartFilter(), Component.translatable(
                "gui.ae2_batchcraft.component_placer.part.tooltip")), PART_MARKER_SLOT);
        for (int i = 0; i < ComponentPlacerItem.MATERIAL_SLOT_COUNT; i++) {
            addSlot(new AppEngSlot(host.getMaterials(), i), SlotSemantics.STORAGE);
        }
        for (int i = 0; i < host.getUpgrades().size(); i++) {
            addSlot(new RestrictedInputSlot(
                    RestrictedInputSlot.PlacableItemType.UPGRADES,
                    host.getUpgrades(), i), SlotSemantics.UPGRADE);
        }
        createPlayerInventorySlots(playerInventory);

        registerClientAction(SET_DIRECTION, Direction.class, this::handleSetDirection);
        registerClientAction(ADJUST_X, Integer.class, value -> handleAdjustOffset(value, 0));
        registerClientAction(ADJUST_Y, Integer.class, value -> handleAdjustOffset(value, 1));
        registerClientAction(ADJUST_Z, Integer.class, value -> handleAdjustOffset(value, 2));
        registerClientAction(RESET_OFFSETS, this::handleResetOffsets);
        registerClientAction(CLEAR_SELECTION, this::handleClearSelection);
        registerClientAction(EXECUTE, this::handleExecute);
        registerClientAction(LOAD_FREQUENCY, this::handleLoadFrequency);
        registerClientAction(RESET_FREQUENCY, this::handleResetFrequency);
    }

    public IUpgradeInventory getUpgrades() {
        return host.getUpgrades();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            var stack = host.getItemStack();
            aeConnected = host.getLinkStatus().connected();
            var settings = ItemData.getSettings(stack);
            direction = settings.direction();
            offsetX = settings.offsetX();
            offsetY = settings.offsetY();
            offsetZ = settings.offsetZ();
            var selection = ItemData.getSelection(stack);
            hasCable = ComponentPlacerItem.isUsableCable(
                    ComponentPlacerItem.getMarkedCable(stack));
            hasPart = ComponentPlacerItem.isUsablePart(
                    ComponentPlacerItem.getMarkedPart(stack));
            frequency = Short.toUnsignedInt(ItemData.getFrequency(stack));
            if (selection == null) {
                selectionState = ComponentPlacerSelection.Validation.INCOMPLETE;
                sizeX = sizeY = sizeZ = 0;
                hasSelection = false;
            } else {
                selectionState = selection.validate();
                sizeX = selection.sizeX();
                sizeY = selection.sizeY();
                sizeZ = selection.sizeZ();
                hasSelection = true;
            }
        }
        super.broadcastChanges();
    }

    public void setDirection(Direction value) {
        if (value != null) {
            direction = value;
            sendClientAction(SET_DIRECTION, value);
        }
    }

    public void adjustOffset(int axis, int delta) {
        if (delta == 0) {
            return;
        }
        switch (axis) {
            case 0 -> offsetX = ComponentPlacerSettings.clampOffset(offsetX + delta);
            case 1 -> offsetY = ComponentPlacerSettings.clampOffset(offsetY + delta);
            case 2 -> offsetZ = ComponentPlacerSettings.clampOffset(offsetZ + delta);
            default -> throw new IllegalArgumentException("axis");
        }
        sendClientAction(switch (axis) {
            case 0 -> ADJUST_X;
            case 1 -> ADJUST_Y;
            default -> ADJUST_Z;
        }, delta);
    }

    public void resetOffsets() {
        offsetX = offsetZ = 0;
        offsetY = 1;
        sendClientAction(RESET_OFFSETS);
    }

    public void clearSelection() {
        sendClientAction(CLEAR_SELECTION);
    }

    public void execute() {
        sendClientAction(EXECUTE);
    }

    public void loadFrequency() {
        sendClientAction(LOAD_FREQUENCY);
    }

    public void resetFrequency() {
        frequency = 0;
        sendClientAction(RESET_FREQUENCY);
    }

    private void handleSetDirection(Direction value) {
        if (isServerSide() && value != null) {
            updateSettings(ItemData.getSettings(host.getItemStack()).withDirection(value));
        }
    }

    private void handleAdjustOffset(Integer value, int axis) {
        if (!isServerSide() || value == null) {
            return;
        }
        var settings = ItemData.getSettings(host.getItemStack());
        int x = settings.offsetX();
        int y = settings.offsetY();
        int z = settings.offsetZ();
        switch (axis) {
            case 0 -> x = addOffset(x, value);
            case 1 -> y = addOffset(y, value);
            case 2 -> z = addOffset(z, value);
            default -> { return; }
        }
        updateSettings(settings.withOffsets(x, y, z));
    }

    private void handleResetOffsets() {
        if (isServerSide()) {
            updateSettings(ItemData.getSettings(host.getItemStack()).resetOffsets());
        }
    }

    private void handleClearSelection() {
        if (isServerSide()) {
            ItemData.setSelection(host.getItemStack(), null);
        }
    }

    private void handleExecute() {
        if (isServerSide() && getPlayer() instanceof ServerPlayer serverPlayer) {
            var result = ComponentPlacementService.place(serverPlayer, host);
            if (host.getLinkStatus().connected() && !result.missingMaterial().isEmpty()) {
                AEItemKey key = AEItemKey.of(result.missingMaterial());
                if (key != null && getLocator() != null) {
                    CraftAmountMenu.open(serverPlayer, getLocator(), key, result.missingAmount());
                    return;
                }
            }
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.component_placer.result",
                    result.placed(), result.occupied(), result.materialFailed(), result.placementFailed()), false);
        }
    }

    private void handleLoadFrequency() {
        if (!isServerSide()) {
            return;
        }

        ItemStack card = findMemoryCard();
        if (!(card.getItem() instanceof IMemoryCard)) {
            getPlayer().displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.component_placer.frequency.no_card"), false);
            return;
        }

        var cardData = ((IMemoryCard) card.getItem()).getData(card);
        var storedType = IPartItem.byId(ResourceLocation.tryParse(cardData.getString("p2pType")));
        short storedFrequency = ItemData.getP2PFrequency(cardData);
        if (storedType == null || !P2PTunnelPart.class.isAssignableFrom(storedType.getPartClass())
                || !ItemData.hasP2PFrequency(cardData)) {
            getPlayer().displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.component_placer.frequency.invalid_card"), false);
            return;
        }

        setFrequency(storedFrequency);
        getPlayer().displayClientMessage(Component.translatable(
                "message.ae2_batchcraft.component_placer.frequency.loaded",
                Platform.p2p().toHexString(storedFrequency)), false);
    }

    private void handleResetFrequency() {
        if (!isServerSide()) {
            return;
        }
        setFrequency((short) 0);
        getPlayer().displayClientMessage(Component.translatable(
                "message.ae2_batchcraft.component_placer.frequency.reset"), false);
    }

    private ItemStack findMemoryCard() {
        if (getCarried().getItem() instanceof IMemoryCard) {
            return getCarried();
        }
        if (getPlayer().getMainHandItem().getItem() instanceof IMemoryCard) {
            return getPlayer().getMainHandItem();
        }
        if (getPlayer().getOffhandItem().getItem() instanceof IMemoryCard) {
            return getPlayer().getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    private void setFrequency(short value) {
        ItemData.setFrequency(host.getItemStack(), value);
        frequency = Short.toUnsignedInt(value);
    }

    private void updateSettings(ComponentPlacerSettings settings) {
        ItemData.setSettings(host.getItemStack(), settings);
    }

    private static int addOffset(int offset, int delta) {
        return (int) Math.max(-ComponentPlacerSettings.MAX_OFFSET,
                Math.min((long) offset + delta, ComponentPlacerSettings.MAX_OFFSET));
    }

    private static final class CableMarkerSlot extends FakeSlot {
        private CableMarkerSlot(InternalInventory inventory, Component tooltip) {
            super(inventory, 0);
            setEmptyTooltip(() -> List.of(tooltip));
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
