package cn.ae2bc;

import appeng.api.config.Actionable;
import appeng.api.definitions.IParts;
import appeng.core.AEConfig;
import appeng.items.parts.PartItem;
import cn.ae2bc.registry.ModContent;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.part.PatternP2PTunnelPart;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import appeng.core.Api;
import appeng.api.config.Upgrades;

/** Forge 36 entry point. All AE2 8-specific registration stays in this platform. */
@Mod(Ae2bcMod.MOD_ID)
public final class Ae2bcMod {
    public static final String MOD_ID = "ae2_batchcraft";

    public Ae2bcMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModContent.register(bus);
        ModNetwork.initialize();
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Api.instance().registries().wireless().registerWirelessHandler(ModContent.COMPONENT_PLACER.get());
            Upgrades.CAPACITY.registerItem(ModContent.COMPONENT_PLACER.get(), 2,
                    "gui.ae2_batchcraft.component_placer");
            Upgrades.CRAFTING.registerItem(ModContent.COMPONENT_PLACER.get(), 1,
                    "gui.ae2_batchcraft.component_placer");
        });
    }
}
