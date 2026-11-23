package org.ae2bc.ioport;

import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.Settings;
import appeng.api.inventories.InternalInventory;
import appeng.api.orientation.RelativeSide;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.OutputSlot;
import appeng.menu.slot.RestrictedInputSlot;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import org.ae2bc.Ae2bcMod;
import org.jetbrains.annotations.Nullable;

public final class Ae2bcIoPortMenu extends UpgradeableMenu<Ae2bcIoPortBlockEntity> {
    public static final MenuType<Ae2bcIoPortMenu> TYPE = MenuTypeBuilder
            .create(Ae2bcIoPortMenu::new, Ae2bcIoPortBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2_batchcraft.io_port"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "io_port"));
    private static final String CYCLE_MODE = "cycle_cell_transfer";
    private static final String TOGGLE_SIDE = "toggle_cell_output_side";
    private static final String CLEAR_SIDES = "clear_cell_output_sides";
    @Getter
    private FullnessMode fullMode = FullnessMode.EMPTY;
    @Getter
    private OperationMode operationMode = OperationMode.EMPTY;
    private IoPortTransferData transferData = IoPortTransferData.DEFAULT;
    private @Nullable IoPortTransferPacket lastSent;

    public Ae2bcIoPortMenu(int id, Inventory inventory, Ae2bcIoPortBlockEntity host) {
        super(TYPE, id, inventory, host);
        this.registerClientAction(CYCLE_MODE, this::cycleTransferMode);
        this.registerClientAction(TOGGLE_SIDE, RelativeSide.class, this::toggleOutputSide);
        this.registerClientAction(CLEAR_SIDES, this::clearOutputSides);
    }

    @Override
    protected void setupConfig() {
        InternalInventory cells = this.getHost().getInternalInventory();
        for (int slot = 0; slot < Ae2bcIoPortBlockEntity.CELL_SLOTS; slot++) {
            this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.STORAGE_CELLS, cells, slot),
                    SlotSemantics.MACHINE_INPUT);
            this.addSlot(new OutputSlot(cells, Ae2bcIoPortBlockEntity.CELL_SLOTS + slot,
                    RestrictedInputSlot.PlacableItemType.STORAGE_CELLS.icon), SlotSemantics.MACHINE_OUTPUT);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager config) {
        this.operationMode = config.getSetting(Settings.OPERATION_MODE);
        this.fullMode = config.getSetting(Settings.FULLNESS_MODE);
        this.setRedStoneMode(config.getSetting(Settings.REDSTONE_CONTROLLED));
    }

    public IoPortTransferData getTransferData() {
        return this.isServerSide() ? this.getHost().getTransferData() : this.transferData;
    }

    public void applySnapshot(IoPortTransferPacket packet) {
        if (this.isClientSide()) {
            this.transferData = packet.data();
            this.fullMode = packet.fullMode();
            this.operationMode = packet.operationMode();
        }
    }

    public void cycleTransferMode() {
        if (this.isClientSide()) {
            this.sendClientAction(CYCLE_MODE);
        } else if (this.canConfigure()) {
            this.getHost().setTransferData(this.getTransferData().nextMode());
        }
    }

    public void toggleOutputSide(RelativeSide side) {
        if (this.isClientSide()) {
            this.sendClientAction(TOGGLE_SIDE, side);
        } else if (this.canConfigure() && this.getTransferData().mode().allowsExternal()) {
            this.getHost().setTransferData(this.getTransferData().toggle(side));
        }
    }

    public void clearOutputSides() {
        if (this.isClientSide()) {
            this.sendClientAction(CLEAR_SIDES);
        } else if (this.canConfigure() && this.getTransferData().mode().allowsExternal()) {
            this.getHost().setTransferData(this.getTransferData().clearSides());
        }
    }

    /* 配置动作只能作用于玩家正在操作且仍存在的方块。 */
    private boolean canConfigure() {
        Ae2bcIoPortBlockEntity host = this.getHost();
        Level level = host.getLevel();
        return this.getPlayer().containerMenu == this && this.stillValid(this.getPlayer())
                && !host.isRemoved() && level != null && level.getBlockEntity(host.getBlockPos()) == host;
    }

    /* 主界面和输出面子页共享菜单，仅发送发生变化的设置快照。 */
    @Override
    protected void standardDetectAndSendChanges() {
        super.standardDetectAndSendChanges();
        if (this.isServerSide() && this.isValidMenu()) {
            IoPortTransferPacket packet = new IoPortTransferPacket(
                    this.containerId, this.getTransferData(), this.fullMode, this.operationMode);
            if (!packet.equals(this.lastSent)) {
                this.lastSent = packet;
                this.sendPacketToClient(packet);
            }
        }
    }
}
