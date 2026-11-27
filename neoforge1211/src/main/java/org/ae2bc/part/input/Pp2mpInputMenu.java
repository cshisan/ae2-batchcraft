package org.ae2bc.part.input;

import appeng.menu.AEBaseMenu;
import appeng.api.networking.IGrid;
import appeng.menu.implementations.MenuTypeBuilder;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.constant.Pp2mpInputKeys;
import org.ae2bc.core.dispatch.TaskAllocationMode;
import org.ae2bc.core.dispatch.TaskSplitMode;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.part.general.Pp2mpGridService;

@Getter
public class Pp2mpInputMenu extends AEBaseMenu {
    public static final MenuType<Pp2mpInputMenu> TYPE = MenuTypeBuilder.create(Pp2mpInputMenu::new, Pp2mpInputPart.class)
            .withMenuTitle(part -> part.getPartItem().asItem().getDescription())
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pp2mp_input"));
    private static final String RESET_TASK_STATE = "ResetTaskState";
    private final Pp2mpInputPart part;

    public Pp2mpInputMenu(int id, Inventory playerInventory, Pp2mpInputPart part) {
        super(TYPE, id, playerInventory, part);
        this.part = part;
        this.registerClientAction(Pp2mpInputKeys.INPUT_CONFIG, InputConfig.class, config -> {
            if (this.isServerSide()) {
                IGrid grid = this.part.grid();
                if (grid != null) {
                    Pp2mpGridService.get(grid).refreshConfigurations();
                }
                this.part.getData().update(config);
            }
        });
        this.registerClientAction(RESET_TASK_STATE, () -> {
            if (this.isServerSide()) {
                this.part.getLogic().resetTaskState();
            }
        });
    }

    public InputConfig getConfig() {
        return this.part.getData().getConfig();
    }

    public void configChange(InputConfig config) {
        this.sendClientAction(Pp2mpInputKeys.INPUT_CONFIG, config);
    }

    public void cycleTaskAllocationMode() {
        InputConfig config = this.getConfig();
        this.configChange(InputConfig.builder(config)
                .taskAllocationMode(config.taskAllocationMode().next())
                .build());
    }

    public TaskAllocationMode getTaskAllocationMode() {
        return this.getConfig().taskAllocationMode();
    }

    public void cycleTaskSplitMode() {
        InputConfig config = this.getConfig();
        this.configChange(InputConfig.builder(config)
                .taskSplitMode(config.taskSplitMode().next())
                .build());
    }

    public TaskSplitMode getTaskSplitMode() {
        return this.getConfig().taskSplitMode();
    }

    public void resetTaskState() {
        this.sendClientAction(RESET_TASK_STATE);
    }
}
