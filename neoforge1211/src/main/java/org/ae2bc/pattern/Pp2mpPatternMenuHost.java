package org.ae2bc.pattern;

import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.MEStorage;
import appeng.api.storage.SupplierStorage;
import appeng.api.util.IConfigManager;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class Pp2mpPatternMenuHost extends ItemMenuHost<Pp2mpPatternItem>
        implements IPatternTerminalMenuHost, IPatternTerminalLogicHost, KeyTypeSelectionHost, IActionHost {
    private final PatternEncodingLogic logic;
    private final IConfigManager configManager;
    private final KeyTypeSelection keyTypeSelection;
    private final @Nullable BlockHitResult hitResult;

    public Pp2mpPatternMenuHost(
            Pp2mpPatternItem item,
            Player player,
            ItemMenuHostLocator locator,
            @Nullable BlockHitResult hitResult
    ) {
        super(item, player, locator);
        this.hitResult = hitResult;
        this.logic = new PatternEncodingLogic(this);
        this.logic.setMode(EncodingMode.PROCESSING);
        this.keyTypeSelection = new KeyTypeSelection(() -> {
        }, keyType -> true);
        this.configManager = IConfigManager.builder(() -> {
                })
                .registerSetting(Settings.SORT_BY, SortOrder.NAME)
                .registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
                .registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING)
                .build();
    }

    @Override
    public PatternEncodingLogic getLogic() {
        return this.logic;
    }

    @Override
    public Level getLevel() {
        return this.getPlayer().level();
    }

    @Override
    public void markForSave() {
    }

    @Override
    public MEStorage getInventory() {
        return new SupplierStorage(() -> {
            IGrid grid = this.getGrid();
            return grid == null ? null : grid.getStorageService().getInventory();
        });
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return this.getActionableNode() == null ? ILinkStatus.ofDisconnected() : ILinkStatus.ofConnected();
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return this.keyTypeSelection;
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (this.getLocator() != null) {
            MenuOpener.open(Pp2mpEncodingTermMenu.TYPE, player, this.getLocator(), true);
        }
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return this.getItemStack().copyWithCount(1);
    }

    public boolean wasOpenedOnBlock() {
        return this.hitResult != null;
    }

    /* 库存和能源共用同一个有效节点，避免仅能读取库存却无法执行存取。 */
    @Nullable
    public IGrid getGrid() {
        IGridNode node = this.getActionableNode();
        return node == null ? null : node.getGrid();
    }

    /* 向 AE2 提供绑定方块的操作节点，沿用距离和节点状态检查。 */
    @Override
    @Nullable
    public IGridNode getActionableNode() {
        if (this.hitResult == null) {
            return null;
        }

        double sqr = this.getPlayer().distanceToSqr(
                this.hitResult.getBlockPos().getX() + 0.5,
                this.hitResult.getBlockPos().getY() + 0.5,
                this.hitResult.getBlockPos().getZ() + 0.5
        );
        if (sqr > 64.0) {
            return null;
        }

        Level level = this.getPlayer().level();
        IGridNode node = GridHelper.getExposedNode(
                level, this.hitResult.getBlockPos(),
                this.hitResult.getDirection()
        );
        if (node == null || !node.isActive()) {
            return null;
        }
        return node;
    }
}
