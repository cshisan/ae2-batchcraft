package org.ae2bc.part.output;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuLocators;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.constant.Pp2mpOutputKeys;
import org.ae2bc.core.part.OutputConfig;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterMenu;

@Getter
public final class Pp2mpOutputMenu extends AEBaseMenu {
    public static final MenuType<Pp2mpOutputMenu> TYPE = MenuTypeBuilder
            .create(Pp2mpOutputMenu::new, Pp2mpOutputPart.class)
            .withMenuTitle(part -> part.getPartItem().asItem().getDescription())
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pp2mp_output"));
    private static final String RESET_TASK_STATE = "ResetTaskState";
    private static final String OPEN_PROVIDER_FILTER = "OpenProviderFilter";
    private final Pp2mpOutputPart part;

    public Pp2mpOutputMenu(int id, Inventory playerInventory, Pp2mpOutputPart part) {
        super(TYPE, id, playerInventory, part);
        this.part = part;
        this.registerClientAction(Pp2mpOutputKeys.OUTPUT_CONFIG, OutputConfig.class, config -> {
            if (this.isServerSide()) {
                this.part.getData().update(config);
                part.wakeProductExtractor();
            }
        });
        this.registerClientAction(Pp2mpOutputKeys.OUTPUT_SYNCED_ENABLED, Boolean.class, synced -> {
            if (this.isServerSide()) {
                this.part.getData().update(() -> this.part.getData().setConfigSyncSwitch(synced));
                this.part.refreshConfigRegister();
            }
        });
        this.registerClientAction(RESET_TASK_STATE, () -> {
            if (this.isServerSide()) {
                this.part.getLogic().resetTaskState();
            }
        });
        this.registerClientAction(OPEN_PROVIDER_FILTER, () -> {
            if (this.isServerSide()) {
                MenuOpener.open(Pp2mpProviderFilterMenu.TYPE, this.getPlayer(), MenuLocators.forPart(this.part));
            }
        });
    }

    public boolean isSyncSwitch() {
        return this.part.getData().isConfigSyncSwitch();
    }

    public OutputConfig getConfig() {
        return this.part.getData().getConfig();
    }


    public void configChange(OutputConfig config) {
        this.sendClientAction(Pp2mpOutputKeys.OUTPUT_CONFIG, config);
    }

    public void switchChange(boolean synced) {
        this.sendClientAction(Pp2mpOutputKeys.OUTPUT_SYNCED_ENABLED, synced);
    }

    public void resetTaskState() {
        this.sendClientAction(RESET_TASK_STATE);
    }

    public void openProviderFilter() {
        this.sendClientAction(OPEN_PROVIDER_FILTER);
    }
}
