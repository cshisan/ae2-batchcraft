package org.ae2bc.client;

import appeng.client.gui.style.StyleManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.ioport.Ae2bcIoPortMenu;
import org.ae2bc.client.render.ComponentPlacerSelectionRender;
import org.ae2bc.client.render.FakeCraftingConverterRenderer;
import org.ae2bc.client.screen.*;
import org.ae2bc.client.screen.page.*;
import org.ae2bc.client.screen.page.adapter.InputPageAttrAdapter;
import org.ae2bc.client.screen.page.adapter.OutputPageAttrAdapter;
import org.ae2bc.client.screen.page.adapter.UnitPageAttrAdapter;
import org.ae2bc.part.Pp2mpPartModels;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterMenu;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterModel;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterMenu;
import org.ae2bc.part.input.Pp2mpInputMenu;
import org.ae2bc.part.output.Pp2mpOutputMenu;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakeMenu;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerItem;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerMenu;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerModel;
import org.ae2bc.part.unit.port.base.Pp2mpUnitInputTypePortMenu;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortMenu;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;
import org.ae2bc.pattern.Pp2mpEncodingTermMenu;
import org.ae2bc.pattern.Pp2mpPatternItem;
import org.ae2bc.pattern.Pp2mpPatternMenuHost;
import org.ae2bc.placer.ComponentPlacerItem;
import org.ae2bc.placer.ComponentPlacerMenu;

@Mod(value = Ae2bcMod.MOD_ID, dist = Dist.CLIENT)
public final class Ae2bcClient {

    public Ae2bcClient(IEventBus eventBus) {
        eventBus.addListener(Ae2bcClient::registerScreens);
        eventBus.addListener(FakeCraftingConverterRenderer::register);
        eventBus.addListener(FakeCraftingConverterModel::registerGeometryLoaders);
        eventBus.addListener(Pp2mpPartModels.Client::registerGeometryLoaders);
        eventBus.addListener(Pp2mpPatternItem::registerProperties);
        eventBus.addListener(Pp2mpUnitManagerItem::registerItemColors);
        eventBus.addListener(Pp2mpUnitManagerModel::registerGeometryLoaders);
        eventBus.addListener(Pp2mpUnitManagerModel::modifyBakingResult);
        eventBus.addListener(Pp2mpUnitPortPartModel::registerGeometryLoaders);

        NeoForge.EVENT_BUS.addListener(ComponentPlacerItem::handleScroll);
        NeoForge.EVENT_BUS.addListener(ComponentPlacerSelectionRender::render);
    }

    /* 注册界面 */
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.<Ae2bcIoPortMenu, Ae2bcIoPortScreen>register(Ae2bcIoPortMenu.TYPE, (menu, inventory, title) -> new Ae2bcIoPortScreen(
                menu, inventory, title, StyleManager.loadStyleDoc("/screens/ae2_batchcraft/io_port.json")));
        // 输入端
        event.<Pp2mpInputMenu, Pp2mpPagedScreen<Pp2mpInputMenu>>register(Pp2mpInputMenu.TYPE, (menu, inventory, title) -> {
            InputPageAttrAdapter adapter = new InputPageAttrAdapter(menu);
            return new Pp2mpPagedScreen<>(
                    menu, inventory, title, StyleManager.loadStyleDoc("/screens/ae2_batchcraft/pp2mp_input.json"),
                    new Pp2mpGeneralPage(null, adapter), new Pp2mpOutputPage(null, adapter),
                    new Pp2mpUnitPage(adapter, null), 
                    new Pp2mpUnitBreakPage(adapter, null), new Pp2mpUnitRedstonePage(adapter, null)
            );
        });

        // 输出端
        event.<Pp2mpOutputMenu, Pp2mpPagedScreen<Pp2mpOutputMenu>>register(Pp2mpOutputMenu.TYPE, (menu, inventory, title) -> {
            OutputPageAttrAdapter adapter = new OutputPageAttrAdapter(menu);
            return new Pp2mpPagedScreen<>(
                    menu, inventory, title,
                    StyleManager.loadStyleDoc("/screens/ae2_batchcraft/pp2mp_output.json"),
                    new Pp2mpGeneralPage(adapter, adapter), new Pp2mpOutputPage(adapter, adapter),
                    new Pp2mpResourceIntakePage(menu::openProviderFilter)
            );
        });

