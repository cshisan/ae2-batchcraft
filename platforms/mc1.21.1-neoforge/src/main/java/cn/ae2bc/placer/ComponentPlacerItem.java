package cn.ae2bc.placer;

import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.BusSupport;
import appeng.api.parts.IPartItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.registry.ModContent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class ComponentPlacerItem extends WirelessTerminalItem {
    public static final int MATERIAL_SLOT_COUNT = 9;

    public ComponentPlacerItem(DoubleSupplier powerCapacity, Properties properties) {
        super(powerCapacity, properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        return trySelect(player, context.getItemInHand(), context.getClickedPos(),
                context.getLevel().dimension().location(), context.isSecondaryUseActive(), context.getLevel().isClientSide())
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide())
                : InteractionResult.PASS;
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack placer = event.getItemStack();
        if (!(placer.getItem() instanceof ComponentPlacerItem)
                && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && placer.isEmpty()
                && event.getEntity().getOffhandItem().getItem() instanceof ComponentPlacerItem) {
            placer = event.getEntity().getOffhandItem();
        }
        if (!(placer.getItem() instanceof ComponentPlacerItem)) {
            return;
        }

        var player = event.getEntity();
        var level = event.getLevel();
        if (!trySelect(player, placer, event.getPos(), level.dimension().location(),
                player.isSecondaryUseActive(), level.isClientSide())) {
            return;
        }

        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        event.setCanceled(true);
    }

    private static boolean trySelect(Player player, ItemStack placer, net.minecraft.core.BlockPos pos,
                                     net.minecraft.resources.ResourceLocation dimension, boolean secondaryUse,
                                     boolean clientSide) {
        ComponentPlacerSelection selection = placer.get(ModContent.PLACER_SELECTION.get());
        if (!secondaryUse && (selection == null || selection.second() != null || !selection.dimension().equals(dimension))) {
            return false;
        }

        if (clientSide) {
            return true;
        }

        if (secondaryUse) {
            placer.set(ModContent.PLACER_SELECTION.get(), ComponentPlacerSelection.start(dimension, pos));
            ComponentPlacerSettings settings = placer.getOrDefault(
                    ModContent.PLACER_SETTINGS.get(), ComponentPlacerSettings.DEFAULT);
            placer.set(ModContent.PLACER_SETTINGS.get(), settings.resetOffsets());
            player.displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.component_placer.first_set", pos.toShortString()), false);
            return true;
        }

        ComponentPlacerSelection completed = selection.complete(pos);
        ComponentPlacerSelection.Validation validation = completed.validate();
        if (validation == ComponentPlacerSelection.Validation.VALID) {
            placer.set(ModContent.PLACER_SELECTION.get(), completed);
            player.displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.component_placer.selection_set",
                    completed.sizeX(), completed.sizeY(), completed.sizeZ()), false);
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.component_placer.selection_"
                            + validation.name().toLowerCase(Locale.ROOT)), false);
        }
        return true;
    }

    @Override
    public MenuType<?> getMenuType() {
        return ComponentPlacerMenu.TYPE;
    }

    @Override
    public @NotNull ComponentPlacerMenuHost getMenuHost(Player player, ItemMenuHostLocator locator,
                                                  @Nullable BlockHitResult hitResult) {
        return new ComponentPlacerMenuHost(this, player, locator,
                (p, subMenu) -> openFromInventory(p, locator, true));
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, 3, (changedStack, upgrades) ->
                setAEMaxPowerMultiplier(changedStack, 1 + Upgrades.getEnergyCardMultiplier(upgrades)));
    }

    public static AppEngInternalInventory getMaterialInventory(ItemStack placer) {
        var inventory = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void saveChangedInventory(AppEngInternalInventory inventory) {
                placer.set(DataComponents.CONTAINER, inventory.toItemContainerContents());
            }

            @Override
            public boolean isClientSide() {
                return false;
            }
        }, MATERIAL_SLOT_COUNT);
        inventory.setEnableClientEvents(true);
        inventory.setFilter(new MaterialFilter());
        inventory.fromItemContainerContents(placer.getOrDefault(
                DataComponents.CONTAINER, ItemContainerContents.EMPTY));
        return inventory;
    }

    public static AppEngInternalInventory getCableFilterInventory(ItemStack placer) {
        return getMarkerInventory(placer, ModContent.PLACER_CABLE, new CableFilter());
    }

    public static AppEngInternalInventory getPartFilterInventory(ItemStack placer) {
        return getMarkerInventory(placer, ModContent.PLACER_PART, new PartFilter());
    }

    private static AppEngInternalInventory getMarkerInventory(ItemStack placer,
                                                               Supplier<DataComponentType<ItemContainerContents>> component,
                                                               IAEItemFilter filter) {
        var inventory = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void saveChangedInventory(AppEngInternalInventory inventory) {
                ItemStack marked = inventory.getStackInSlot(0);
                if (marked.isEmpty()) {
                    placer.remove(component.get());
                } else {
                    placer.set(component.get(), ItemContainerContents.fromItems(
                            java.util.List.of(marked.copyWithCount(1))));
                }
            }

            @Override
            public boolean isClientSide() {
                return false;
            }
        }, 1, 1);
        inventory.setEnableClientEvents(true);
        inventory.setFilter(filter);
        ItemStack marked = getMarkedItem(placer, component.get());
        if (!marked.isEmpty()) {
            inventory.fromItemContainerContents(ItemContainerContents.fromItems(java.util.List.of(marked)));
        }
        return inventory;
    }

    public static ItemStack getMarkedCable(ItemStack placer) {
        return getMarkedItem(placer, ModContent.PLACER_CABLE.get());
    }

    public static ItemStack getMarkedPart(ItemStack placer) {
        return getMarkedItem(placer, ModContent.PLACER_PART.get());
    }

    private static ItemStack getMarkedItem(ItemStack placer, DataComponentType<ItemContainerContents> component) {
        ItemStack marked = placer.getOrDefault(component, ItemContainerContents.EMPTY).copyOne();
        return marked.isEmpty() ? marked : marked.copyWithCount(1);
    }

    public static boolean isUsableCable(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IPartItem<?> partItem)) {
            return false;
        }
        var part = partItem.createPart();
        return part instanceof ICablePart cable && cable.supportsBuses() == BusSupport.CABLE;
    }

    public static boolean isAllowedMaterial(ItemStack stack) {
        return isUsableCable(stack) || isUsablePart(stack);
    }

    public static boolean isUsablePart(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IPartItem<?> partItem)) {
            return false;
        }
        return !ICablePart.class.isAssignableFrom(partItem.getPartClass());
    }

    public static boolean hasCraftingCard(ItemStack placer) {
        return placer.getItem() instanceof ComponentPlacerItem item
                && item.getUpgrades(placer).isInstalled(AEItems.CRAFTING_CARD);
    }

    private static final class MaterialFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(appeng.api.inventories.InternalInventory inventory, int slot, ItemStack stack) {
            return isAllowedMaterial(stack);
        }
    }

    private static final class CableFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(appeng.api.inventories.InternalInventory inventory, int slot, ItemStack stack) {
            return stack.isEmpty() || isUsableCable(stack);
        }
    }

    private static final class PartFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(appeng.api.inventories.InternalInventory inventory, int slot, ItemStack stack) {
            return stack.isEmpty() || isUsablePart(stack);
        }
    }
}
