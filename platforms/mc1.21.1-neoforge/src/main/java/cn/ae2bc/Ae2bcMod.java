package cn.ae2bc;

import appeng.api.AECapabilities;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.api.features.GridLinkables;
import appeng.api.networking.GridServices;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.items.tools.powered.powersink.PoweredItemCapabilities;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Ae2bcMod.MOD_ID)
public final class Ae2bcMod {
    public static final String MOD_ID = "ae2_batchcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ae2bcMod(IEventBus modBus) {
        GridServices.register(PatternP2PEnergyGridService.class, PatternP2PEnergyGridService.class);
        GridServices.register(PatternP2PTopologyGridService.class, PatternP2PTopologyGridService.class);
        GridServices.register(ProductExtractionGridService.class, ProductExtractionGridService.class);
        PatternP2PTunnelPart.registerModels();
        PatternP2PUnitManagerPart.registerModels();
        PatternP2PTunnelEnergyPart.registerModels();
        PatternP2PUnitPortPart.registerModels();
        ModContent.register(modBus);
        ModMenus.register(modBus);
        modBus.addListener(Ae2bcMod::registerPartCapabilities);
        modBus.addListener(Ae2bcMod::registerItemCapabilities);
        modBus.addListener(Ae2bcMod::commonSetup);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true,
                ComponentPlacerItem::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true,
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

    private static void registerPartCapabilities(RegisterPartCapabilitiesEvent event) {
        event.register(AECapabilities.CRAFTING_MACHINE,
                (part, side) -> part.isOutput() ? null : part,
                PatternP2PTunnelPart.class);
        event.register(AECapabilities.GENERIC_INTERNAL_INV,
                (part, side) -> part.isStandardOutput() ? part.getReturnInventory() : null,
                PatternP2PTunnelPart.class);
        event.register(AECapabilities.ME_STORAGE,
                (part, side) -> part.isStandardOutput() ? part.getReturnInventory() : null,
                PatternP2PTunnelPart.class);
        event.register(Capabilities.ItemHandler.BLOCK,
                (part, side) -> part.isStandardOutput() ? part.getReturnItemHandler() : null,
                PatternP2PTunnelPart.class);
        event.register(Capabilities.FluidHandler.BLOCK,
                (part, side) -> part.isStandardOutput() ? part.getReturnFluidHandler() : null,
                PatternP2PTunnelPart.class);
        event.register(Capabilities.EnergyStorage.BLOCK,
                (part, side) -> part.getEnergyStorage(),
                PatternP2PTunnelEnergyPart.class);
        event.register(AECapabilities.GENERIC_INTERNAL_INV,
                (part, side) -> part.isReturnPort() ? part.getReturnInventory() : null,
                PatternP2PUnitPortPart.class);
        event.register(AECapabilities.ME_STORAGE,
                (part, side) -> part.isReturnPort() ? part.getReturnStorage() : null,
                PatternP2PUnitPortPart.class);
        event.register(Capabilities.ItemHandler.BLOCK,
                (part, side) -> part.isReturnPort() ? part.getReturnItemHandler() : null,
                PatternP2PUnitPortPart.class);
        event.register(Capabilities.FluidHandler.BLOCK,
                (part, side) -> part.isReturnPort() ? part.getReturnFluidHandler() : null,
                PatternP2PUnitPortPart.class);
    }

    private static void registerItemCapabilities(RegisterCapabilitiesEvent event) {
        var placer = ModContent.COMPONENT_PLACER.get();
        event.registerItem(Capabilities.EnergyStorage.ITEM,
                (stack, context) -> new PoweredItemCapabilities(stack, placer), placer);
    }
}
