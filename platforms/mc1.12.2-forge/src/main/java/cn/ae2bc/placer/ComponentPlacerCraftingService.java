package cn.ae2bc.placer;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.storage.data.IAEItemStack;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.menu.ComponentPlacerCraftAmountMenu;
import cn.ae2bc.menu.ComponentPlacerCraftConfirmMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Future;

/** Bridges the AE2 crafting amount and confirmation screens for either player hand. */
public final class ComponentPlacerCraftingService {
    private static final Logger LOGGER = LogManager.getLogger(Ae2bcMod.MOD_ID);

    private ComponentPlacerCraftingService() {
    }

    public static void request(EntityPlayerMP player, ComponentPlacerCraftAmountMenu menu,
                               int amount, boolean autoStart) {
        if (amount <= 0 || !menu.canRequestCraft()) return;
        IAEItemStack target = menu.getItemToCraft();
        if (target == null) return;

        Future<ICraftingJob> job = null;
        try {
            IGrid grid = menu.getGrid();
            if (grid == null) return;
            IAEItemStack request = target.copy();
            request.setStackSize(amount);
            ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
            job = craftingGrid.beginCraftingJob(
                    menu.getWorld(), grid, menu.getActionSrc(), request, null);

            EnumHand hand = menu.getHand();
            int slot = hand == EnumHand.MAIN_HAND ? player.inventory.currentItem : 40;
            player.openGui(Ae2bcMod.INSTANCE, Ae2bcMod.GUI_COMPONENT_PLACER_CRAFT_CONFIRM,
                    player.world, slot, hand.ordinal(), 0);
            if (!(player.openContainer instanceof ComponentPlacerCraftConfirmMenu)) {
                job.cancel(true);
                return;
            }
            ComponentPlacerCraftConfirmMenu confirmation =
                    (ComponentPlacerCraftConfirmMenu) player.openContainer;
            confirmation.setAutoStart(autoStart);
            confirmation.setJob(job);
            confirmation.detectAndSendChanges();
        } catch (Throwable exception) {
            if (job != null) job.cancel(true);
            LOGGER.error("Failed to open component placer crafting confirmation", exception);
        }
    }
}
