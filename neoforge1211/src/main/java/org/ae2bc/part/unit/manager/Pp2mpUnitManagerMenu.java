package org.ae2bc.part.unit.manager;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuLocators;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.constant.Pp2mpUnitManagerKeys;
import org.ae2bc.core.part.UnitConfig;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterMenu;

@Getter
public final class Pp2mpUnitManagerMenu extends AEBaseMenu {
    public static final MenuType<Pp2mpUnitManagerMenu> TYPE = MenuTypeBuilder.create(
            Pp2mpUnitManagerMenu::new, Pp2mpUnitManagerPart.class
    ).withMenuTitle(part -> part.getPartItem().asItem().getDescription()).buildUnregistered(
            ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pp2mp_unit_manager")
    );
    private static final String OPEN_PROVIDER_FILTER = "OpenProviderFilter";
    private static final String RESET_TASK_STATE = "ResetTaskState";
    private final Pp2mpUnitManagerPart part;

    public Pp2mpUnitManagerMenu(int id, Inventory playerInventory, Pp2mpUnitManagerPart part) {
        super(TYPE, id, playerInventory, part);
        this.part = part;
        this.registerClientAction(OPEN_PROVIDER_FILTER, () -> {
            if (this.isServerSide()) {
                MenuOpener.open(Pp2mpProviderFilterMenu.TYPE, this.getPlayer(), MenuLocators.forPart(this.part));
            }
        });

        this.registerClientAction(Pp2mpUnitManagerKeys.OUTPUT_CONFIG, UnitConfig.class, config -> {
            if (this.isServerSide() && !this.part.getData().isSyncEnabled()) {
                this.part.getData().update(config);
            }
        });
        this.registerClientAction(Pp2mpUnitManagerKeys.UNIT_MANAGER_SYNCED_ENABLED, Boolean.class, synced -> {
            if (this.isServerSide()) {
                this.part.getData().update(() -> this.part.getData().setSyncEnabled(synced));
                this.part.refreshConfigEventRegister();
            }
        });
        this.registerClientAction(RESET_TASK_STATE, () -> {
            if (this.isServerSide()) {
                this.part.getLogic().resetTaskState();
            }
        });
    }

    /* 打开管理器绑定端口所采用的供应过滤配置。 */
    public void openProviderFilter() {
        this.sendClientAction(OPEN_PROVIDER_FILTER);
    }

    public boolean isSyncSwitch() {
        return this.part.getData().isSyncEnabled();
    }

    public UnitConfig getConfig() {
        return this.part.getData().getConfig();
    }

    public void configChange(UnitConfig config) {
        this.sendClientAction(Pp2mpUnitManagerKeys.OUTPUT_CONFIG, config);
    }

    public void switchChange(boolean synced) {
        this.sendClientAction(Pp2mpUnitManagerKeys.UNIT_MANAGER_SYNCED_ENABLED, synced);
    }

    public void resetTaskState() {
        this.sendClientAction(RESET_TASK_STATE);
    }
}
