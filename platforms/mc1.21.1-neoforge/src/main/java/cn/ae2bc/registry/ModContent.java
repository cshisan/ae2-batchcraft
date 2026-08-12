package cn.ae2bc.registry;

import appeng.core.AEConfig;
import appeng.api.config.Actionable;
import appeng.api.upgrades.Upgrades;
import appeng.items.parts.PartItem;
import appeng.items.parts.ColoredPartItem;
import appeng.api.util.AEColor;
import cn.ae2bc.placer.ComponentPlacerItem;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.logic.PatternP2PUnitPortType;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.placer.ComponentPlacerSelection;
import cn.ae2bc.placer.ComponentPlacerSettings;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import java.util.EnumMap;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public final class ModContent {
    public static final String COMPONENT_PLACER_ID = "component_placer";

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Ae2bcMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Ae2bcMod.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Ae2bcMod.MOD_ID);

    public static final Supplier<DataComponentType<MaterialOutputConfigData>> MATERIAL_OUTPUT_CONFIG =
            COMPONENTS.register("material_output_config", () -> DataComponentType.<MaterialOutputConfigData>builder()
                    .persistent(MaterialOutputConfigData.CODEC)
                    .networkSynchronized(MaterialOutputConfigData.STREAM_CODEC)
                    .build());
    public static final Supplier<DataComponentType<UUID>> PATTERN_P2P_UNIT_ID =
            COMPONENTS.register("pattern_p2p_unit_id", () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    public static final Supplier<DataComponentType<ComponentPlacerSettings>> PLACER_SETTINGS =
            COMPONENTS.register("component_placer_settings", () -> DataComponentType.<ComponentPlacerSettings>builder()
                    .persistent(ComponentPlacerSettings.CODEC)
                    .networkSynchronized(ComponentPlacerSettings.STREAM_CODEC)
                    .build());
    public static final Supplier<DataComponentType<ComponentPlacerSelection>> PLACER_SELECTION =
            COMPONENTS.register("component_placer_selection", () -> DataComponentType.<ComponentPlacerSelection>builder()
                    .persistent(ComponentPlacerSelection.CODEC)
                    .networkSynchronized(ComponentPlacerSelection.STREAM_CODEC)
                    .build());
    public static final Supplier<DataComponentType<ItemContainerContents>> PLACER_CABLE =
            COMPONENTS.register("component_placer_cable", () -> DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC)
                    .build());
    public static final Supplier<DataComponentType<ItemContainerContents>> PLACER_PART =
            COMPONENTS.register("component_placer_part", () -> DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC)
                    .build());
    public static final Supplier<DataComponentType<Short>> PLACER_FREQUENCY =
            COMPONENTS.register("component_placer_frequency", () -> DataComponentType.<Short>builder()
                    .persistent(com.mojang.serialization.Codec.SHORT)
                    .networkSynchronized(ByteBufCodecs.SHORT)
                    .build());

    public static final DeferredHolder<Item, PartItem<PatternP2PTunnelPart>> PATTERN_P2P_TUNNEL_INPUT =
            ITEMS.register("pattern_p2p_tunnel_input", () -> new PartItem<>(new Item.Properties(),
                    PatternP2PTunnelPart.class, item -> new PatternP2PTunnelPart(item, false)));
    public static final DeferredHolder<Item, PartItem<PatternP2PTunnelPart>> PATTERN_P2P_TUNNEL_OUTPUT =
            ITEMS.register("pattern_p2p_tunnel_output", () -> new PartItem<>(new Item.Properties(),
                    PatternP2PTunnelPart.class, item -> new PatternP2PTunnelPart(item, true)));
    public static final DeferredHolder<Item, PartItem<PatternP2PTunnelEnergyPart>> PATTERN_P2P_TUNNEL_ENERGY =
            ITEMS.register("pattern_p2p_tunnel_energy", () -> new PartItem<>(new Item.Properties(),
                    PatternP2PTunnelEnergyPart.class, PatternP2PTunnelEnergyPart::new));
    public static final Map<AEColor, DeferredHolder<Item, ColoredPartItem<PatternP2PUnitManagerPart>>> PATTERN_P2P_UNIT_MANAGERS =
            registerPatternP2PUnitManagers();
    public static final DeferredHolder<Item, ColoredPartItem<PatternP2PUnitManagerPart>> PATTERN_P2P_UNIT_MANAGER =
            PATTERN_P2P_UNIT_MANAGERS.get(AEColor.TRANSPARENT);
    public static final DeferredHolder<Item, PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_DROP =
            patternP2PUnitPort("pattern_p2p_unit_port_drop", PatternP2PUnitPortType.DROP);
    public static final DeferredHolder<Item, PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_COLLECT =
            patternP2PUnitPort("pattern_p2p_unit_port_collect", PatternP2PUnitPortType.COLLECT);
    public static final DeferredHolder<Item, PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_PLACE =
            patternP2PUnitPort("pattern_p2p_unit_port_place", PatternP2PUnitPortType.PLACE);
    public static final DeferredHolder<Item, PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_BREAK =
            patternP2PUnitPort("pattern_p2p_unit_port_break", PatternP2PUnitPortType.BREAK);
    public static final DeferredHolder<Item, PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_TRANSFER =
            patternP2PUnitPort("pattern_p2p_unit_port_transfer", PatternP2PUnitPortType.TRANSFER);
    public static final DeferredHolder<Item, PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_RETURN =
            patternP2PUnitPort("pattern_p2p_unit_port_return", PatternP2PUnitPortType.RETURN);
    public static final DeferredHolder<Item, PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_EXTRACT =
            patternP2PUnitPort("pattern_p2p_unit_port_extract", PatternP2PUnitPortType.EXTRACT);
    public static final DeferredHolder<Item, PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_REDSTONE =
            patternP2PUnitPort("pattern_p2p_unit_port_redstone", PatternP2PUnitPortType.REDSTONE);
    public static final DeferredHolder<Item, PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_ENERGY =
            patternP2PUnitPort("pattern_p2p_unit_port_energy", PatternP2PUnitPortType.ENERGY);
    public static final DeferredHolder<Item, Item> PRODUCT_EXTRACTION_CARD =
            ITEMS.register("product_extraction_card", () -> Upgrades.createUpgradeCardItem(new Item.Properties()));
    public static final DeferredHolder<Item, ComponentPlacerItem> COMPONENT_PLACER =
            ITEMS.register(COMPONENT_PLACER_ID, () -> new ComponentPlacerItem(
                    AEConfig.instance().getWirelessTerminalBattery(), new Item.Properties().stacksTo(1)));

    public static final Supplier<CreativeModeTab> CREATIVE_TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ae2_batchcraft"))
            .icon(() -> PATTERN_P2P_TUNNEL_INPUT.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PATTERN_P2P_TUNNEL_INPUT.get());
                output.accept(PATTERN_P2P_TUNNEL_OUTPUT.get());
                output.accept(PATTERN_P2P_TUNNEL_ENERGY.get());
                for (var manager : PATTERN_P2P_UNIT_MANAGERS.values()) {
                    output.accept(manager.get());
                }
                output.accept(PATTERN_P2P_UNIT_PORT_DROP.get());
                output.accept(PATTERN_P2P_UNIT_PORT_COLLECT.get());
                output.accept(PATTERN_P2P_UNIT_PORT_PLACE.get());
                output.accept(PATTERN_P2P_UNIT_PORT_BREAK.get());
                output.accept(PATTERN_P2P_UNIT_PORT_TRANSFER.get());
                output.accept(PATTERN_P2P_UNIT_PORT_RETURN.get());
                output.accept(PATTERN_P2P_UNIT_PORT_EXTRACT.get());
                output.accept(PATTERN_P2P_UNIT_PORT_REDSTONE.get());
                output.accept(PATTERN_P2P_UNIT_PORT_ENERGY.get());
                output.accept(PRODUCT_EXTRACTION_CARD.get());
                var placer = COMPONENT_PLACER.get();
                ItemStack chargedPlacer = placer.getDefaultInstance();
                placer.injectAEPower(chargedPlacer, placer.getAEMaxPower(chargedPlacer), Actionable.MODULATE);
                output.accept(chargedPlacer);
            })
            .build());

    private ModContent() {
    }

    private static DeferredHolder<Item, PartItem<PatternP2PUnitPortPart>> patternP2PUnitPort(
            String id, PatternP2PUnitPortType type) {
        return ITEMS.register(id, () -> new PartItem<>(new Item.Properties(),
                PatternP2PUnitPortPart.class, item -> new PatternP2PUnitPortPart(item, type)));
    }

    private static Map<AEColor, DeferredHolder<Item, ColoredPartItem<PatternP2PUnitManagerPart>>> registerPatternP2PUnitManagers() {
        Map<AEColor, DeferredHolder<Item, ColoredPartItem<PatternP2PUnitManagerPart>>> result =
                new EnumMap<>(AEColor.class);
        for (AEColor color : AEColor.values()) {
            String id = color == AEColor.TRANSPARENT
                    ? "pattern_p2p_unit_manager" : color.registryPrefix + "_pattern_p2p_unit_manager";
            result.put(color, ITEMS.register(id, () -> new ColoredPartItem<>(new Item.Properties(),
                    PatternP2PUnitManagerPart.class, PatternP2PUnitManagerPart::new, color)));
        }
        return Collections.unmodifiableMap(result);
    }

    public static ColoredPartItem<PatternP2PUnitManagerPart> getPatternP2PUnitManager(AEColor color) {
        return PATTERN_P2P_UNIT_MANAGERS.get(color).get();
    }

    public static boolean isPatternP2PUnitManagerItem(Item item) {
        return PATTERN_P2P_UNIT_MANAGERS.values().stream().anyMatch(holder -> holder.get() == item);
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
        COMPONENTS.register(bus);
    }
}
