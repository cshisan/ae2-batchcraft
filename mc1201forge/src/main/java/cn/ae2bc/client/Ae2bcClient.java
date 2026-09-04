package cn.ae2bc.client;

import appeng.client.gui.style.StyleManager;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.menu.PatternP2PTunnelInputMenu;
import cn.ae2bc.menu.PatternP2PTunnelOutputMenu;
import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.menu.ProductExtractionMenu;
import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import cn.ae2bc.menu.UnitPortOutputConfigMenu;
import cn.ae2bc.menu.UnitPortInputConfigMenu;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.minecraft.client.gui.screens.MenuScreens;
import appeng.items.parts.ColoredPartItem;
import cn.ae2bc.client.model.PatternP2PUnitManagerFrequencyGeometry;
import cn.ae2bc.client.model.PatternP2PUnitPortIdentityGeometry;
import cn.ae2bc.registry.ModContent;

public final class Ae2bcClient {
    private static final String INPUT_SCREEN_STYLE = "/screens/ae2_batchcraft/pattern_p2p_tunnel_input.json";
    private static final String OUTPUT_SCREEN_STYLE = "/screens/ae2_batchcraft/pattern_p2p_tunnel_output.json";
    private static final String PLACER_SCREEN_STYLE = "/screens/ae2_batchcraft/component_placer.json";
    private static final String ENERGY_SCREEN_STYLE = "/screens/ae2_batchcraft/pattern_p2p_tunnel_energy.json";
    private static final String PRODUCT_EXTRACTION_SCREEN_STYLE = "/screens/ae2_batchcraft/product_extraction.json";
    private static final String PATTERN_P2P_UNIT_MANAGER_SCREEN_STYLE = "/screens/ae2_batchcraft/pattern_p2p_unit_manager.json";
    private static final String UNIT_PORT_OUTPUT_CONFIG_SCREEN_STYLE = "/screens/ae2_batchcraft/unit_port_output_config.json";
    private static final String UNIT_PORT_INPUT_CONFIG_SCREEN_STYLE = "/screens/ae2_batchcraft/unit_port_input_config.json";

    private Ae2bcClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(Ae2bcClient::clientSetup);
        modBus.addListener(Ae2bcClient::registerGeometryLoaders);
        modBus.addListener(Ae2bcClient::registerItemColors);
        MinecraftForge.EVENT_BUS.addListener(ComponentPlacerSelectionRenderer::render);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
        MenuScreens.<PatternP2PTunnelOutputMenu, PatternP2PTunnelOutputScreen>register(
                PatternP2PTunnelOutputMenu.TYPE, (menu, inventory, title) ->
                        new PatternP2PTunnelOutputScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(OUTPUT_SCREEN_STYLE)));
        MenuScreens.<PatternP2PTunnelInputMenu, PatternP2PTunnelInputScreen>register(
                PatternP2PTunnelInputMenu.TYPE, (menu, inventory, title) ->
                        new PatternP2PTunnelInputScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(INPUT_SCREEN_STYLE)));
        MenuScreens.<PatternP2PTunnelEnergyMenu, PatternP2PTunnelEnergyScreen>register(
                PatternP2PTunnelEnergyMenu.TYPE, (menu, inventory, title) ->
                        new PatternP2PTunnelEnergyScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(ENERGY_SCREEN_STYLE)));
        MenuScreens.<ComponentPlacerMenu, ComponentPlacerScreen>register(
                ComponentPlacerMenu.TYPE, (menu, inventory, title) ->
                        new ComponentPlacerScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(PLACER_SCREEN_STYLE)));
        MenuScreens.<ProductExtractionMenu, ProductExtractionScreen>register(
                ProductExtractionMenu.TYPE, (menu, inventory, title) ->
                        new ProductExtractionScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(PRODUCT_EXTRACTION_SCREEN_STYLE)));
        MenuScreens.<PatternP2PUnitManagerMenu, PatternP2PUnitManagerScreen>register(
                PatternP2PUnitManagerMenu.TYPE, (menu, inventory, title) ->
                        new PatternP2PUnitManagerScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(PATTERN_P2P_UNIT_MANAGER_SCREEN_STYLE)));
        MenuScreens.<UnitPortOutputConfigMenu, UnitPortOutputConfigScreen>register(
                UnitPortOutputConfigMenu.TYPE, (menu, inventory, title) ->
                        new UnitPortOutputConfigScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(UNIT_PORT_OUTPUT_CONFIG_SCREEN_STYLE)));
        MenuScreens.<UnitPortInputConfigMenu, UnitPortInputConfigScreen>register(
                UnitPortInputConfigMenu.TYPE, (menu, inventory, title) ->
                        new UnitPortInputConfigScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(UNIT_PORT_INPUT_CONFIG_SCREEN_STYLE)));
        });
    }

    private static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("pattern_p2p_unit_manager_frequency",
                PatternP2PUnitManagerFrequencyGeometry.INSTANCE);
        event.register("pattern_p2p_unit_port_identity",
                PatternP2PUnitPortIdentityGeometry.INSTANCE);
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        Item[] managers = ModContent.PATTERN_P2P_UNIT_MANAGERS.values().stream()
                .map(holder -> (Item) holder.get())
                .toArray(Item[]::new);
        event.register((stack, tintIndex) -> 0xFF000000 | ((ColoredPartItem<?>) stack.getItem())
                .getColor().getVariantByTintIndex(tintIndex), managers);
    }
}
