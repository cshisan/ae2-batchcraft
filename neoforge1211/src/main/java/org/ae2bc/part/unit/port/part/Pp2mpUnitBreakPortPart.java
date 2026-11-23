package org.ae2bc.part.unit.port.part;

import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartItem;
import appeng.parts.automation.ItemPickupStrategy;
import appeng.util.SettingsFrom;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.Pp2mpBlockPickupStrategy;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class Pp2mpUnitBreakPortPart extends Pp2mpUnitCollectPortPart {
    private static final String ENCHANTMENTS = "Enchantments";
    private ItemEnchantments enchantments = ItemEnchantments.EMPTY;

    public Pp2mpUnitBreakPortPart(IPartItem<?> partItem) {
        super(partItem, UnitPortType.BREAK, Pp2mpUnitPortPartModel.MODEL_BREAK);
    }

    @Override
    protected ItemEnchantments getPickupEnchantments() {
        return this.enchantments;
    }

    /* 限定原生方块策略的掉落来源，保留其他模组注册的拾取策略。 */
    @Override
    protected List<PickupStrategy> createPickupStrategies(Pp2mpAdjacentTarget.Target target, @Nullable UUID owner) {
        return super.createPickupStrategies(target, owner).stream()
                .map(strategy -> strategy.getClass() == ItemPickupStrategy.class
                        ? target.withLevel(level -> new Pp2mpBlockPickupStrategy(
                                level, target, this.getBlockEntity(), this.enchantments, owner))
                        : strategy)
                .toList();
    }

    @Override
    public void importSettings(SettingsFrom from, DataComponentMap components, @Nullable Player player) {
        super.importSettings(from, components, player);
        if (from == SettingsFrom.DISMANTLE_ITEM) {
            this.enchantments = components.getOrDefault(
                    DataComponents.ENCHANTMENTS,
                    ItemEnchantments.EMPTY
            );
            this.clearPickupStrategies();
        }
    }

    @Override
    public void exportSettings(SettingsFrom from, DataComponentMap.Builder builder) {
        super.exportSettings(from, builder);
        if (from == SettingsFrom.DISMANTLE_ITEM && !this.enchantments.isEmpty()) {
            builder.set(DataComponents.ENCHANTMENTS, this.enchantments);
        }
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.enchantments = ItemEnchantments.EMPTY;
        if (data.contains(ENCHANTMENTS, Tag.TAG_COMPOUND)) {
            ItemEnchantments.CODEC.parse(
                            registries.createSerializationContext(NbtOps.INSTANCE),
                            data.getCompound(ENCHANTMENTS)
                    )
                    .resultOrPartial(error -> Ae2bcMod.LOGGER.warn(
                            "Failed to load break unit port enchantments: {}", error))
                    .ifPresent(value -> this.enchantments = value);
        }
        this.clearPickupStrategies();
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.remove(ENCHANTMENTS);
        if (!this.enchantments.isEmpty()) {
            ItemEnchantments.CODEC.encodeStart(
                            registries.createSerializationContext(NbtOps.INSTANCE), this.enchantments
                    )
                    .resultOrPartial(error -> Ae2bcMod.LOGGER.warn(
                            "Failed to save break unit port enchantments: {}", error))
                    .ifPresent(tag -> data.put(ENCHANTMENTS, tag));
        }
    }

    /* 两种拾取模式共用附魔破坏；关闭拾取时让策略将产物留在世界中。 */
    public boolean breakAdjacentBlock(boolean breakPickup) {
        Pp2mpUnitManagerPart manager = this.findManager();
        if (manager == null || !this.isBoundTo(manager) || !manager.getLogic().isTaskOperational()) {
            return false;
        }

        Pp2mpAdjacentTarget.Target target = this.adjacentTarget.resolve();
        if (target == null) {
            return false;
        }

        IGrid grid = this.grid();
        if (grid == null) {
            return false;
        }

        boolean hasFluid = target.withLevel(level -> {
            BlockState state = level.getBlockState(target.pos());
            return !state.getFluidState().isEmpty();
        });
        if (hasFluid) {
            return this.tryPickupFluid(target, grid);
        }

        boolean isAir = target.withLevel(level -> level.getBlockState(target.pos()).isAir());
        if (isAir) {
            return false;
        }

        if (!breakPickup) {
            return this.pickupInScope(target, grid,
                    (key, amount, mode) -> mode == Actionable.SIMULATE ? amount : 0);
        }

        return this.tryPickupBlock(target, grid);
    }

    /**
     * 破坏端口不能执行收集端口的实体扫描。
     * 它唯一的拾取操作是上面的方块/流体拾取策略，
     * 且范围仅限于当前被破坏的相邻目标。
     */
    @Override
    public boolean collectProducts(int maxOperations) {
        return false;
    }
}
