package org.ae2bc.part.resource_intake;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuLocators;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterMenu;

public final class Pp2mpResourceIntakeMenu extends AEBaseMenu {
    public static final MenuType<Pp2mpResourceIntakeMenu> TYPE = MenuTypeBuilder
            .create(Pp2mpResourceIntakeMenu::new, Pp2mpResourceIntakePart.class)
            .withMenuTitle(part -> part.getPartItem().asItem().getDescription())
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pp2mp_resource_intake"));
    private static final String SET_ACTIVE_PULL = "SetActivePull";
    private static final String SET_DISTRIBUTION = "SetDistribution";
    private static final String SET_EXTRACTION_INTERVAL = "SetExtractionInterval";
    private static final String SET_EXTRACTION_QUANTITY = "SetExtractionQuantity";
    private static final String OPEN_PROVIDER_FILTER = "OpenProviderFilter";
    private final Pp2mpResourceIntakePart part;

    public Pp2mpResourceIntakeMenu(int id, Inventory playerInventory, Pp2mpResourceIntakePart part) {
        super(TYPE, id, playerInventory, part);
        this.part = part;
        this.registerClientAction(SET_ACTIVE_PULL, Boolean.class, active -> {
            if (this.isServerSide() && active != null) {
                this.part.getData().setActivePull(active);
                this.part.getLogic().resetPullCooldown();
            }
        });
        this.registerClientAction(SET_DISTRIBUTION, Integer.class, mode -> {
            if (this.isServerSide() && mode != null) {
                this.part.getData().setDistributionMode(mode);
            }
        });
        this.registerClientAction(SET_EXTRACTION_INTERVAL, Integer.class, interval -> {
            if (this.isServerSide() && interval != null) {
                this.part.getData().setExtractionInterval(interval);
                this.part.getLogic().resetPullCooldown();
            }
        });
        this.registerClientAction(SET_EXTRACTION_QUANTITY, Integer.class, quantity -> {
            if (this.isServerSide() && quantity != null) {
                this.part.getData().setExtractionQuantity(quantity);
                this.part.getLogic().resetPullCooldown();
            }
        });
        this.registerClientAction(Pp2mpResourceIntakeMenu.OPEN_PROVIDER_FILTER, () -> {
            if (this.isServerSide()) {
                MenuOpener.open(Pp2mpProviderFilterMenu.TYPE, this.getPlayer(), MenuLocators.forPart(this.part));
            }
        });
    }

    /* 打开资源接收端自身的共享过滤配置。 */
    public void openProviderFilter() {
        this.sendClientAction(Pp2mpResourceIntakeMenu.OPEN_PROVIDER_FILTER);
    }

    public boolean isActivePull() {
        return this.part.getData().isActivePull();
    }

    public void setActivePull(boolean active) {
        this.sendClientAction(SET_ACTIVE_PULL, active);
    }

    public int getDistributionMode() {
        return this.part.getData().getDistributionMode();
    }

    public int getExtractionInterval() {
        return this.part.getData().getExtractionParam().getInterval();
    }

    public void setExtractionInterval(int interval) {
        this.sendClientAction(SET_EXTRACTION_INTERVAL, interval);
    }

    public int getExtractionQuantity() {
        return this.part.getData().getExtractionParam().getQuantity();
    }

    public void setExtractionQuantity(int quantity) {
        this.sendClientAction(SET_EXTRACTION_QUANTITY, quantity);
    }

    /* 将选定的分配模式交给服务端保存和同步。 */
    public void setDistributionMode(int mode) {
        this.sendClientAction(SET_DISTRIBUTION, mode);
    }
}
