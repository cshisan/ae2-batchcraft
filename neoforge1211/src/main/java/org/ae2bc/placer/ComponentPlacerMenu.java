package org.ae2bc.placer;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardColors;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.ILinkStatus;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.RestrictedInputSlot;
import lombok.Getter;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.placer.DataModel;
import org.ae2bc.core.placer.Pos;
import org.ae2bc.placer.packet.ComponentPlacerClientPacket;
import org.ae2bc.registry.component.Pp2mpUnitManagerRegistry;
import org.ae2bc.storage.MarkerSlot;
import org.jetbrains.annotations.Nullable;

public final class ComponentPlacerMenu extends AEBaseMenu {
    public static final MenuType<ComponentPlacerMenu> TYPE = MenuTypeBuilder
            .create(ComponentPlacerMenu::new, ComponentPlacerMenuHost.class)
            .withMenuTitle(host -> host.getItemStack().getHoverName())
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "component_placer"));

    public static final SlotSemantic CABLE_MARKER_SLOT = SlotSemantics.register("AE2BC_PLACER_CABLE", false);
    public static final SlotSemantic PART_MARKER_SLOT = SlotSemantics.register("AE2BC_PLACER_PART", false);
    private static final String SET_DIRECTION = "setDirection";
    private static final String ADJUST_OFFSET = "adjustOffset";
    private static final String RESET_OFFSETS = "resetOffsets";
    private static final String CLEAR_SELECTION = "clearSelection";
    private static final String CLEAR_FREQUENCY = "clearFrequency";
    private static final String CLEAR_UNIT_ID = "clearUnitId";
    private static final String LOAD_FREQUENCY = "loadFrequency";
    private static final String LOAD_UNIT_ID = "loadUnitId";
    private static final String EXECUTE = "execute";

    private final ComponentPlacerMenuHost host;
    private @Getter Direction direction = ComponentPlacerPose.DEFAULT.direction();
    private @Getter DataModel model = DataModel.DEFAULT;
    private @Getter boolean paired;
    private @Getter @Nullable Component linkError;
    private ComponentPlacerClientPacket lastSentState;

    public ComponentPlacerMenu(int id, Inventory playerInventory, ComponentPlacerMenuHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;

        this.addSlot(new MarkerSlot(host.getCableMarker(), Component.translatable(
                "gui.ae2_batchcraft.component_placer.cable.tooltip"
        )), CABLE_MARKER_SLOT);
        this.addSlot(new MarkerSlot(host.getPartMarker(), Component.translatable(
                "gui.ae2_batchcraft.component_placer.part.tooltip")
        ), PART_MARKER_SLOT);
        for (int i = 0; i < ComponentPlacerData.MATERIAL_SLOT_COUNT; i++) {
            this.addSlot(new AppEngSlot(host.getMaterials(), i), SlotSemantics.STORAGE);
        }

        // 升级卡槽
        IUpgradeInventory upgrades = host.getItem().getUpgrades(host.getItemStack());
        for (int i = 0; i < upgrades.size(); i++) {
            this.addSlot(new RestrictedInputSlot(
                    RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades, i
            ), SlotSemantics.UPGRADE);
        }
        // 玩家物品栏
        this.createPlayerInventorySlots(playerInventory);

        this.registerClientAction(SET_DIRECTION, Direction.class, this::handleSetDirection);
        this.registerClientAction(ADJUST_OFFSET, int[].class, this::handleAdjustOffset);
        this.registerClientAction(RESET_OFFSETS, this::handleResetOffsets);
        this.registerClientAction(CLEAR_SELECTION, this::handleClearSelection);
        this.registerClientAction(CLEAR_FREQUENCY, this::handleClearFrequency);
        this.registerClientAction(CLEAR_UNIT_ID, this::handleClearUnitId);
        this.registerClientAction(LOAD_FREQUENCY, () -> this.handleLoadSetting(AEComponents.EXPORTED_P2P_FREQUENCY));
        this.registerClientAction(LOAD_UNIT_ID, () -> this.handleLoadSetting(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID));
        this.registerClientAction(EXECUTE, this::handleExecute);
    }

    /* 服务端比较完整快照，首次打开和配置、选区、连接状态变化时同步。 */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.isServerSide() && this.isValidMenu()) {
            ItemStack stack = this.host.getItemStack();
            ILinkStatus linkStatus = this.host.getLinkStatus();
            this.direction = ComponentPlacerData.pose(stack).direction();
            this.model = ComponentPlacerData.createDataModel(stack, linkStatus.connected());
            this.paired = this.host.getItem().getLinkedPosition(stack) != null;
            this.linkError = linkStatus.statusDescription();
            ComponentPlacerClientPacket packet = new ComponentPlacerClientPacket(
                    this.containerId, this.direction, this.model, this.paired, this.linkError);
            if (!packet.equals(this.lastSentState)) {
                this.sendPacketToClient(packet);
                this.lastSentState = packet;
            }
        }
    }

    public IUpgradeInventory getUpgrades() {
        return this.host.getItem().getUpgrades(this.host.getItemStack());
    }

    public void setDirection(Direction value) {
        if (value != null) {
            this.direction = value;
            this.sendClientAction(SET_DIRECTION, value);
        }
    }

    public void adjustOffset(int axis, int delta) {
        int[] offsets = {this.model.pos1().x(), this.model.pos1().y(), this.model.pos1().z()};
        offsets[axis] = ComponentPlacerPose.clampOffset(offsets[axis] + delta);
        DataModel.Builder builder = new DataModel.Builder(this.model);
        builder.pos1(new Pos(offsets[0], offsets[1], offsets[2]));
        this.model = builder.build();
        this.sendClientAction(ADJUST_OFFSET, offsets);
    }

    public void resetOffsets() {
        DataModel.Builder builder = new DataModel.Builder(this.model);
        builder.pos1(ComponentPlacerPose.DEFAULT.pos());
        this.model = builder.build();
        this.sendClientAction(RESET_OFFSETS);
    }

    public void clearSelection() {
        this.sendClientAction(CLEAR_SELECTION);
    }

    public void clearFrequency() {
        this.sendClientAction(CLEAR_FREQUENCY);
    }

    public void clearUnitId() {
        this.sendClientAction(CLEAR_UNIT_ID);
    }

    public void loadFrequency() {
        this.sendClientAction(LOAD_FREQUENCY);
    }

    public void loadUnitId() {
        this.sendClientAction(LOAD_UNIT_ID);
    }

    public void execute() {
        this.sendClientAction(EXECUTE);
    }

    private ComponentPlacerPose pose() {
        return ComponentPlacerData.pose(this.host.getItemStack());
    }

    private void handleSetDirection(Direction value) {
        if (this.isServerSide() && value != null) {
            ComponentPlacerData.pose(
                    this.host.getItemStack(),
                    this.pose().withDirection(value)
            );
        }
    }

    private void handleAdjustOffset(int[] values) {
        if (this.isServerSide() && values != null && values.length == 3) {
            ComponentPlacerData.pose(
                    this.host.getItemStack(),
                    this.pose().withOffsets(values[0], values[1], values[2])
            );
        }
    }

    private void handleResetOffsets() {
        if (this.isServerSide()) {
            ComponentPlacerData.pose(
                    this.host.getItemStack(),
                    this.pose().resetOffsets()
            );
        }
    }

    private void handleClearSelection() {
        if (this.isServerSide()) {
            ComponentPlacerData.selection(this.host.getItemStack(), null);
        }
    }

    /* 在服务端清除频率，由现有快照同步刷新色块。 */
    private void handleClearFrequency() {
        if (this.isServerSide()) {
            ComponentPlacerData.clearFrequency(this.host.getItemStack());
        }
    }

    /* 单元 ID 独立清除，不影响保存的频率和其他配置。 */
    private void handleClearUnitId() {
        if (this.isServerSide()) {
            ComponentPlacerData.clearUnitId(this.host.getItemStack());
        }
    }

    /* 只读取服务端光标上的内存卡；无有效值时保留原配置，不消耗或修改卡片。 */
    private void handleLoadSetting(DataComponentType<Short> setting) {
        if (!this.isServerSide()) {
            return;
        }
        ItemStack card = this.getCarried();
        if (card.isEmpty() || !(card.getItem() instanceof IMemoryCard memoryCard)) {
            return;
        }
        Short value = card.get(setting);
        if (value == null || value == 0) {
            memoryCard.notifyUser(this.getPlayer(), MemoryCardMessages.INVALID_MACHINE);
            return;
        }
        ItemStack stack = this.host.getItemStack();
        if (setting == AEComponents.EXPORTED_P2P_FREQUENCY) {
            ComponentPlacerData.clearFrequency(stack);
            Item type = card.get(AEComponents.EXPORTED_P2P_TYPE);
            if (type == null) {
                stack.remove(AEComponents.EXPORTED_P2P_TYPE);
            } else {
                stack.set(AEComponents.EXPORTED_P2P_TYPE, type);
            }
            MemoryCardColors colors = card.get(AEComponents.MEMORY_CARD_COLORS);
            if (colors != null) {
                stack.set(AEComponents.MEMORY_CARD_COLORS, colors);
            }
        } else {
            ComponentPlacerData.clearUnitId(stack);
        }
        stack.set(setting, value);
        memoryCard.notifyUser(this.getPlayer(), MemoryCardMessages.SETTINGS_LOADED);
    }

    private void handleExecute() {
        if (!this.isServerSide() || !(this.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        PlacementService.Result result = PlacementService.place(player, this.host);
        if (this.host.getLinkStatus().connected() && !result.missingMaterial().isEmpty()) {
            AEItemKey key = AEItemKey.of(result.missingMaterial());
            if (key != null && this.getLocator() != null) {
                CraftAmountMenu.open(player, this.getLocator(), key, result.missingAmount());
                return;
            }
        }
        player.displayClientMessage(Component.translatable("message.ae2_batchcraft.component_placer.result",
                result.placed(), result.occupied(), result.materialFailed(), result.placementFailed()), false);
        ILinkStatus linkStatus = this.host.getLinkStatus();
        if (result.materialFailed() > 0 && !linkStatus.connected()) {
            Component reason = linkStatus.statusDescription();
            player.displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.component_placer.network_unavailable",
                    reason != null ? reason : Component.translatable(
                            "gui.ae2_batchcraft.component_placer.disconnected")), false);
        }
    }

    /* 应用服务端快照，不触发客户端操作或再次发送同步包。 */
    public void change(Direction direction, DataModel model, boolean paired, @Nullable Component linkError) {
        this.direction = direction;
        this.model = model;
        this.paired = paired;
        this.linkError = linkError;
    }
}
