package cn.ae2bc.registry;

import cn.ae2bc.item.PatternP2PUnitManagerItem;
import cn.ae2bc.item.PatternP2PPartItem;
import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;
import cn.ae2bc.menu.PatternP2PTunnelMenu;
import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.placer.ComponentPlacerItem;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.inventory.container.ContainerType;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject;
import appeng.core.Api;
import appeng.api.util.AEColor;
import net.minecraft.util.ResourceLocation;

import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.core.unit.UnitPortType;

/** Minimal AE2 8 registration surface. */
public final class ModContent {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Ae2bcMod.MOD_ID);
    private static final DeferredRegister<ContainerType<?>> CONTAINERS =
            DeferredRegister.create(ForgeRegistries.CONTAINERS, Ae2bcMod.MOD_ID);

    public static final RegistryObject<PatternP2PPartItem<PatternP2PTunnelPart>> PATTERN_P2P_INPUT = ITEMS.register(
            "pattern_p2p_tunnel_input", () -> new PatternP2PPartItem<PatternP2PTunnelPart>(
                    new Item.Properties().tab(ItemGroup.TAB_MISC),
                    stack -> new PatternP2PTunnelPart(stack, false),
                    "tooltip.ae2_batchcraft.pattern_p2p.input"));
    public static final RegistryObject<PatternP2PPartItem<PatternP2PTunnelPart>> PATTERN_P2P_OUTPUT = ITEMS.register(
            "pattern_p2p_tunnel_output", () -> new PatternP2PPartItem<PatternP2PTunnelPart>(
                    new Item.Properties().tab(ItemGroup.TAB_MISC),
                    stack -> new PatternP2PTunnelPart(stack, true),
                    "tooltip.ae2_batchcraft.pattern_p2p.output"));
    public static final RegistryObject<PatternP2PPartItem<PatternP2PTunnelEnergyPart>> PATTERN_P2P_ENERGY = ITEMS.register(
            "pattern_p2p_tunnel_energy", () -> new PatternP2PPartItem<PatternP2PTunnelEnergyPart>(
                    new Item.Properties().tab(ItemGroup.TAB_MISC), PatternP2PTunnelEnergyPart::new,
                    "tooltip.ae2_batchcraft.pattern_p2p.energy"));
    public static final RegistryObject<ComponentPlacerItem> COMPONENT_PLACER = ITEMS.register(
            "component_placer", () -> new ComponentPlacerItem(new Item.Properties().tab(ItemGroup.TAB_MISC)));
    public static final java.util.Map<AEColor, RegistryObject<PatternP2PUnitManagerItem<PatternP2PUnitManagerPart>>>
            UNIT_MANAGERS = registerUnitManagers();
    public static final RegistryObject<PatternP2PUnitManagerItem<PatternP2PUnitManagerPart>> UNIT_MANAGER =
            UNIT_MANAGERS.get(AEColor.TRANSPARENT);
    public static final java.util.Map<UnitPortType, RegistryObject<PatternP2PPartItem<PatternP2PUnitPortPart>>> UNIT_PORTS =
            registerUnitPorts();
    public static final RegistryObject<ContainerType<PatternP2PTunnelMenu>> PATTERN_P2P_SETTINGS =
            CONTAINERS.register("pattern_p2p_settings",
                    () -> IForgeContainerType.create(PatternP2PTunnelMenu::new));
    public static final RegistryObject<ContainerType<PatternP2PTunnelEnergyMenu>> PATTERN_P2P_ENERGY_SETTINGS =
            CONTAINERS.register("pattern_p2p_energy_settings",
                    () -> IForgeContainerType.create(PatternP2PTunnelEnergyMenu::new));
    public static final RegistryObject<ContainerType<PatternP2PUnitManagerMenu>> UNIT_MANAGER_SETTINGS =
            CONTAINERS.register("pattern_p2p_unit_manager_settings",
                    () -> IForgeContainerType.create(PatternP2PUnitManagerMenu::new));
    public static final RegistryObject<ContainerType<ComponentPlacerMenu>> COMPONENT_PLACER_MENU =
            CONTAINERS.register("component_placer",
                    () -> IForgeContainerType.create(ComponentPlacerMenu::new));

    private ModContent() {
    }

    private static java.util.Map<UnitPortType, RegistryObject<PatternP2PPartItem<PatternP2PUnitPortPart>>> registerUnitPorts() {
        java.util.Map<UnitPortType, RegistryObject<PatternP2PPartItem<PatternP2PUnitPortPart>>> result =
                new java.util.EnumMap<UnitPortType, RegistryObject<PatternP2PPartItem<PatternP2PUnitPortPart>>>(UnitPortType.class);
        for (UnitPortType type : UnitPortType.values()) {
            final UnitPortType captured = type;
            result.put(type, ITEMS.register("pattern_p2p_unit_port_" + type.getId(),
                    () -> new PatternP2PPartItem<PatternP2PUnitPortPart>(
                            new Item.Properties().tab(ItemGroup.TAB_MISC),
                            stack -> new PatternP2PUnitPortPart(stack, captured),
                            "tooltip.ae2_batchcraft.pattern_p2p.unit_port." + captured.getId(),
                            "tooltip.ae2_batchcraft.pattern_p2p.binding")));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static java.util.Map<AEColor, RegistryObject<PatternP2PUnitManagerItem<PatternP2PUnitManagerPart>>>
            registerUnitManagers() {
        java.util.Map<AEColor, RegistryObject<PatternP2PUnitManagerItem<PatternP2PUnitManagerPart>>> result =
                new java.util.EnumMap<AEColor, RegistryObject<PatternP2PUnitManagerItem<PatternP2PUnitManagerPart>>>(
                        AEColor.class);
        for (AEColor color : AEColor.values()) {
            final AEColor captured = color;
            String id = color == AEColor.TRANSPARENT
                    ? "pattern_p2p_unit_manager" : color.registryPrefix + "_pattern_p2p_unit_manager";
            result.put(color, ITEMS.register(id, () -> new PatternP2PUnitManagerItem<PatternP2PUnitManagerPart>(
                    new Item.Properties().tab(ItemGroup.TAB_MISC), PatternP2PUnitManagerPart::new, captured,
                    "tooltip.ae2_batchcraft.pattern_p2p.unit_manager")));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    public static Item getUnitManagerItem(AEColor color) {
        RegistryObject<PatternP2PUnitManagerItem<PatternP2PUnitManagerPart>> item = UNIT_MANAGERS.get(color);
        return item == null ? UNIT_MANAGER.get() : item.get();
    }

    public static void register(IEventBus bus) {
        registerPartModels();
        ITEMS.register(bus);
        CONTAINERS.register(bus);
    }

    /**
     * AE2 freezes this registry during ModelRegistryEvent, before Forge's common setup phase.
     * Register during mod construction so every custom cable-bus model reaches the baker.
     */
    private static void registerPartModels() {
        Api.instance().registries().partModels().registerModels(
                PatternP2PTunnelPart.getModels().stream()
                        .flatMap(model -> model.getModels().stream())
                        .collect(java.util.stream.Collectors.toList()));
        Api.instance().registries().partModels().registerModels(
                java.util.Arrays.asList(
                        PatternP2PTunnelEnergyPart.MODEL_ID,
                        PatternP2PUnitManagerPart.MODEL_ID,
                        PatternP2PUnitManagerPart.FREQUENCY_MODEL_ID,
                        PatternP2PUnitManagerPart.GLASS_MODEL_ID));
        Api.instance().registries().partModels().registerModels(
                PatternP2PUnitPortPart.getModels().stream()
                        .flatMap(model -> model.getModels().stream())
                        .collect(java.util.stream.Collectors.toList()));
    }
}
