package org.ae2bc.part.unit.manager;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;
import appeng.items.parts.ColoredPartItem;
import appeng.me.helpers.MachineSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import appeng.parts.networking.CablePart;
import appeng.util.InteractionUtil;
import appeng.util.SettingsFrom;
import lombok.Getter;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.ae2bc.core.constant.Pp2mpUnitManagerKeys;
import org.ae2bc.core.constant.UnitManagerConstant;
import org.ae2bc.core.part.UnitConfig;
import org.ae2bc.core.screen.Route;
import org.ae2bc.part.InputChildrenPart;
import org.ae2bc.part.Pp2mpKey;
import org.ae2bc.part.Pp2mpPart;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.general.Pp2mpReceiveTarget;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterHost;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakePart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitProviderPortPart;
import org.ae2bc.registry.ItemRegistry;
import org.ae2bc.registry.component.Pp2mpUnitManagerRegistry;
import org.ae2bc.storage.PendingMaterial;
import org.ae2bc.storage.ReceiveInventory;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.function.Predicate;

public class Pp2mpUnitManagerPart extends CablePart
        implements Pp2mpPart, InputChildrenPart, Pp2mpProviderFilterHost {
    private final @Getter Pp2mpUnitManagerData data;
    private final @Getter Pp2mpUnitManagerLogic logic;
    private final Pp2mpReceiveTarget receiveTarget;
    private final @Getter ReceiveInventory receiveInventory;
    private @Getter short frequency;
    private @Getter short unitId;
    private @Nullable Pp2mpInputPart boundInput;
    private boolean refreshingConfig;

    public Pp2mpUnitManagerPart(ColoredPartItem<?> partItem) {
        super(partItem);
        this.data = new Pp2mpUnitManagerData(
                partItem.asItem(),
                () -> this.getHost().markForSave(),
                () -> this.getHost().markForUpdate()
        );
        this.logic = new Pp2mpUnitManagerLogic(super.getMainNode(), this);
        this.data.eventSource().register(this::applyPortConfig);
        this.receiveTarget = new Pp2mpReceiveTarget(
                () -> this.getMainNode().getGrid(),
                this::getFrequency
        );
        this.receiveInventory = new ReceiveInventory(
                this.receiveTarget::findInventory,
                this.receiveTarget::findStrategies,
                this.logic::canAcceptReturned,
                this.logic::onReturnedInserted,
                new MachineSource(super.getMainNode()::getNode),
                this.logic::alertPendingRetry,
                this.logic::beginReturn, this.logic::endReturn
        );
        super.getMainNode().addService(IGridTickable.class, new Pp2mpUnitManagerTicker(this.logic));
    }

    /* 为统一供应过滤菜单提供管理器本地配置及返回入口。 */
    @Override
    public AEBasePart getProviderFilterPart() {
        return this;
    }

    @Override
    public Pp2mpProviderFilterData getProviderFilter() {
        return this.data.getProviderFilter();
    }

    @Override
    public boolean isProviderFilterSynced() {
        return this.data.isSyncEnabled();
    }

    @Override
    public MenuType<?> getProviderFilterParentMenu() {
        return Pp2mpUnitManagerMenu.TYPE;
    }

    @Override
    public Route getProviderFilterParentRoute() {
        return Route.RESOURCE_INTAKE;
    }

    /* 共用已有同步开关，供应规则由资源接收端覆盖到管理器的本地副本。 */
    @Override
    public Pp2mpProviderFilterData getEffectiveProviderFilter() {
        IGrid grid = this.grid();
        if (grid != null && !this.isClientSide()) {
            Pp2mpGridService.get(grid).refreshConfigurations();
        }
        return this.data.getProviderFilter();
    }

    /* 没有供应源时保留已保存的本地规则，不清空配置或修改同步开关。 */
    public void refreshProviderFilter() {
        if (this.isClientSide() || !this.isProviderFilterSynced()) {
            return;
        }
        IGrid grid = this.grid();
        if (grid != null) {
            Pp2mpResourceIntakePart resourceIntake = Pp2mpGridService.get(grid).findResourceIntake(this.frequency);
            if (resourceIntake != null) {
                this.data.getProviderFilter().copyFrom(resourceIntake.getProviderFilter());
            }
        }
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean adventureMode) {
        super.addAdditionalDrops(drops, adventureMode);
        this.data.getProviderFilter().addDrops(drops);
        Level level = this.getLevel();
        if (level == null) {
            return;
        }
        for (PendingMaterial material : this.data.getPlannedSend()) {
            material.stack().what().addDrops(material.stack().amount(), drops, level, this.getBlockEntity().getBlockPos());
        }
        for (Map<AEKey, Long> recovery : List.of(this.data.getInputRecovery(), this.data.getExtractionRecovery())) {
            recovery.forEach((key, amount) -> key.addDrops(amount, drops, level, this.getBlockEntity().getBlockPos()));
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.data.getProviderFilter().upgrades().clear();
        this.data.getPlannedSend().clear();
        this.data.getInputRecovery().clear();
        this.data.getExtractionRecovery().clear();
    }

    @SuppressWarnings("UnstableApiUsage")
    public Map<AEKeyType, ExternalStorageStrategy> findReceiveStrategies() {
        return this.receiveTarget.findStrategies();
    }

    @SuppressWarnings("UnstableApiUsage")
    public @Nullable GenericInternalInventory findReceiveInventory() {
        return this.receiveTarget.findInventory();
    }

    public void clearReceiveStorageCache() {
        this.receiveTarget.clear();
    }

    public void applyPlacerAttr(short frequency, short unitId) {
        this.setFrequency(this, frequency, this.logic::hasTaskState,
                () -> this.frequency = frequency, this::refreshConfigEventRegister);
        if (unitId != 0) {
            this.unitId = unitId;
        }
        if (this.getHost() != null) {
            this.getHost().markForSave();
            this.getHost().markForUpdate();
        }
        IGrid grid = this.grid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
            this.refreshConfigEventRegister();
        }
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!this.isClientSide()) {
            MenuOpener.open(Pp2mpUnitManagerMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }

    /* 保存复用 AE2 流程；加载保留跨类型绑定和任务状态限制。 */
    @Override
    public boolean onUseItemOn(
            @NonNull ItemStack heldItem, @NonNull Player player, @NonNull InteractionHand hand, @NonNull Vec3 pos
    ) {
        if (hand == InteractionHand.MAIN_HAND && heldItem.isEmpty()) {
            return this.onUseWithoutItem(player, pos);
        }
        if (!(heldItem.getItem() instanceof IMemoryCard memoryCard)) {
            return super.onUseItemOn(heldItem, player, hand, pos);
        }
        if (hand == InteractionHand.OFF_HAND) {
            return false;
        }
        if (this.isClientSide()) {
            return true;
        }
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return super.onUseItemOn(heldItem, player, hand, pos);
        }
        Item storedType = heldItem.get(AEComponents.EXPORTED_P2P_TYPE);
        Short storedFrequency = heldItem.get(AEComponents.EXPORTED_P2P_FREQUENCY);
        if (storedType == null || storedFrequency == null
                || (storedType != ItemRegistry.PP2MP_INPUT.get()
                && !Pp2mpUnitManagerItem.isUnitManagerItem(storedType))) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }
        if (this.logic.hasTaskState()) {
            player.displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.load_frequency.working"
            ), true);
            return true;
        }
        this.setFrequency(this, storedFrequency, this.logic::hasTaskState, () -> {
            super.importSettings(SettingsFrom.MEMORY_CARD, heldItem.getComponents(), player);
            this.frequency = storedFrequency;
            this.getHost().markForSave();
            this.getHost().markForUpdate();
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        }, this::refreshConfigEventRegister);
        return true;
    }

    @Override
    public void onPlacement(Player player) {
        super.onPlacement(player);
        this.initializeUnitId();
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        this.clearReceiveStorageCache();

        IGrid grid = this.grid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        } else {
            this.clearConfigBinding();
        }
        this.initializeUnitId();
        this.logic.alertPendingRetry();
    }

    /* 首次入网时分配单元标识，放置和节点更新使用同一流程。 */
    private void initializeUnitId() {
        if (this.unitId != 0) {
            return;
        }

        IGrid grid = this.grid();
        if (grid == null) {
            return;
        }

        Pp2mpGridService gridService = Pp2mpGridService.get(grid);
        this.unitId = Pp2mpKey.newUnitId(
                gridService.getUnitIdGenerator(), gridService::rebuildIfNeeded,
                key -> gridService.getManagerUnitIds().contains(key)
        );
        gridService.topologyChanged();

        this.getHost().markForSave();
        this.getHost().markForUpdate();
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);

        data.putShort(Pp2mpUnitManagerKeys.FREQUENCY, this.frequency);
        data.putShort(Pp2mpUnitManagerKeys.UNIT_ID, this.unitId);
        this.data.writeToNBT(data, registries);
        this.data.writeTaskState(data, registries);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.clearReceiveStorageCache();

        this.frequency = data.getShort(Pp2mpUnitManagerKeys.FREQUENCY);
        this.unitId = data.getShort(Pp2mpUnitManagerKeys.UNIT_ID);
        this.data.readFromNBT(data, registries);
        this.data.readTaskState(data, registries);

        IGrid grid = super.getMainNode().getGrid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
    }

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeShort(this.frequency);
        data.writeShort(this.unitId);
        this.data.writeToStream(data);
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);

        short oldFrequency = this.frequency;
        short oldUnitId = this.unitId;
        this.frequency = data.readShort();
        this.unitId = data.readShort();

        boolean attrChanged = this.data.readFromStream(data);

        return changed || attrChanged || oldFrequency != this.frequency || oldUnitId != this.unitId;
    }

    /* 内存卡统一导出来源、频率与单元标识，不改变拆除物品的数据。 */
    @Override
    public void exportSettings(@NonNull SettingsFrom mode, DataComponentMap.@NonNull Builder builder) {
        super.exportSettings(mode, builder);

        if (mode == SettingsFrom.MEMORY_CARD) {
            builder.set(AEComponents.EXPORTED_P2P_TYPE, this.getPartItem().asItem());
            builder.set(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID, this.unitId);
            if (this.frequency != 0) {
                builder.set(AEComponents.EXPORTED_P2P_FREQUENCY, this.frequency);
            }
        }
    }

    /* 先校准管理器本地值，再向当前拓扑中的端口发布最终结果。 */
    public void refreshConfigEventRegister() {
        if (this.isClientSide() || this.refreshingConfig) {
            return;
        }
        this.refreshingConfig = true;
        try {
            Pp2mpInputPart input = this.findInput(this.frequency, this.grid());
            if (input != this.boundInput) {
                this.clearConfigBinding();
                this.boundInput = input;
                if (input != null) {
                    input.getData().eventSource().register(this.data);
                }
            }
            if (input != null) {
                this.data.apply(input.getData().getConfig());
            }
            this.refreshProviderFilter();
        } finally {
            this.refreshingConfig = false;
        }
        this.applyPortConfig(this.data.getConfig());
    }

    /* 按当前归属推送，端口换管理器后不会残留在旧来源的监听列表中。 */
    private void applyPortConfig(UnitConfig config) {
        IGrid grid = this.grid();
        if (this.refreshingConfig || grid == null || this.frequency == 0 || this.unitId == 0) {
            return;
        }
        Pp2mpGridService service = Pp2mpGridService.get(grid);
        if (service.findManager(this.frequency, this.unitId) != this) {
            return;
        }
        for (Pp2mpUnitPortPart port : service.getPorts(this.frequency, this.unitId)) {
            if (port instanceof Pp2mpUnitOutputTypePortPart outputPort) {
                outputPort.getData().apply(config);
            } else if (port instanceof Pp2mpUnitProviderPortPart providerPort) {
                providerPort.applyProviderFilter(this.data.getProviderFilter());
            }
        }
    }

    public void clearConfigBinding() {
        if (this.boundInput != null) {
            this.boundInput.getData().eventSource().unregister(this.data);
            this.boundInput = null;
        }
    }

    @Override
    public void removeFromWorld() {
        this.clearConfigBinding();
        super.removeFromWorld();
    }

    /* 设置碰撞箱 */
    @Override
    public void getBoxes(IPartCollisionHelper helper, Predicate<@Nullable Direction> filterConnections) {
        super.updateConnections();
        // 设置单元管理器立方碰撞箱
        super.addNonDenseBoxes(
                helper, direction -> direction == null && filterConnections.test(null),
                UnitManagerConstant.FRAME_MIN, UnitManagerConstant.FRAME_MAX
        );
        // 设置延申的线缆碰撞箱
        super.addNonDenseBoxes(
                helper, direction -> direction != null && filterConnections.test(direction),
                UnitManagerConstant.INNER_MIN, UnitManagerConstant.INNER_MAX
        );
    }

    /* 设置立方中心线缆类型 */
    @Override
    public AECableType getCableConnectionType() {
        return AECableType.COVERED;
    }

    @Override
    public boolean changeColor(AEColor newColor, Player who) {
        if (newColor == this.getCableColor()) {
            return false;
        }
        if (this.isClientSide()) {
            return true;
        }
        this.setPartItem(Pp2mpUnitManagerItem.get(newColor));
        this.getMainNode().setGridColor(newColor);
        this.getHost().partChanged();
        this.getHost().markForUpdate();
        this.getHost().markForSave();
        return true;
    }

    /* 设置模型数据 */
    @Override
    public ModelData getModelData() {
        long frequencyFlags = Short.toUnsignedLong(frequency);
        if (this.isActive() && this.isPowered()) {
            frequencyFlags |= 0x10000L;
        }

        long unitFlags = Short.toUnsignedLong(unitId);
        if (this.isActive() && this.isPowered()) {
            unitFlags |= 0x10000L;
        }

        return ModelData.builder()
                .with(P2PTunnelFrequencyModelData.FREQUENCY, frequencyFlags)
                .with(Pp2mpUnitManagerModel.PP2MP_UNIT_ID, unitFlags)
                .build();
    }
}
