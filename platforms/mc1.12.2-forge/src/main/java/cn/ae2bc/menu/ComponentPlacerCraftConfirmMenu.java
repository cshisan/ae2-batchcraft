package cn.ae2bc.menu;

import appeng.container.implementations.ContainerCraftConfirm;
import cn.ae2bc.placer.ComponentPlacerNetworkAccess;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;

public final class ComponentPlacerCraftConfirmMenu extends ContainerCraftConfirm {
    private final EnumHand hand;

    public ComponentPlacerCraftConfirmMenu(EntityPlayer player,
                                           EnumHand hand, ComponentPlacerNetworkAccess networkAccess) {
        super(player.inventory, networkAccess.getTerminal());
        this.hand = hand;
    }

    public EnumHand getHand() {
        return hand;
    }
}
