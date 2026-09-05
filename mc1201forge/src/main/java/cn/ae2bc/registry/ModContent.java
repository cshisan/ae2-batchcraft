package cn.ae2bc.registry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.parts.IPartItem;
import appeng.api.upgrades.Upgrades;
import appeng.api.util.AEColor;
import appeng.core.AEConfig;
import appeng.items.parts.ColoredPartItem;
import appeng.items.parts.PartItem;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.item.PatternP2PUnitPortItem;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import cn.ae2bc.placer.ComponentPlacerItem;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModContent {
    public static final String COMPONENT_PLACER_ID = "component_placer";

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Ae2bcMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Ae2bcMod.MOD_ID);

    public static final RegistryObject<PartItem<PatternP2PTunnelPart>> PATTERN_P2P_TUNNEL_INPUT =
            ITEMS.register("pattern_p2p_tunnel_input", () -> new PartItem<>(new Item.Properties(),
                    PatternP2PTunnelPart.class, item -> new PatternP2PTunnelPart(item, false)));
    public static final RegistryObject<PartItem<PatternP2PTunnelPart>> PATTERN_P2P_TUNNEL_OUTPUT =
            ITEMS.register("pattern_p2p_tunnel_output", () -> new PartItem<>(new Item.Properties(),
                    PatternP2PTunnelPart.class, item -> new PatternP2PTunnelPart(item, true)));
    public static final RegistryObject<PartItem<PatternP2PTunnelEnergyPart>> PATTERN_P2P_TUNNEL_ENERGY =
            ITEMS.register("pattern_p2p_tunnel_energy", () -> new PartItem<>(new Item.Properties(),
                    PatternP2PTunnelEnergyPart.class, PatternP2PTunnelEnergyPart::new));

    public static final Map<AEColor, RegistryObject<ColoredPartItem<PatternP2PUnitManagerPart>>>
            PATTERN_P2P_UNIT_MANAGERS = registerPatternP2PUnitManagers();
    public static final RegistryObject<ColoredPartItem<PatternP2PUnitManagerPart>> PATTERN_P2P_UNIT_MANAGER =
            PATTERN_P2P_UNIT_MANAGERS.get(AEColor.TRANSPARENT);

    public static final RegistryObject<PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_DROP =
            patternP2PUnitPort("pattern_p2p_unit_port_drop", UnitPortType.DROP);
    public static final RegistryObject<PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_COLLECT =
            patternP2PUnitPort("pattern_p2p_unit_port_collect", UnitPortType.COLLECT);
    public static final RegistryObject<PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_PLACE =
            patternP2PUnitPort("pattern_p2p_unit_port_place", UnitPortType.PLACE);
    public static final RegistryObject<PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_BREAK =
            patternP2PUnitPort("pattern_p2p_unit_port_break", UnitPortType.BREAK);
    public static final RegistryObject<PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_TRANSFER =
            patternP2PUnitPort("pattern_p2p_unit_port_transfer", UnitPortType.TRANSFER);
    public static final RegistryObject<PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_RETURN =
            patternP2PUnitPort("pattern_p2p_unit_port_return", UnitPortType.RETURN);
    public static final RegistryObject<PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_EXTRACT =
            patternP2PUnitPort("pattern_p2p_unit_port_extract", UnitPortType.EXTRACT);
    public static final RegistryObject<PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_REDSTONE =
            patternP2PUnitPort("pattern_p2p_unit_port_redstone", UnitPortType.REDSTONE);
    public static final RegistryObject<PartItem<PatternP2PUnitPortPart>> PATTERN_P2P_UNIT_PORT_ENERGY =
            patternP2PUnitPort("pattern_p2p_unit_port_energy", UnitPortType.ENERGY);

    public static final RegistryObject<Item> PRODUCT_EXTRACTION_CARD = ITEMS.register(
            "product_extraction_card", () -> Upgrades.createUpgradeCardItem(new Item.Properties()));
    public static final RegistryObject<ComponentPlacerItem> COMPONENT_PLACER = ITEMS.register(
            COMPONENT_PLACER_ID, () -> new ComponentPlacerItem(
                    AEConfig.instance().getWirelessTerminalBattery(), new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> CREATIVE_TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2_batchcraft"))
                    .icon(() -> PATTERN_P2P_TUNNEL_INPUT.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(PATTERN_P2P_TUNNEL_INPUT.get());
                        output.accept(PATTERN_P2P_TUNNEL_OUTPUT.get());
                        output.accept(PATTERN_P2P_TUNNEL_ENERGY.get());
                        for (RegistryObject<ColoredPartItem<PatternP2PUnitManagerPart>> manager
                                : PATTERN_P2P_UNIT_MANAGERS.values()) output.accept(manager.get());
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
                        ComponentPlacerItem placer = COMPONENT_PLACER.get();
                        ItemStack charged = placer.getDefaultInstance();
                        placer.injectAEPower(charged, placer.getAEMaxPower(charged), Actionable.MODULATE);
                        output.accept(charged);
                    }).build());

    private ModContent() {
    }

    private static RegistryObject<PartItem<PatternP2PUnitPortPart>> patternP2PUnitPort(
            String id, UnitPortType type) {
        return ITEMS.<PartItem<PatternP2PUnitPortPart>>register(id,
                () -> new PatternP2PUnitPortItem(new Item.Properties(), type));
    }

    private static Map<AEColor, RegistryObject<ColoredPartItem<PatternP2PUnitManagerPart>>>
    registerPatternP2PUnitManagers() {
        Map<AEColor, RegistryObject<ColoredPartItem<PatternP2PUnitManagerPart>>> result =
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

    public static boolean isPatternP2PFrequencySource(ResourceLocation itemId) {
        if (itemId == null) {
            return false;
        }
        if (itemId.equals(IPartItem.getId(PATTERN_P2P_TUNNEL_INPUT.get()))) {
            return true;
        }
        return PATTERN_P2P_UNIT_MANAGERS.values().stream()
                .anyMatch(holder -> itemId.equals(IPartItem.getId(holder.get())));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
