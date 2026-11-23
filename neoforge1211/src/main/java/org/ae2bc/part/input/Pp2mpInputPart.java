package org.ae2bc.part.input;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.items.tools.MemoryCardItem;
import appeng.me.service.P2PService;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.p2p.P2PTunnelPart;
import appeng.util.InteractionUtil;
import appeng.util.SettingsFrom;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.ae2bc.part.Pp2mpPart;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.registry.ItemRegistry;

import java.util.List;

public class Pp2mpInputPart extends P2PTunnelPart<Pp2mpInputPart> implements Pp2mpPart, ICraftingMachine {
    private final @Getter Pp2mpInputLogic logic;
    private final @Getter Pp2mpInputData data;

    public Pp2mpInputPart(IPartItem<?> partItem) {
        super(partItem);
        this.logic = new Pp2mpInputLogic(this, super.getMainNode());
        this.data = new Pp2mpInputData(
                () -> this.getHost().markForSave(),
                () -> this.getHost().markForUpdate()
        );
        super.getMainNode().addService(IGridTickable.class, new Pp2mpInputTicker(this));
    }


    @Override
    public IPartModel getStaticModels() {
        return Pp2mpInputModel.MODELS.getModel(super.isPowered(), super.isActive());
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.isClientSide()) {
            MenuOpener.open(Pp2mpInputMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        // 副手或者非内存卡则交给父类处理
        if (hand == InteractionHand.OFF_HAND || !(heldItem.getItem() instanceof IMemoryCard memoryCard)) {
            return false;
        }

        if (super.isClientSide()) {
            return true;
        }

        // 不是潜行右键则不处理
        if (!InteractionUtil.isInAlternateUseMode(player)) {
            return true;
        }
        IGrid grid = this.grid();
        if (grid == null) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return true;
        }

        // 生成、更新频率
        short frequency = super.getFrequency();
        boolean needGenerate = frequency == 0;
        P2PService p2pService = P2PService.get(grid);
        if (needGenerate) {
            frequency = p2pService.newFrequency();
        }
        p2pService.updateFreq(this, frequency);

        // 保存到内存卡
        MemoryCardItem.clearCard(heldItem);
        heldItem.set(AEComponents.EXPORTED_SETTINGS_SOURCE, super.getPartItem().asItem().getDescription());
        heldItem.applyComponents(super.exportSettings(SettingsFrom.MEMORY_CARD));
        memoryCard.notifyUser(player, needGenerate ? MemoryCardMessages.SETTINGS_RESET : MemoryCardMessages.SETTINGS_SAVED);
        return true;
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.data.readFromNBT(data, registries);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        this.data.writeToNBT(data, registries);
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        return this.data.readFromStream(data) || changed;
    }

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        this.data.writeToStream(data);
    }

    /* 拆除时掉落尚未发送的实物和恢复资源，标记资源不生成物品。 */
    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean adventureMode) {
        super.addAdditionalDrops(drops, adventureMode);
        this.data.addLegacyProviderUpgradeDrops(drops);
        Level level = this.getLevel();
        if (level == null) {
            return;
        }
        this.logic.storedInputs().forEach((key, amount) ->
                key.addDrops(amount, drops, level, this.getBlockEntity().getBlockPos()));
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.data.clearLegacyProviderUpgrades();
        this.data.clearBatchTask();
        this.data.getInputRecovery().clear();
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        Item item = ItemRegistry.PP2MP_INPUT.get();
        return new PatternContainerGroup(AEItemKey.of(item), item.getDescription(), List.of());
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        return this.logic.pushPattern(patternDetails, inputs);
    }

    @Override
    public boolean acceptsPlans() {
        return this.logic.canAcceptPlans();
    }

    @Override
    public void onTunnelConfigChange() {
        super.onTunnelConfigChange();

        IGrid grid = super.getMainNode().getGrid();
        if (grid != null) {
            Pp2mpGridService gridService = Pp2mpGridService.get(grid);
            gridService.topologyChanged();
            gridService.invalidateReturnStorageCaches(super.getFrequency());
        }
    }

    @Override
    public void onTunnelNetworkChange() {
        super.onTunnelNetworkChange();

        IGrid grid = super.getMainNode().getGrid();
        if (grid != null) {
            Pp2mpGridService gridService = Pp2mpGridService.get(grid);
            gridService.topologyChanged();
            gridService.invalidateReturnStorageCaches(super.getFrequency());
        }
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);

        IGrid grid = super.getMainNode().getGrid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
    }

    @Override
    public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChanged(level, pos, neighbor);

        IGrid grid = super.getMainNode().getGrid();
        if (grid != null && super.getFrequency() != 0) {
            Pp2mpGridService.get(grid).wakeReturnTargets(super.getFrequency());
        }
        this.logic.wakeBatchDispatcher();
    }
}
