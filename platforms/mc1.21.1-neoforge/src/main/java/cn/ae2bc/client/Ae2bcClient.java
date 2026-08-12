package cn.ae2bc.client;

import appeng.client.gui.style.StyleManager;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.menu.PatternP2PTunnelInputMenu;
import cn.ae2bc.menu.PatternP2PTunnelOutputMenu;
import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.menu.ProductExtractionMenu;
import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import appeng.items.parts.ColoredPartItem;
import cn.ae2bc.client.model.PatternP2PUnitManagerFrequencyGeometry;
import cn.ae2bc.client.model.PatternP2PUnitPortIdentityGeometry;
import cn.ae2bc.registry.ModContent;

@Mod(value = Ae2bcMod.MOD_ID, dist = Dist.CLIENT)
public final class Ae2bcClient {
    private static final String INPUT_SCREEN_STYLE = "/screens/ae2_batchcraft/pattern_p2p_tunnel_input.json";
    private static final String OUTPUT_SCREEN_STYLE = "/screens/ae2_batchcraft/pattern_p2p_tunnel_output.json";
    private static final String PLACER_SCREEN_STYLE = "/screens/ae2_batchcraft/component_placer.json";
    private static final String ENERGY_SCREEN_STYLE = "/screens/ae2_batchcraft/pattern_p2p_tunnel_energy.json";
    private static final String PRODUCT_EXTRACTION_SCREEN_STYLE = "/screens/ae2_batchcraft/product_extraction.json";
    private static final String PATTERN_P2P_UNIT_MANAGER_SCREEN_STYLE = "/screens/ae2_batchcraft/pattern_p2p_unit_manager.json";

    public Ae2bcClient(IEventBus modBus) {
        modBus.addListener(Ae2bcClient::registerScreens);
        modBus.addListener(Ae2bcClient::registerGeometryLoaders);
        modBus.addListener(Ae2bcClient::registerItemColors);
        NeoForge.EVENT_BUS.addListener(ComponentPlacerSelectionRenderer::render);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.<PatternP2PTunnelOutputMenu, PatternP2PTunnelOutputScreen>register(
                PatternP2PTunnelOutputMenu.TYPE, (menu, inventory, title) ->
                        new PatternP2PTunnelOutputScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(OUTPUT_SCREEN_STYLE)));
        event.<PatternP2PTunnelInputMenu, PatternP2PTunnelInputScreen>register(
                PatternP2PTunnelInputMenu.TYPE, (menu, inventory, title) ->
                        new PatternP2PTunnelInputScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(INPUT_SCREEN_STYLE)));
        event.<PatternP2PTunnelEnergyMenu, PatternP2PTunnelEnergyScreen>register(
                PatternP2PTunnelEnergyMenu.TYPE, (menu, inventory, title) ->
                        new PatternP2PTunnelEnergyScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(ENERGY_SCREEN_STYLE)));
        event.<ComponentPlacerMenu, ComponentPlacerScreen>register(
                ComponentPlacerMenu.TYPE, (menu, inventory, title) ->
                        new ComponentPlacerScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(PLACER_SCREEN_STYLE)));
        event.<ProductExtractionMenu, ProductExtractionScreen>register(
                ProductExtractionMenu.TYPE, (menu, inventory, title) ->
                        new ProductExtractionScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(PRODUCT_EXTRACTION_SCREEN_STYLE)));
        event.<PatternP2PUnitManagerMenu, PatternP2PUnitManagerScreen>register(
                PatternP2PUnitManagerMenu.TYPE, (menu, inventory, title) ->
                        new PatternP2PUnitManagerScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc(PATTERN_P2P_UNIT_MANAGER_SCREEN_STYLE)));
    }

    private static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pattern_p2p_unit_manager_frequency"),
                PatternP2PUnitManagerFrequencyGeometry.INSTANCE);
        event.register(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pattern_p2p_unit_port_identity"),
                PatternP2PUnitPortIdentityGeometry.INSTANCE);
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        Item[] managers = ModContent.PATTERN_P2P_UNIT_MANAGERS.values().stream()
                .map(holder -> (Item) holder.get())
                .toArray(Item[]::new);
        event.register((stack, tintIndex) -> FastColor.ARGB32.opaque(
                ((ColoredPartItem<?>) stack.getItem()).getColor().getVariantByTintIndex(tintIndex)), managers);
    }
}
