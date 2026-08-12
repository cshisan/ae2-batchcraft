package cn.ae2bc;


import appeng.api.features.GridLinkables;
import appeng.api.networking.GridServices;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import cn.ae2bc.client.Ae2bcClient;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.placer.ComponentPlacerItem;
import cn.ae2bc.logic.PatternP2PEnergyGridService;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.logic.ProductExtractionGridService;
import cn.ae2bc.registry.ModContent;
import cn.ae2bc.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Ae2bcMod.MOD_ID)
public final class Ae2bcMod {
    public static final String MOD_ID = "ae2_batchcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ae2bcMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        GridServices.register(PatternP2PEnergyGridService.class, PatternP2PEnergyGridService.class);
        GridServices.register(PatternP2PTopologyGridService.class, PatternP2PTopologyGridService.class);
        GridServices.register(ProductExtractionGridService.class, ProductExtractionGridService.class);
        PatternP2PTunnelPart.registerModels();
        PatternP2PUnitManagerPart.registerModels();
        PatternP2PTunnelEnergyPart.registerModels();
        PatternP2PUnitPortPart.registerModels();
        ModContent.register(modBus);
        ModMenus.register(modBus);
        modBus.addListener(Ae2bcMod::commonSetup);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Ae2bcClient.register(modBus));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true,
                ComponentPlacerItem::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true,
                PatternP2PTunnelPart::onRightClickBlock);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GridLinkables.register(
                    ModContent.COMPONENT_PLACER.get(), WirelessTerminalItem.LINKABLE_HANDLER);
            Upgrades.add(AEItems.ENERGY_CARD, ModContent.COMPONENT_PLACER.get(), 2);
            Upgrades.add(AEItems.CRAFTING_CARD, ModContent.COMPONENT_PLACER.get(), 1);
            ModMenus.verifyRegistrations();
        });
    }

}
