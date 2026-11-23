package org.ae2bc.registry;

import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import appeng.items.parts.PartItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakePart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerItem;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.item.Pp2mpUnitBreakPortItem;
import org.ae2bc.part.unit.port.part.*;
import org.ae2bc.pattern.Pp2mpPatternItem;
import org.ae2bc.placer.ComponentPlacerItem;

import java.util.Map;

public class ItemRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Ae2bcMod.MOD_ID);
    /* 输入端 */
    public static final DeferredHolder<Item, PartItem<Pp2mpInputPart>> PP2MP_INPUT = ITEMS.register(
            "pp2mp_input", () -> new PartItem<>(new Item.Properties(), Pp2mpInputPart.class, Pp2mpInputPart::new)
    );
    /* 输出端 */
    public static final DeferredHolder<Item, PartItem<Pp2mpOutputPart>> PP2MP_OUTPUT = ITEMS.register(
            "pp2mp_output", () -> new PartItem<>(new Item.Properties(), Pp2mpOutputPart.class, Pp2mpOutputPart::new)
    );
    /* 资源接收端 */
    public static final DeferredHolder<Item, PartItem<Pp2mpResourceIntakePart>> PP2MP_RESOURCE_INTAKE = ITEMS.register(
            "pp2mp_resource_intake", () -> new PartItem<>(new Item.Properties(), Pp2mpResourceIntakePart.class, Pp2mpResourceIntakePart::new)
    );
    /* 单元管理器 */
    public static final Map<AEColor, DeferredHolder<Item, ColoredPartItem<Pp2mpUnitManagerPart>>> PP2MP_UNIT_MANAGERS = Pp2mpUnitManagerItem.register();
    /* 传输单元端口 */
    public static final DeferredHolder<Item, PartItem<Pp2mpUnitTransferPortPart>> PP2MP_UNIT_PORT_TRANSFER = ITEMS.register(
            "pp2mp_unit_port_transfer", () -> new PartItem<>(
                    new Item.Properties(), Pp2mpUnitTransferPortPart.class, Pp2mpUnitTransferPortPart::new
            )
    );
    /* 掉落单元端口 */
    public static final DeferredHolder<Item, PartItem<Pp2mpUnitDropPortPart>> PP2MP_UNIT_PORT_DROP = ITEMS.register(
            "pp2mp_unit_port_drop", () -> new PartItem<>(
                    new Item.Properties(), Pp2mpUnitDropPortPart.class, Pp2mpUnitDropPortPart::new
            )
    );
    /* 放置单元端口 */
    public static final DeferredHolder<Item, PartItem<Pp2mpUnitPlacePortPart>> PP2MP_UNIT_PORT_PLACE = ITEMS.register(
            "pp2mp_unit_port_place", () -> new PartItem<>(
                    new Item.Properties(), Pp2mpUnitPlacePortPart.class, Pp2mpUnitPlacePortPart::new
            )
    );
    /* 返回单元端口 */
    public static final DeferredHolder<Item, PartItem<Pp2mpUnitReturnPortPart>> PP2MP_UNIT_PORT_RETURN = ITEMS.register(
            "pp2mp_unit_port_return", () -> new PartItem<>(
                    new Item.Properties(), Pp2mpUnitReturnPortPart.class, Pp2mpUnitReturnPortPart::new
            )
    );
    /* 提取单元端口 */
    public static final DeferredHolder<Item, PartItem<Pp2mpUnitExtractPortPart>> PP2MP_UNIT_PORT_EXTRACT = ITEMS.register(
            "pp2mp_unit_port_extract", () -> new PartItem<>(
                    new Item.Properties(), Pp2mpUnitExtractPortPart.class, Pp2mpUnitExtractPortPart::new
            )
    );
    /* 破坏单元端口 */
    public static final DeferredHolder<Item, Pp2mpUnitBreakPortItem> PP2MP_UNIT_PORT_BREAK = ITEMS.register(
            "pp2mp_unit_port_break", () -> new Pp2mpUnitBreakPortItem(new Item.Properties())
    );
    /* 收集单元端口 */
    public static final DeferredHolder<Item, PartItem<Pp2mpUnitCollectPortPart>> PP2MP_UNIT_PORT_COLLECT = ITEMS.register(
            "pp2mp_unit_port_collect", () -> new PartItem<>(
                    new Item.Properties(), Pp2mpUnitCollectPortPart.class, Pp2mpUnitCollectPortPart::new
            )
    );
    /* 红石单元端口 */
    public static final DeferredHolder<Item, PartItem<Pp2mpUnitRedstonePortPart>> PP2MP_UNIT_PORT_REDSTONE = ITEMS.register(
            "pp2mp_unit_port_redstone", () -> new PartItem<>(
                    new Item.Properties(), Pp2mpUnitRedstonePortPart.class, Pp2mpUnitRedstonePortPart::new
            )
    );
    /* 供应单元端口 */
    public static final DeferredHolder<Item, PartItem<Pp2mpUnitProviderPortPart>> PP2MP_UNIT_PORT_PROVIDER = ITEMS.register(
            "pp2mp_unit_port_provider", () -> new PartItem<>(
                    new Item.Properties(), Pp2mpUnitProviderPortPart.class, Pp2mpUnitProviderPortPart::new
            )
    );
    /* 假合成转换器 */
    public static final DeferredHolder<Item, BlockItem> FAKE_CRAFTING_CONVERTER = ITEMS.register(
            "fake_crafting_converter",
            () -> new BlockItem(BlockRegistry.FAKE_CRAFTING_CONVERTER.get(), new Item.Properties())
    );
    /* PP2MP样板 */
    public static final DeferredHolder<Item, Pp2mpPatternItem> PP2MP_PATTERN = ITEMS.register(
            "pp2mp_pattern", Pp2mpPatternItem::new
    );

    /* ME部件放置器 */
    public static final DeferredHolder<Item, ComponentPlacerItem> COMPONENT_PLACER = ITEMS.register(
            "component_placer", ComponentPlacerItem::new
    );
    /* ME IO端口 */
    public static final DeferredHolder<Item, BlockItem> IO_PORT = ITEMS.register(
            "io_port", () -> new BlockItem(BlockRegistry.IO_PORT.get(), new Item.Properties())
    );

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, Ae2bcMod.MOD_ID
    );

    public static void register(IEventBus bus) {
        // 创造物品栏分组
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("item_group." + Ae2bcMod.MOD_ID))
                .icon(() -> ItemRegistry.PP2MP_INPUT.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    // 输入端、输出端、资源接收端、单元管理器、单元端口
                    output.accept(ItemRegistry.PP2MP_INPUT.get());
                    output.accept(ItemRegistry.PP2MP_OUTPUT.get());
                    output.accept(ItemRegistry.PP2MP_RESOURCE_INTAKE.get());
                    output.accept(ItemRegistry.PP2MP_UNIT_PORT_TRANSFER.get());
                    output.accept(ItemRegistry.PP2MP_UNIT_PORT_DROP.get());
                    output.accept(ItemRegistry.PP2MP_UNIT_PORT_PLACE.get());
                    output.accept(ItemRegistry.PP2MP_UNIT_PORT_RETURN.get());
                    output.accept(ItemRegistry.PP2MP_UNIT_PORT_EXTRACT.get());
                    output.accept(ItemRegistry.PP2MP_UNIT_PORT_BREAK.get());
                    output.accept(ItemRegistry.PP2MP_UNIT_PORT_COLLECT.get());
                    output.accept(ItemRegistry.PP2MP_UNIT_PORT_REDSTONE.get());
                    output.accept(ItemRegistry.PP2MP_UNIT_PORT_PROVIDER.get());
                    ItemRegistry.PP2MP_UNIT_MANAGERS.values().forEach(
                            manager -> output.accept(manager.get())
                    );
                    output.accept(ItemRegistry.FAKE_CRAFTING_CONVERTER.get());
                    output.accept(ItemRegistry.PP2MP_PATTERN.get());

                    output.accept(ItemRegistry.COMPONENT_PLACER.get());
                    output.accept(ItemRegistry.IO_PORT.get());
                }).build()
        );

        ITEMS.register(bus);
        TABS.register(bus);
    }
}
