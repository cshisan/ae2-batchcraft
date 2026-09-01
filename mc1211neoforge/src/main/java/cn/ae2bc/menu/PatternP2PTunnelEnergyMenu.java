package cn.ae2bc.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.logic.EnergyDistributionMode;
import cn.ae2bc.logic.PatternP2PEnergyGridService;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

public final class PatternP2PTunnelEnergyMenu extends AEBaseMenu {
    private static final String SET_PULL_ENABLED = "setPullEnabled";
    private static final String SET_ENERGY_DISTRIBUTION_MODE = "setEnergyDistributionMode";

    public static final MenuType<PatternP2PTunnelEnergyMenu> TYPE = MenuTypeBuilder
            .create(PatternP2PTunnelEnergyMenu::new, PatternP2PTunnelEnergyPart.class)
            .withMenuTitle(part -> part.getPartItem().asItem().getDescription())
            .build(ResourceLocation.fromNamespaceAndPath(
                    Ae2bcMod.MOD_ID, "pattern_p2p_tunnel_energy"));

    private final PatternP2PTunnelEnergyPart host;

    @GuiSync(0)
    public boolean pullEnabled;
    @GuiSync(1)
    public int pullInterval = PatternP2PTunnelEnergyPart.PULL_INTERVAL;
    @GuiSync(2)
    public EnergyDistributionMode energyDistributionMode = EnergyDistributionMode.EVEN;

    public PatternP2PTunnelEnergyMenu(int id, Inventory playerInventory, PatternP2PTunnelEnergyPart host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        registerClientAction(SET_PULL_ENABLED, Boolean.class, this::handleSetPullEnabled);
        registerClientAction(SET_ENERGY_DISTRIBUTION_MODE, EnergyDistributionMode.class,
                this::handleSetEnergyDistributionMode);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            pullEnabled = host.isPullEnabled();
            pullInterval = host.getPullInterval();
            var grid = host.getMainNode().getGrid();
            energyDistributionMode = grid == null ? EnergyDistributionMode.EVEN
                    : grid.getService(PatternP2PEnergyGridService.class).getGlobalEnergyDistributionMode();
        }
        super.broadcastChanges();
    }

    public void setPullEnabled(boolean enabled) {
        pullEnabled = enabled;
        sendClientAction(SET_PULL_ENABLED, enabled);
    }

    public void setEnergyDistributionMode(EnergyDistributionMode mode) {
        energyDistributionMode = mode;
        sendClientAction(SET_ENERGY_DISTRIBUTION_MODE, mode);
    }

    private void handleSetPullEnabled(Boolean enabled) {
        if (isServerSide() && enabled != null) {
            host.setPullEnabled(enabled);
        }
    }

    private void handleSetEnergyDistributionMode(EnergyDistributionMode mode) {
        if (isServerSide() && mode != null) {
            var grid = host.getMainNode().getGrid();
            if (grid != null) {
                grid.getService(PatternP2PEnergyGridService.class).setGlobalEnergyDistributionMode(mode);
            }
        }
    }
}
