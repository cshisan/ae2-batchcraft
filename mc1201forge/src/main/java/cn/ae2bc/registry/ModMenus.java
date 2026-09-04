package cn.ae2bc.registry;

import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;
import cn.ae2bc.menu.PatternP2PTunnelInputMenu;
import cn.ae2bc.menu.PatternP2PTunnelOutputMenu;
import cn.ae2bc.menu.ProductExtractionMenu;
import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import cn.ae2bc.menu.UnitPortOutputConfigMenu;
import cn.ae2bc.menu.UnitPortInputConfigMenu;
import appeng.core.AppEng;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ModMenus {
    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        // Loading these types queues registration through AE2's cross-version menu builder.
        MenuType<?>[] ignored = {
                PatternP2PTunnelInputMenu.TYPE,
                PatternP2PTunnelOutputMenu.TYPE,
                PatternP2PTunnelEnergyMenu.TYPE,
                ComponentPlacerMenu.TYPE,
                ProductExtractionMenu.TYPE,
                PatternP2PUnitManagerMenu.TYPE,
                UnitPortOutputConfigMenu.TYPE,
                UnitPortInputConfigMenu.TYPE
        };
    }

    public static void verifyRegistrations() {
        verifyRegistration("pattern_p2p_tunnel_input", PatternP2PTunnelInputMenu.TYPE);
        verifyRegistration("pattern_p2p_tunnel_output", PatternP2PTunnelOutputMenu.TYPE);
        verifyRegistration("pattern_p2p_tunnel_energy", PatternP2PTunnelEnergyMenu.TYPE);
        verifyRegistration(ModContent.COMPONENT_PLACER_ID, ComponentPlacerMenu.TYPE);
        verifyRegistration("product_extraction", ProductExtractionMenu.TYPE);
        verifyRegistration("pattern_p2p_unit_manager", PatternP2PUnitManagerMenu.TYPE);
        verifyRegistration("unit_port_output_config", UnitPortOutputConfigMenu.TYPE);
        verifyRegistration("unit_port_input_config", UnitPortInputConfigMenu.TYPE);
        Ae2bcMod.LOGGER.info("Verified AE2 BatchCraft menu registrations");
    }

    private static void verifyRegistration(String path, MenuType<?> type) {
        // AE2 15.4's MenuTypeBuilder owns registration and always uses the AE2 namespace.
        ResourceLocation expectedId = AppEng.makeId(path);
        ResourceLocation actualId = BuiltInRegistries.MENU.getKey(type);
        if (!expectedId.equals(actualId)) {
            throw new IllegalStateException("Menu type " + expectedId
                    + " was registered as " + actualId);
        }
    }
}
