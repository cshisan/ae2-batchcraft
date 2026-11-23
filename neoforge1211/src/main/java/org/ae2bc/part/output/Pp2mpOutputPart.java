package org.ae2bc.part.output;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.me.helpers.MachineSource;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.ae2bc.core.screen.Route;
import org.ae2bc.part.InputChildrenPart;
import org.ae2bc.part.Pp2mpPart;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.general.Pp2mpReceiveTarget;
import org.ae2bc.part.general.Pp2mpTickWake;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterHost;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakePart;
import org.ae2bc.registry.ItemRegistry;
import org.ae2bc.storage.ReceiveInventory;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class Pp2mpOutputPart extends P2PTunnelPart<Pp2mpInputPart>
        implements Pp2mpPart, InputChildrenPart, Pp2mpProviderFilterHost {
    private final @Getter Pp2mpOutputData data;
    private final @Getter Pp2mpProviderFilterData providerFilter;
    private final @Getter Pp2mpOutputLogic logic;
    private final @Getter ReceiveInventory receiveInventory;
    private final Pp2mpReceiveTarget receiveTarget;
    private final Pp2mpTickWake tickWake = new Pp2mpTickWake();
    private @Nullable Pp2mpInputPart boundInput;

    public Pp2mpOutputPart(IPartItem<?> partItem) {
        super(partItem);
        super.getMainNode().setFlags();
        this.data = new Pp2mpOutputData(
                () -> this.getHost().markForSave(),
                () -> this.getHost().markForUpdate(),
                this::wakeProductExtractor
        );
        this.providerFilter = new Pp2mpProviderFilterData(
                partItem.asItem(), this::markForSaveAndUpdate
        );
        this.logic = new Pp2mpOutputLogic(this, this.data);
        this.receiveTarget = new Pp2mpReceiveTarget(() -> this.getMainNode().getGrid(), this::getFrequency);
        this.receiveInventory = new ReceiveInventory(
                this.receiveTarget::findInventory,
                this.receiveTarget::findStrategies,
                this.logic::accessReceive,
                this.logic::onReturnedInserted,
                new MachineSource(super.getMainNode()::getNode),
                this::wakeProductExtractor,
                this.logic::beginReturn, this.logic::endReturn
        );

        // 添加产物提取服务
        super.getMainNode().addService(IGridTickable.class, new Pp2mpOutputTicker(this));
    }

    /* 寻找产物返回过滤 */
    @Override
    public boolean isOutput() {
        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        return Pp2mpOutputModel.MODELS.getModel(super.isPowered(), super.isActive());
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.isClientSide()) {
            MenuOpener.open(Pp2mpOutputMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        // 副手或者非内存卡则交给父类处理
        if (hand == InteractionHand.OFF_HAND || !(heldItem.getItem() instanceof IMemoryCard memoryCard)) {
            return false;
        }

        if (isClientSide()) {
            return true;
        }

        // 潜行右键则不处理
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return true;
        }
        Item storedType = heldItem.get(AEComponents.EXPORTED_P2P_TYPE);
        Short storedFrequency = heldItem.get(AEComponents.EXPORTED_P2P_FREQUENCY);
        if (storedType != ItemRegistry.PP2MP_INPUT.get() || storedFrequency == null) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }

        // 设置频率
        this.setFrequency(this, storedFrequency, data, data::isTaskActive, () -> {
            super.importSettings(SettingsFrom.MEMORY_CARD, heldItem.getComponents(), player);
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        });
        return true;
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.data.readFromNBT(data, registries);
        this.providerFilter.readFromNBT(data, registries);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        this.data.writeToNBT(data, registries);
        this.providerFilter.writeToNBT(data, registries);
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean dataChanged = this.data.readFromStream(data);
        boolean filterChanged = this.providerFilter.readFromStream(data);
        return dataChanged || filterChanged || changed;
    }

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        this.data.writeToStream(data);
        this.providerFilter.writeToStream(data);
    }


    /* 为统一供应过滤菜单提供宿主和主页面同步状态。 */
    @Override
    public AEBasePart getProviderFilterPart() {
        return this;
    }

    @Override
    public boolean isProviderFilterSynced() {
        return this.data.isConfigSyncSwitch();
    }

    @Override
    public MenuType<?> getProviderFilterParentMenu() {
        return Pp2mpOutputMenu.TYPE;
    }

    @Override
    public Route getProviderFilterParentRoute() {
        return Route.RESOURCE_INTAKE;
    }

    /* 供应规则始终读取本地副本，现有同步开关控制是否由资源接收端覆盖。 */
    @Override
    public Pp2mpProviderFilterData getEffectiveProviderFilter() {
        this.refreshProviderFilter();
        return this.providerFilter;
    }

    /* 按同一 AE 网络和频率同步供应配置；没有资源接收端时保留最后的本地配置。 */
    public void refreshProviderFilter() {
        if (this.isClientSide() || !this.isProviderFilterSynced()) {
            return;
        }
        IGrid grid = this.grid();
        if (grid != null) {
            Pp2mpResourceIntakePart resourceIntake = Pp2mpGridService.get(grid).findResourceIntake(this.getFrequency());
            if (resourceIntake != null) {
                this.providerFilter.copyFrom(resourceIntake.getProviderFilter());
            }
        }
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean adventureMode) {
        super.addAdditionalDrops(drops, adventureMode);
        this.providerFilter.addDrops(drops);
        for (Map<AEKey, Long> group : this.data.getPendingInputs().values()) {
            this.addStoredDrops(group, drops);
        }
        this.addStoredDrops(this.data.getInputRecovery(), drops);
        this.addStoredDrops(this.data.getExtractionRecovery(), drops);
    }

    /* 拆除时按 AE 资源类型的掉落规则处理端点实际持有的资源。 */
    private void addStoredDrops(Map<AEKey, Long> resources, List<ItemStack> drops) {
        Level level = this.getLevel();
        if (level == null) {
            return;
        }
        for (Map.Entry<AEKey, Long> entry : resources.entrySet()) {
            entry.getKey().addDrops(entry.getValue(), drops, level, this.getBlockEntity().getBlockPos());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.providerFilter.upgrades().clear();
        this.data.getPendingInputs().clear();
        this.data.getInputRecovery().clear();
        this.data.getExtractionRecovery().clear();
    }

    public void wakeProductExtractor() {
        this.tickWake.alert(super.getMainNode());
    }

    void resetTickWake() {
        this.tickWake.reset();
    }

    @Override
    public void onTunnelConfigChange() {
        super.onTunnelConfigChange();
        this.clearReceiveTargetCache();
        IGrid grid = super.getMainNode().getGrid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
        this.refreshConfigRegister();
        this.wakeProductExtractor();
    }

    @Override
    public void onTunnelNetworkChange() {
        super.onTunnelNetworkChange();
        this.clearReceiveTargetCache();
        IGrid grid = super.getMainNode().getGrid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
        this.refreshConfigRegister();
        this.wakeProductExtractor();
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        this.clearReceiveTargetCache();
        IGrid grid = super.getMainNode().getGrid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
        this.refreshConfigRegister();
        this.wakeProductExtractor();
    }

    @Override
    public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        this.wakeProductExtractor();
    }

    @Override
    public void removeFromWorld() {
        this.logic.clearTarget();
        this.receiveTarget.clear();
        super.removeFromWorld();
    }

    public void clearReceiveTargetCache() {
        this.receiveTarget.clear();
    }

    public void markForSaveAndUpdate() {
        this.getHost().markForSave();
        this.getHost().markForUpdate();
        this.wakeProductExtractor();
    }

    public void refreshConfigRegister() {
        this.refreshProviderFilter();
        if (this.boundInput != null) {
            this.boundInput.getData().eventSource().unregister(this.data);
            this.boundInput = null;
        }

        IGrid grid = super.getMainNode().getGrid();
        if (grid == null || super.getFrequency() == 0) {
            return;
        }

        Pp2mpInputPart input = this.findInput(super.getFrequency(), grid);
        if (input == null) {
            return;
        }
        this.boundInput = input;

        input.getData().eventSource().register(this.data);
        if (!this.data.isConfigSyncSwitch()) {
            this.wakeProductExtractor();
            return;
        }
        this.data.apply(input.getData().getConfig());
        this.wakeProductExtractor();
    }
}
