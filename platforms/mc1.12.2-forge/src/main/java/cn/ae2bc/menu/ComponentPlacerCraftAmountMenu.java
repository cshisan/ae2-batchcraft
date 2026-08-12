package cn.ae2bc.menu;

import appeng.container.implementations.ContainerCraftAmount;
import cn.ae2bc.placer.ComponentPlacerItem;
import cn.ae2bc.placer.ComponentPlacerNetworkAccess;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

public final class ComponentPlacerCraftAmountMenu extends ContainerCraftAmount {
    private final EntityPlayer player;
    private final EnumHand hand;
    private final ItemStack placer;
    private final ComponentPlacerNetworkAccess networkAccess;

    public ComponentPlacerCraftAmountMenu(EntityPlayer player, EnumHand hand,
                                          ComponentPlacerNetworkAccess networkAccess) {
        super(player.inventory, networkAccess.getTerminal());
        this.player = player;
        this.hand = hand;
        this.placer = player.getHeldItem(hand);
        this.networkAccess = networkAccess;
    }

    public boolean canRequestCraft() {
        return player.getHeldItem(hand) == placer
                && placer.getItem() instanceof ComponentPlacerItem
                && ComponentPlacerItem.hasCraftingCard(placer)
                && networkAccess.isConnected();
    }

    public EnumHand getHand() {
        return hand;
    }
}
