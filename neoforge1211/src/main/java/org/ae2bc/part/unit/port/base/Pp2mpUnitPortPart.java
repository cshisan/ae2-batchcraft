package org.ae2bc.part.unit.port.base;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;
import appeng.me.helpers.MachineSource;
import appeng.parts.AEBasePart;
import appeng.util.InteractionUtil;
import appeng.util.SettingsFrom;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.ae2bc.core.constant.Pp2mpUnitPortKeys;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.Pp2mpPart;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.registry.component.Pp2mpUnitManagerRegistry;
import org.jspecify.annotations.Nullable;

@Getter
public abstract class Pp2mpUnitPortPart extends AEBasePart implements Pp2mpPart {
    public static ModelProperty<Long> PP2MP_UNIT_ID = new ModelProperty<>();
    protected final UnitPortType type;
    protected final IActionSource actionSource;
    private final Pp2mpUnitPortPartModel models;
    protected short frequency;
    protected short unitId;
    private @Nullable String endpointId;

    public Pp2mpUnitPortPart(IPartItem<?> partItem, UnitPortType type, Pp2mpUnitPortPartModel models) {
        super(partItem);
        this.type = type;
        this.models = models;
        this.actionSource = new MachineSource(this);
    }

    /* 相同世界位置的端口在重载后保持任务分配身份。 */
    public String getEndpointId() {
        if (this.endpointId == null) {
            Level level = this.getLevel();
            if (level == null) {
                throw new IllegalStateException("Port has no level");
            }
            this.endpointId = level.dimension().location() + "|"
                    + this.getBlockEntity().getBlockPos().asLong() + "|" + this.getSide();
        }
        return this.endpointId;
    }

    /* 是否绑定关系 */
    public boolean isBoundTo(Pp2mpUnitManagerPart manager) {
        return manager != null && this.versionEq(this.frequency, manager.getFrequency())
                && this.versionEq(this.unitId, manager.getUnitId())
                && this.grid() == manager.grid();
    }

    /* 端口目标或过滤变化后仅唤醒当前绑定的管理器。 */
    protected final void alertManager() {
        IGrid grid = this.grid();
        if (grid != null) {
            Pp2mpGridService.get(grid).wakeUnitManager(this.frequency, this.unitId);
        }
    }

    /* 应用放置器传递配置 */
    public void applyPlacerAttr(short frequency, short unitId) {
        this.frequency = frequency;
        this.unitId = unitId;
        if (this.getHost() != null) {
            this.getHost().markForSave();
            this.getHost().markForUpdate();
        }
        IGrid grid = this.grid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
            Pp2mpUnitManagerPart manager = Pp2mpGridService.get(grid).findManager(
                    this.frequency, this.unitId
            );
            if (manager != null) {
                manager.refreshConfigEventRegister();
            }
        }
    }

    protected void openMenu(Player player) {
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!this.isClientSide()) {
            this.openMenu(player);
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        if (hand == InteractionHand.OFF_HAND || !(heldItem.getItem() instanceof IMemoryCard memoryCard)) {
            return false;
        }
        if (isClientSide()) {
            return true;
        }

        // 右键
        if (!InteractionUtil.isInAlternateUseMode(player)) {
            Short storedFrequency = heldItem.get(AEComponents.EXPORTED_P2P_FREQUENCY);
            Short storedUnitId = heldItem.get(Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID);
            if (storedUnitId == null || storedFrequency == null) {
                return true;
            }

            IGrid grid = this.grid();
            if (grid == null) {
                return true;
            }
            Pp2mpUnitManagerPart manager = Pp2mpGridService.get(grid).findManager(
                    storedFrequency, storedUnitId
            );
            if (manager != null && manager.getLogic().hasTaskState()) {
                player.displayClientMessage(Component.translatable(
                        "message.ae2_batchcraft.load_frequency.working"
                ), true);
                return true;
            }

            super.importSettings(SettingsFrom.MEMORY_CARD, heldItem.getComponents(), player);

            this.frequency = storedFrequency;
            this.unitId = storedUnitId;

            this.getHost().markForSave();
            this.getHost().markForUpdate();
            Pp2mpGridService.get(grid).topologyChanged();
            manager = Pp2mpGridService.get(grid).findManager(this.frequency, this.unitId);
            if (manager != null) {
                manager.refreshConfigEventRegister();
            }

            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        }
        return true;
    }

    @Nullable
    protected Pp2mpUnitManagerPart findManager() {
        IGrid grid = this.grid();
        if (grid == null) {
            return null;
        }
        return Pp2mpGridService.get(grid).findManager(this.frequency, this.unitId);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putShort(Pp2mpUnitPortKeys.FREQUENCY, this.frequency);
        data.putShort(Pp2mpUnitPortKeys.UNIT_ID, this.unitId);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.frequency = data.getShort(Pp2mpUnitPortKeys.FREQUENCY);
        this.unitId = data.getShort(Pp2mpUnitPortKeys.UNIT_ID);
    }


    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeShort(this.frequency);
        data.writeShort(this.unitId);
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);

        short oldFrequency = this.frequency;
        short oldUnitId = this.unitId;
        this.frequency = data.readShort();
        this.unitId = data.readShort();

        return changed || oldFrequency != this.frequency || oldUnitId != this.unitId;
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);

        IGrid grid = this.grid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
    }

    @Override
    public void getBoxes(IPartCollisionHelper helper) {
        helper.addBox(3, 3, 13, 13, 13, 16);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 1;
    }

    @Override
    public IPartModel getStaticModels() {
        return this.models.getModel(this.isPowered(), this.isActive());
    }

    @Override
    public ModelData getModelData() {
        long frequencyValue = Short.toUnsignedLong(frequency);
        long unitIdValue = Short.toUnsignedLong(unitId);
        if (this.isPowered()) {
            frequencyValue |= 0x10000L;
            unitIdValue |= 0x10000L;
        }
        return ModelData.builder()
                .with(P2PTunnelFrequencyModelData.FREQUENCY, frequencyValue)
                .with(Pp2mpUnitPortPart.PP2MP_UNIT_ID, unitIdValue)
                .build();
    }
}
