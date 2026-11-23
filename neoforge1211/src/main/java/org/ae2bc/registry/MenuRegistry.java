package org.ae2bc.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.ioport.Ae2bcIoPortMenu;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterMenu;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterMenu;
import org.ae2bc.part.input.Pp2mpInputMenu;
import org.ae2bc.part.output.Pp2mpOutputMenu;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakeMenu;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerMenu;
import org.ae2bc.part.unit.port.base.Pp2mpUnitInputTypePortMenu;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortMenu;
import org.ae2bc.pattern.Pp2mpEncodingTermMenu;
import org.ae2bc.placer.ComponentPlacerMenu;

public class MenuRegistry {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Ae2bcMod.MOD_ID);

    public static void register(IEventBus bus) {
        // 注册界面菜单
        MENUS.register("pp2mp_input", () -> Pp2mpInputMenu.TYPE);
        MENUS.register("pp2mp_output", () -> Pp2mpOutputMenu.TYPE);
        MENUS.register("pp2mp_resource_intake", () -> Pp2mpResourceIntakeMenu.TYPE);
        MENUS.register("pp2mp_unit_manager", () -> Pp2mpUnitManagerMenu.TYPE);
        MENUS.register("pp2mp_unit_output_type_port", () -> Pp2mpUnitOutputTypePortMenu.TYPE);
        MENUS.register("pp2mp_unit_input_type_port", () -> Pp2mpUnitInputTypePortMenu.TYPE);
        MENUS.register("pp2mp_encoding_term", () -> Pp2mpEncodingTermMenu.TYPE);
        MENUS.register("component_placer", () -> ComponentPlacerMenu.TYPE);
        MENUS.register("fake_crafting_converter", () -> FakeCraftingConverterMenu.TYPE);
        MENUS.register("io_port", () -> Ae2bcIoPortMenu.TYPE);

        MENUS.register("pp2mp_provider_filter", () -> Pp2mpProviderFilterMenu.TYPE);

        MENUS.register(bus);
    }
}