        // 资源接收端
        event.<Pp2mpResourceIntakeMenu, Pp2mpResourceIntakeScreen>register(
                Pp2mpResourceIntakeMenu.TYPE,
                (menu, inventory, title) -> new Pp2mpResourceIntakeScreen(
                        menu, inventory, title, StyleManager.loadStyleDoc("/screens/ae2_batchcraft/pp2mp_resource_intake.json")
                )
        );
        event.<Pp2mpProviderFilterMenu, Pp2mpProviderFilterScreen>register(
                Pp2mpProviderFilterMenu.TYPE, (menu, inventory, title) -> new Pp2mpProviderFilterScreen(
                        menu, inventory, title, StyleManager.loadStyleDoc("/screens/ae2_batchcraft/pp2mp_provider_filter.json")
                )
        );

        // 单元管理器
        event.<Pp2mpUnitManagerMenu, Pp2mpPagedScreen<Pp2mpUnitManagerMenu>>register(
                Pp2mpUnitManagerMenu.TYPE, (menu, inventory, title) -> {
                    UnitPageAttrAdapter adapter = new UnitPageAttrAdapter(menu);
                    return new Pp2mpPagedScreen<>(
                            menu, inventory, ScreenHeader.unitManagerTitle(menu.getPart().getCableColor()),
                            StyleManager.loadStyleDoc("/screens/ae2_batchcraft/pp2mp_unit.json"),
                            new Pp2mpGeneralPage(adapter, adapter), new Pp2mpUnitPage(adapter, adapter),
                            new Pp2mpUnitBreakPage(adapter, adapter),
                            new Pp2mpUnitRedstonePage(adapter, adapter), new Pp2mpResourceIntakePage(menu::openProviderFilter)
                    );
                }
        );

        // 输出型单元端口
        event.<Pp2mpUnitOutputTypePortMenu, Pp2mpUnitOutputTypeScreen>register(
                Pp2mpUnitOutputTypePortMenu.TYPE,
                (menu, inventory, title) -> new Pp2mpUnitOutputTypeScreen(
                        menu, inventory, title, StyleManager.loadStyleDoc("/screens/ae2_batchcraft/pp2mp_unit_port_output_type.json")
                )
        );
        // 输入型单元端口
        event.<Pp2mpUnitInputTypePortMenu, Pp2mpUnitInputTypeScreen>register(
                Pp2mpUnitInputTypePortMenu.TYPE,
                (menu, inventory, title) -> new Pp2mpUnitInputTypeScreen(
                        menu, inventory, title, StyleManager.loadStyleDoc("/screens/ae2_batchcraft/pp2mp_unit_port_input_type.json")
                )
        );

        // PP2MP样板
        event.<Pp2mpEncodingTermMenu, Pp2mpPatternScreen>register(
                Pp2mpEncodingTermMenu.TYPE,
                (menu, inventory, title) -> {
                    if (menu.getHost() instanceof Pp2mpPatternMenuHost host && !host.wasOpenedOnBlock()) {
                        return new Pp2mpCompactPatternScreen(
                                menu, inventory, title,
                                StyleManager.loadStyleDoc("/screens/ae2_batchcraft/pp2mp_encoding_terminal.json")
                        );
                    }

                    return new Pp2mpPatternScreen(
                            menu, inventory, title,
                            StyleManager.loadStyleDoc("/screens/ae2_batchcraft/pp2mp_network_encoding_terminal.json")
                    );
                }
        );

        // 部件放置器
        event.<ComponentPlacerMenu, ComponentPlacerScreen>register(
                ComponentPlacerMenu.TYPE,
                (menu, inventory, title) -> new ComponentPlacerScreen(
                        menu, inventory, title,
                        StyleManager.loadStyleDoc("/screens/ae2_batchcraft/component_placer.json")
                )
        );

        // 假合成转换器
        event.<FakeCraftingConverterMenu, FakeCraftingConverterScreen>register(
                FakeCraftingConverterMenu.TYPE,
                (menu, inventory, title) -> new FakeCraftingConverterScreen(
                        menu, inventory, title, StyleManager.loadStyleDoc("/screens/ae2_batchcraft/fake_crafting_converter.json")
                )
        );

    }
}
