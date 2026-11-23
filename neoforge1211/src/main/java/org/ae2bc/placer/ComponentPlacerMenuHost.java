package org.ae2bc.placer;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.contents.StackDependentSupplier;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.ConfigInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.SupplierInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import lombok.Getter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.ae2bc.registry.component.ComponentPlacerRegistry;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

@Getter
public final class ComponentPlacerMenuHost extends WirelessTerminalMenuHost<ComponentPlacerItem> {
    private final SupplierInternalInventory<InternalInventory> materials;
    private final SupplierInternalInventory<InternalInventory> cableMarker;
    private final SupplierInternalInventory<InternalInventory> partMarker;

    public ComponentPlacerMenuHost(
            ComponentPlacerItem item, Player player, ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu
    ) {
        super(item, player, locator, returnToMainMenu);

        // 材料槽
        this.materials = new SupplierInternalInventory<>(new StackDependentSupplier<>(this::getItemStack, stack -> {
            AppEngInternalInventory inventory = new AppEngInternalInventory(new InternalInventoryHost() {
                @Override
                public void saveChangedInventory(AppEngInternalInventory changed) {
                    stack.set(DataComponents.CONTAINER, changed.toItemContainerContents());
                }

                @Override
                public boolean isClientSide() {
                    return false;
                }
            }, ComponentPlacerData.MATERIAL_SLOT_COUNT);
            inventory.setEnableClientEvents(true);
            inventory.setFilter(new IAEItemFilter() {
                @Override
                public boolean allowInsert(InternalInventory target, int slot, ItemStack stack) {
                    return ComponentPlacerItem.isAllowedMaterial(stack);
                }
            });
            inventory.fromItemContainerContents(stack.getOrDefault(
                    DataComponents.CONTAINER, ItemContainerContents.EMPTY));
            return inventory;
        }));

        // 线缆标记槽
        this.cableMarker = new SupplierInternalInventory<>(new StackDependentSupplier<>(
                this::getItemStack,
                stack -> MarkerInventoryBinding.create(
                        stack, ComponentPlacerRegistry.CABLE,
                        ComponentPlacerRegistry.LEGACY_CABLE,
                        key -> ComponentPlacerItem.isUsableCable(key.toStack())
                ).createMenuWrapper()
        ));

        // 部件标记槽
        this.partMarker = new SupplierInternalInventory<>(new StackDependentSupplier<>(
                this::getItemStack,
                stack -> MarkerInventoryBinding.create(
                        stack, ComponentPlacerRegistry.PART,
                        ComponentPlacerRegistry.LEGACY_PART,
                        key -> ComponentPlacerItem.isUsablePart(key.toStack())
                ).createMenuWrapper()
        ));
    }

    /* 创造模式的材料预检、取料和退料免电，生存模式沿用无线终端耗电。 */
    @Override
    public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
        if (this.getPlayer().isCreative()) {
            return amount;
        }
        return super.extractAEPower(amount, mode, multiplier);
    }

    private record MarkerInventoryBinding(
            ItemStack stack, DataComponentType<GenericStack> component,
            DataComponentType<ItemContainerContents> legacyComponent,
            ConfigInventory inventory
    ) {
        private static MarkerInventoryBinding create(
                ItemStack stack, DataComponentType<GenericStack> component,
                DataComponentType<ItemContainerContents> legacyComponent,
                Predicate<AEItemKey> filter
        ) {
            ConfigInventory[] holder = new ConfigInventory[1];
            ConfigInventory inventory = ConfigInventory.configTypes(1)
                    .supportedType(AEKeyType.items())
                    .slotFilter((slot, key) -> key instanceof AEItemKey itemKey && filter.test(itemKey))
                    .changeListener(() -> MarkerInventoryBinding.save(stack, component, legacyComponent, holder[0]))
                    .build();
            holder[0] = inventory;

            MarkerInventoryBinding binding = new MarkerInventoryBinding(
                    stack, component, legacyComponent, inventory
            );
            binding.load();
            return binding;
        }

        private static void save(
                ItemStack stack, DataComponentType<GenericStack> component,
                DataComponentType<ItemContainerContents> legacyComponent,
                ConfigInventory inventory
        ) {
            GenericStack marker = inventory.getStack(0);
            if (marker == null) {
                stack.remove(component);
            } else {
                stack.set(component, new GenericStack(marker.what(), 0));
            }
            stack.remove(legacyComponent);
        }

        private InternalInventory createMenuWrapper() {
            return this.inventory.createMenuWrapper();
        }

        private void load() {
            GenericStack marker = this.stack.get(this.component);
            if (marker == null) {
                ItemStack legacy = this.stack.getOrDefault(
                        this.legacyComponent,
                        ItemContainerContents.EMPTY
                ).copyOne();
                marker = GenericStack.fromItemStack(legacy);
            }
            if (marker != null && this.inventory.isAllowedIn(0, marker.what())) {
                this.inventory.setStack(0, new GenericStack(marker.what(), 0));
            }
        }
    }
}
