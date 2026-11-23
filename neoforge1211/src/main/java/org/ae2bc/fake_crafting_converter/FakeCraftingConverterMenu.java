package org.ae2bc.fake_crafting_converter;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.util.ConfigMenuInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.Ae2bcMod;
import org.jspecify.annotations.NonNull;

public final class FakeCraftingConverterMenu extends AEBaseMenu {
    public static final SlotSemantic INPUT_MARKERS = SlotSemantics.register(
            "ae2_batchcraft_fake_input_markers", false
    );
    public static final SlotSemantic OUTPUT_MARKERS = SlotSemantics.register(
            "ae2_batchcraft_fake_output_markers", false
    );
    public static final MenuType<FakeCraftingConverterMenu> TYPE = MenuTypeBuilder
            .create(FakeCraftingConverterMenu::new, FakeCraftingConverterBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2_batchcraft.fake_crafting_converter"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    Ae2bcMod.MOD_ID, "fake_crafting_converter"
            ));

    private final FakeCraftingConverterBlockEntity converter;

    public FakeCraftingConverterMenu(
            MenuType<?> menuType, int id, Inventory playerInventory,
            FakeCraftingConverterBlockEntity converter
    ) {
        super(menuType, id, playerInventory, converter);
        this.converter = converter;
        ConfigMenuInventory markers = converter.getMarkers().createMenuWrapper();
        for (int column = 0; column < FakeCraftingConverterBlockEntity.COLUMN_COUNT; column++) {
            this.addMarkerSlot(markers, column, column, INPUT_MARKERS);
        }
        for (int column = 0; column < FakeCraftingConverterBlockEntity.COLUMN_COUNT; column++) {
            int markerSlot = FakeCraftingConverterBlockEntity.COLUMN_COUNT + column;
            this.addMarkerSlot(markers, markerSlot,
                    FakeCraftingConverterBlockEntity.COLUMN_COUNT + column, OUTPUT_MARKERS);
        }
        this.createPlayerInventorySlots(playerInventory);
    }

    private void addMarkerSlot(
            ConfigMenuInventory markers, int markerSlot, int stockSlot, SlotSemantic semantic
    ) {
        this.addSlot(new FakeCraftingConverterMarkerSlot(
                this.converter.getStock(), markers, stockSlot, markerSlot, this.converter
        ), semantic);
    }

    /* 服务端拦截标记槽点击，避免未标记槽进入原版库存插入流程。 */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.PICKUP
                && slotId >= 0
                && slotId < this.slots.size()
                && this.getSlot(slotId) instanceof FakeCraftingConverterMarkerSlot markerSlot) {
            AEItemKey marker = markerSlot.getMarkerKey();
            if (button == 0 && marker == null && !this.getCarried().isEmpty()) {
                this.converter.setMarker(markerSlot.getMarkerSlot(), AEItemKey.of(this.getCarried()));
                this.broadcastChanges();
                return;
            }
            if (button == 1 && marker != null) {
                this.converter.setMarker(markerSlot.getMarkerSlot(), null);
                this.broadcastChanges();
                return;
            }
            if (button == 0 && marker != null && !this.getCarried().isEmpty()) {
                ItemStack carried = this.getCarried().copy();
                this.insertPlayerStack(markerSlot, carried);
                this.setCarried(carried);
                this.broadcastChanges();
                return;
            }
            if (this.getCarried().isEmpty()) {
                ItemStack available = markerSlot.previewExtraction();
                int amount = button == 0 ? available.getCount() : (available.getCount() + 1) / 2;
                this.setCarried(markerSlot.remove(amount));
                this.broadcastChanges();
                return;
            }
            return;
        }
        if (clickType == ClickType.SWAP && (button >= 0 && button < 9 || button == 40)
                && slotId >= 0 && slotId < this.slots.size()
                && this.getSlot(slotId) instanceof FakeCraftingConverterMarkerSlot markerSlot) {
            ItemStack hotbar = player.getInventory().getItem(button).copy();
            if (!hotbar.isEmpty()) {
                this.insertPlayerStack(markerSlot, hotbar);
                player.getInventory().setItem(button, hotbar);
                this.broadcastChanges();
                return;
            }
            player.getInventory().setItem(button, markerSlot.remove(Integer.MAX_VALUE));
            this.broadcastChanges();
            return;
        }
        if (slotId >= 0 && slotId < this.slots.size()
                && this.getSlot(slotId) instanceof FakeCraftingConverterMarkerSlot markerSlot) {
            if (clickType == ClickType.QUICK_MOVE) {
                this.quickMoveStack(player, slotId);
            } else if (clickType == ClickType.THROW && this.getCarried().isEmpty() && !this.isClientSide()) {
                player.drop(markerSlot.remove(button == 0 ? 1 : Integer.MAX_VALUE), true);
            } else if (clickType == ClickType.CLONE && player.getAbilities().instabuild && this.getCarried().isEmpty()) {
                ItemStack stack = markerSlot.previewExtraction();
                this.setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(stack.getMaxStackSize()));
            }
            // 禁止原版交换、拖拽等路径将 AE2 包装物取到玩家背包。
            this.broadcastChanges();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /* 每次快捷取出一组实物，按玩家背包实际接收量扣减大库存。 */
    @Override
    public @NonNull ItemStack quickMoveStack(Player player, int slotIndex) {
        if (!(this.getSlot(slotIndex) instanceof FakeCraftingConverterMarkerSlot markerSlot)) {
            return super.quickMoveStack(player, slotIndex);
        }
        if (this.isClientSide() || !markerSlot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack offered = markerSlot.previewExtraction();
        int amount = offered.getCount();
        if (this.moveItemStackTo(offered, FakeCraftingConverterBlockEntity.STOCK_SLOT_COUNT, this.slots.size(), true)) {
            markerSlot.remove(amount - offered.getCount());
            this.broadcastChanges();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(@NonNull ItemStack stack, @NonNull Slot slot) {
        return !(slot instanceof FakeCraftingConverterMarkerSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    /* 玩家点击已标记槽时，只扣除外部入口实际接受的数量，余量保持原名。 */
    private void insertPlayerStack(FakeCraftingConverterMarkerSlot slot, ItemStack stack) {
        AEItemKey marker = slot.getMarkerKey();
        if (marker == null || !marker.matches(stack)) {
            return;
        }
        int row = slot.getMarkerSlot() % FakeCraftingConverterBlockEntity.COLUMN_COUNT;
        long inserted = this.converter.insertExternal(row, marker, stack.getCount(), Actionable.MODULATE);
        stack.shrink((int) inserted);
    }

    /* 快捷移动同样走外部入口，由 AE2 按实际接受数量扣除玩家库存。 */
    @Override
    protected int transferStackToMenu(ItemStack input) {
        AEItemKey key = AEItemKey.of(input);
        if (key == null) {
            return 0;
        }
        return (int) this.converter.getStorage().insert(key, input.getCount(),
                Actionable.MODULATE, IActionSource.ofPlayer(this.getPlayer()));
    }

    @Override
    public void setFilter(int slotIndex, ItemStack item) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return;
        }
        if (this.getSlot(slotIndex) instanceof FakeCraftingConverterMarkerSlot markerSlot) {
            AEItemKey key = item.isEmpty() ? null : AEItemKey.of(item);
            this.converter.setMarker(markerSlot.getMarkerSlot(), key);
            this.broadcastChanges();
            return;
        }
        super.setFilter(slotIndex, item);
    }

}
