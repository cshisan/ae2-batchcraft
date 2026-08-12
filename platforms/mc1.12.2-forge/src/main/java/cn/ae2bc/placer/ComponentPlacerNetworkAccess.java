package cn.ae2bc.placer;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.me.helpers.PlayerSource;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** Keeps one verified wireless-network context for a component placer menu. */
public final class ComponentPlacerNetworkAccess {
    private final WirelessTerminalGuiObject terminal;
    private final PlayerSource source;
    private final IItemStorageChannel channel;
    private IMEMonitor<IAEItemStack> inventory;

    public ComponentPlacerNetworkAccess(EntityPlayer player, EnumHand hand, ItemStack placer) {
        int slot = hand == EnumHand.MAIN_HAND ? player.inventory.currentItem : 40;
        terminal = new WirelessTerminalGuiObject((ComponentPlacerItem) placer.getItem(), placer,
                player, player.world, slot, 0, 0);
        source = new PlayerSource(player, terminal);
        channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    public WirelessTerminalGuiObject getTerminal() {
        return terminal;
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
        IAEItemStack extracted = AEApi.instance().storage().poweredExtraction(
                terminal, inventory, request, source, Actionable.MODULATE);
        return extracted == null ? ItemStack.EMPTY : extracted.createItemStack();
    }

    public ItemStack insertPowered(ItemStack stack) {
        if (stack.isEmpty() || !isConnected()) return stack;
        IAEItemStack input = channel.createStack(stack);
        if (input == null) return stack;
        IAEItemStack remainder = AEApi.instance().storage().poweredInsert(
                terminal, inventory, input, source, Actionable.MODULATE);
        return remainder == null || remainder.getStackSize() == 0
                ? ItemStack.EMPTY : remainder.createItemStack();
    }
}
