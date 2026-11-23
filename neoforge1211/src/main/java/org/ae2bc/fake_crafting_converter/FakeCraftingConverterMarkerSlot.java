package org.ae2bc.fake_crafting_converter;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.network.ServerboundPacket;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class FakeCraftingConverterMarkerSlot extends AppEngSlot {
    private final ConfigMenuInventory markers;
    private final @Getter int markerSlot;
    private final int stockSlot;
    private final FakeCraftingConverterBlockEntity converter;

    public FakeCraftingConverterMarkerSlot(
            InternalInventory stock,
            ConfigMenuInventory markers,
            int stockSlot,
            int markerSlot,
            FakeCraftingConverterBlockEntity converter
    ) {
        super(stock, stockSlot);
        this.markers = markers;
        this.markerSlot = markerSlot;
        this.stockSlot = stockSlot;
        this.converter = converter;
        this.setNotDraggable();
    }

    public boolean canSetFilterTo(ItemStack stack) {
        AEItemKey key = stack.isEmpty() ? null : AEItemKey.of(stack);
        return this.converter.canSetMarker(this.markerSlot, key)
                && this.markers.isItemValid(this.markerSlot, stack);
    }

    public void setFilterTo(ItemStack stack) {
        if (!this.canSetFilterTo(stack)) {
            return;
        }
        ServerboundPacket packet = new InventoryActionPacket(
                InventoryAction.SET_FILTER, this.index, stack
        );
        PacketDistributor.sendToServer(packet);
    }

    public @Nullable AEItemKey getMarkerKey() {
        GenericStack marker = this.markers.getDelegate().getStack(this.markerSlot);
        return marker != null && marker.what() instanceof AEItemKey itemKey ? itemKey : null;
    }

    /* 标记只作为空槽占位，实物同步到槽位后不再提供半透明图案。 */
    public ItemStack getMarkerDisplayStack() {
        if (!this.getItem().isEmpty()) {
            return ItemStack.EMPTY;
        }
        AEItemKey key = this.getMarkerKey();
        return key == null ? ItemStack.EMPTY : key.toStack();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        // 实际存入由菜单交给外部转换入口，原版拖拽和交换不能直接写入下槽。
        return false;
    }

    /* 大库存的包装物仅用于显示，玩家取到的始终是正常堆叠的实物。 */
    public ItemStack previewExtraction() {
        return this.getInventory().extractItem(this.stockSlot, Integer.MAX_VALUE, true);
    }

    @Override
    public boolean mayPickup(Player player) {
        return this.isSlotEnabled() && !this.previewExtraction().isEmpty();
    }

    @Override
    public @NonNull ItemStack remove(int amount) {
        return this.isSlotEnabled()
                ? this.getInventory().extractItem(this.stockSlot, amount, false) : ItemStack.EMPTY;
    }

    @Override
    public @Nullable List<Component> getCustomTooltip(ItemStack carriedItem) {
        if (!this.getItem().isEmpty()) {
            return null;
        }
        AEItemKey marker = this.getMarkerKey();
        return marker == null ? null : List.of(marker.toStack().getHoverName());
    }
}
