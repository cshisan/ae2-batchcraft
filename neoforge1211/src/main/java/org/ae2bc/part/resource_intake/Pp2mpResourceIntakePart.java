package org.ae2bc.part.resource_intake;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import appeng.parts.p2p.P2PTunnelPart;
import appeng.util.InteractionUtil;
import appeng.util.SettingsFrom;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.ae2bc.core.screen.Route;
import org.ae2bc.part.Pp2mpPart;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterHost;
import org.ae2bc.registry.ItemRegistry;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Getter
public final class Pp2mpResourceIntakePart extends P2PTunnelPart<Pp2mpResourceIntakePart>
        implements Pp2mpPart, Pp2mpProviderFilterHost {
    private final Pp2mpResourceIntakeData data;
    private final Pp2mpResourceIntakeLogic logic;

    public Pp2mpResourceIntakePart(IPartItem<?> partItem) {
        super(partItem);
        super.getMainNode().setFlags();
        this.data = new Pp2mpResourceIntakeData(partItem.asItem(), this::markForSaveAndUpdate, this::onProviderFilterChanged);
        this.logic = new Pp2mpResourceIntakeLogic(this, this.data.getBuffer());
        super.getMainNode().addService(IGridTickable.class, new Pp2mpResourceIntakeTicker(this));
    }

    @Override
    public boolean isOutput() {
        return true;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.isClientSide()) {
            MenuOpener.open(Pp2mpResourceIntakeMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }

    /* 内存卡只加载输入端已有频率，不生成频率或替换资源接收端部件。 */
    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        if (hand == InteractionHand.OFF_HAND || !(heldItem.getItem() instanceof IMemoryCard memoryCard)) {
            return false;
        }
        if (this.isClientSide() || InteractionUtil.isInAlternateUseMode(player)) {
            return true;
        }

        Item storedType = heldItem.get(AEComponents.EXPORTED_P2P_TYPE);
        Short storedFrequency = heldItem.get(AEComponents.EXPORTED_P2P_FREQUENCY);
        if (storedType != ItemRegistry.PP2MP_INPUT.get() || storedFrequency == null || storedFrequency == 0) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }

        super.importSettings(SettingsFrom.MEMORY_CARD, heldItem.getComponents(), player);
        this.onTunnelConfigChange();
        this.markForSaveAndUpdate();
        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        return Pp2mpResourceIntakeModel.MODELS.getModel(super.isPowered(), super.isActive());
    }

    /* 资源接收端持有共享过滤规则，过滤子页面返回资源接收端自己的主界面。 */
    @Override
    public AEBasePart getProviderFilterPart() {
        return this;
    }

    @Override
    public Pp2mpProviderFilterData getProviderFilter() {
        return this.data.getProviderFilter();
    }

    @Override
    public MenuType<?> getProviderFilterParentMenu() {
        return Pp2mpResourceIntakeMenu.TYPE;
    }

    @Override
    public @Nullable Route getProviderFilterParentRoute() {
        return null;
    }

    /* 拆除资源接收端时掉落实际安装的升级卡，标记资源不作为物品掉落。 */
    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean adventureMode) {
        super.addAdditionalDrops(drops, adventureMode);
        this.data.getProviderFilter().addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.data.getProviderFilter().upgrades().clear();
    }

    /* 委托数据对象恢复资源接收端存档。 */
    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        this.data.readFromNBT(tag, registries);
    }

    /* 委托数据对象保存资源接收端配置和库存。 */
    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        this.data.writeToNBT(tag, registries);
    }

    /* 在父类数据之后写入资源接收端配置。 */
    @Override
    public void writeToStream(RegistryFriendlyByteBuf buffer) {
        super.writeToStream(buffer);
        this.data.writeToStream(buffer);
    }

    /* 无论父类状态是否变化，都完整读取资源接收端配置。 */
    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf buffer) {
        boolean changed = super.readFromStream(buffer);
        return this.data.readFromStream(buffer) || changed;
    }

    @Override
    public void onTunnelConfigChange() {
        super.onTunnelConfigChange();
        this.invalidateProviderConfig();
        this.logic.onSourceChanged();
    }

    @Override
    public void onTunnelNetworkChange() {
        super.onTunnelNetworkChange();
        this.invalidateProviderConfig();
        this.logic.onSourceChanged();
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        this.invalidateProviderConfig();
        this.logic.onSourceChanged();
    }

    @Override
    public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChanged(level, pos, neighbor);
        this.logic.onSourceChanged();
    }

    @Override
    public void removeFromWorld() {
        this.logic.clearTarget();
        super.removeFromWorld();
    }

    public void wake() {
        IGrid grid = super.getMainNode().getGrid();
        IGridNode node = super.getMainNode().getNode();
        if (grid != null && node != null) {
            grid.getTickManager().alertDevice(node);
        }
    }

    public void markForSaveAndUpdate() {
        if (this.getHost() != null) {
            this.getHost().markForSave();
            this.getHost().markForUpdate();
        }
        this.wake();
    }

    /* 只在过滤配置变更时通知网络覆盖已开启同步的输出端和单元管理器。 */
    private void onProviderFilterChanged() {
        this.markForSaveAndUpdate();
        IGrid grid = this.grid();
        if (grid != null) {
            Pp2mpGridService.get(grid).resourceIntakeConfigChanged();
        }
    }

    /* 频率或网络改变后重建供应配置来源，实际同步由网络 tick 完成。 */
    private void invalidateProviderConfig() {
        IGrid grid = this.grid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
    }
}
