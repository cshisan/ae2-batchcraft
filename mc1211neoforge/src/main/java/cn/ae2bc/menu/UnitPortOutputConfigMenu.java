package cn.ae2bc.menu;

import appeng.api.inventories.InternalInventory;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.RestrictedInputSlot;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.core.unit.OutputSlotSharingMode;

/** Configuration menu for output-type unit ports. */
public final class UnitPortOutputConfigMenu extends AEBaseMenu implements ISubMenu {
    private static final String SET_PRIORITY = "setPriority";
    private static final String SET_SINGLE_SLOT = "setSingleSlot";
    public static final MenuType<UnitPortOutputConfigMenu> TYPE = MenuTypeBuilder
            .create(UnitPortOutputConfigMenu::new, PatternP2PUnitPortPart.class)
            .withMenuTitle(part -> part.getMainMenuIcon().getHoverName())
            .build(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "unit_port_output_config"));

    private final PatternP2PUnitPortPart host;
    @GuiSync(0) public int priority;
    @GuiSync(1) public boolean singleSlot;
    @GuiSync(2) public boolean singleSlotEditable;

    public UnitPortOutputConfigMenu(int id, Inventory playerInventory, PatternP2PUnitPortPart host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        if (!host.getType().acceptsTaskInput()) {
            throw new IllegalStateException("Output configuration menu opened for non-output unit port: " + host.getType());
        }
        InternalInventory markers = host.getOutputFilterMarkers().createMenuWrapper();
        for (int i = 0; i < host.getOutputFilterMarkers().size(); i++) {
            addSlot(new FakeSlot(markers, i), SlotSemantics.CONFIG);
        }
        addSlot(new InverterSlot(host.getOutputFilterInverter(), 0), SlotSemantics.UPGRADE);
        createPlayerInventorySlots(playerInventory);
        priority = host.getPriority();
        singleSlot = host.getEffectiveSingleSlot();
        singleSlotEditable = host.isSingleSlotEditable();
        registerClientAction(SET_PRIORITY, Integer.class, value -> {
            if (isServerSide() && value != null) host.setPriority(value);
        });
        registerClientAction(SET_SINGLE_SLOT, Boolean.class, value -> {
            if (isServerSide() && value != null && host.isSingleSlotEditable()) {
                host.setSingleSlot(value);
            }
        });
    }

    @Override public void broadcastChanges() {
        if (isServerSide()) {
            priority = host.getPriority();
            singleSlot = host.getEffectiveSingleSlot();
            singleSlotEditable = host.isSingleSlotEditable();
        }
        super.broadcastChanges();
    }

    public void setPriority(int value) {
        priority = Math.clamp(value, PatternP2PUnitPortPart.MIN_TRANSFER_PRIORITY,
                PatternP2PUnitPortPart.MAX_TRANSFER_PRIORITY);
        sendClientAction(SET_PRIORITY, priority);
    }

    public void setSingleSlot(boolean value) {
        if (!singleSlotEditable) {
            return;
        }
        singleSlot = value;
        sendClientAction(SET_SINGLE_SLOT, value);
    }

    @Override public ISubMenuHost getHost() { return host; }

    private static final class InverterSlot extends RestrictedInputSlot {
        private InverterSlot(InternalInventory inventory, int slot) {
            super(PlacableItemType.UPGRADES, inventory, slot);
        }
    }
}
