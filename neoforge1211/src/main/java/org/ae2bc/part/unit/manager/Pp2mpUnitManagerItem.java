package org.ae2bc.part.unit.manager;

import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.ae2bc.registry.ItemRegistry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class Pp2mpUnitManagerItem {
    /**
     * 注册物品
     */
    public static Map<AEColor, DeferredHolder<Item, ColoredPartItem<Pp2mpUnitManagerPart>>> register() {
        Map<AEColor, DeferredHolder<Item, ColoredPartItem<Pp2mpUnitManagerPart>>> result = new EnumMap<>(AEColor.class);
        for (AEColor color : AEColor.values()) {
            String id = "pp2mp_unit_manager_" + color.registryPrefix;
            result.put(color, ItemRegistry.ITEMS.register(id, () -> new ColoredPartItem<>(
                    new Item.Properties(),
                    Pp2mpUnitManagerPart.class,
                    Pp2mpUnitManagerPart::new,
                    color
            )));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 注册物品颜色
     */
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        Item[] managers = ItemRegistry.PP2MP_UNIT_MANAGERS.values().stream().map(
                holder -> (Item) holder.get()
        ).toArray(Item[]::new);
        event.register((stack, tintIndex) -> {
            ColoredPartItem<?> item = (ColoredPartItem<?>) stack.getItem();
            int variant = item.getColor().getVariantByTintIndex(tintIndex);
            return FastColor.ARGB32.opaque(variant);
        }, managers);
    }

    public static ColoredPartItem<Pp2mpUnitManagerPart> get(AEColor color) {
        DeferredHolder<Item, ColoredPartItem<Pp2mpUnitManagerPart>> holder = ItemRegistry.PP2MP_UNIT_MANAGERS.get(color);
        if (holder == null) {
            throw new IllegalArgumentException("Unregistered unit manager color: " + color);
        }
        return holder.get();
    }

    public static boolean isUnitManagerItem(Item item) {
        return ItemRegistry.PP2MP_UNIT_MANAGERS.values().stream().anyMatch(
                holder -> holder.get() == item
        );
    }
}
