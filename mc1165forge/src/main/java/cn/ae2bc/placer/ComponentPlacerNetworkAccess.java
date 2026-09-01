package cn.ae2bc.placer;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.Api;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.me.helpers.PlayerSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/** Keeps one verified wireless-network context for a component placer menu. */
public final class ComponentPlacerNetworkAccess {
    private final WirelessTerminalGuiObject terminal;
    private final PlayerSource source;
    private final IItemStorageChannel channel;
    private IMEMonitor<IAEItemStack> inventory;

    public ComponentPlacerNetworkAccess(PlayerEntity player, Hand hand, ItemStack placer) {
        int slot = hand == Hand.MAIN_HAND ? player.inventory.selected : 40;
        terminal = new WirelessTerminalGuiObject((ComponentPlacerItem) placer.getItem(), placer, player, slot);
        source = new PlayerSource(player, terminal);
        channel = Api.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    public boolean isConnected() {
        if (!terminal.rangeCheck() || terminal.getActionableNode() == null) {
            inventory = null;
            return false;
        }
        inventory = terminal.getInventory(channel);
        return inventory != null;
    }

    public long count(ItemStack requested, int limit) {
        if (requested.isEmpty() || limit <= 0 || !isConnected()) return 0;
        IAEItemStack request = channel.createStack(requested);
        if (request == null) return 0;
        request.setStackSize(limit);
        IAEItemStack available = inventory.extractItems(request, Actionable.SIMULATE, source);
        return available == null ? 0 : available.getStackSize();
    }

    public ItemStack extractPowered(ItemStack requested, int amount) {
        if (requested.isEmpty() || amount <= 0 || !isConnected()) return ItemStack.EMPTY;
        IAEItemStack request = channel.createStack(requested);
        if (request == null) return ItemStack.EMPTY;
        request.setStackSize(amount);
        IAEItemStack extracted = Api.instance().storage().poweredExtraction(
                terminal, inventory, request, source, Actionable.MODULATE);
        return extracted == null ? ItemStack.EMPTY : extracted.createItemStack();
    }

    public ItemStack insertPowered(ItemStack stack) {
        if (stack.isEmpty() || !isConnected()) return stack;
        IAEItemStack input = channel.createStack(stack);
        if (input == null) return stack;
        IAEItemStack remainder = Api.instance().storage().poweredInsert(
                terminal, inventory, input, source, Actionable.MODULATE);
        return remainder == null || remainder.getStackSize() == 0
                ? ItemStack.EMPTY : remainder.createItemStack();
    }
}
