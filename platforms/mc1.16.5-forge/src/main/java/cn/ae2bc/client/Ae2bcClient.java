package cn.ae2bc.client;

import cn.ae2bc.client.model.PatternP2PUnitManagerBakedModel;
import cn.ae2bc.client.model.PatternP2PUnitPortIdentityGeometry;
import cn.ae2bc.item.PatternP2PUnitManagerItem;
import cn.ae2bc.registry.ModContent;

import appeng.items.parts.ColoredPartItem;
import net.minecraft.item.Item;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoaderRegistry;

import cn.ae2bc.Ae2bcMod;

@Mod.EventBusSubscriber(modid = Ae2bcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class Ae2bcClient {
    private Ae2bcClient() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ScreenManager.register(
                ModContent.PATTERN_P2P_SETTINGS.get(), PatternP2PTunnelScreen::new));
        event.enqueueWork(() -> ScreenManager.register(
                ModContent.PATTERN_P2P_ENERGY_SETTINGS.get(), PatternP2PTunnelEnergyScreen::new));
        event.enqueueWork(() -> ScreenManager.register(
                ModContent.UNIT_MANAGER_SETTINGS.get(), PatternP2PUnitManagerScreen::new));
        event.enqueueWork(() -> ScreenManager.register(
                ModContent.COMPONENT_PLACER_MENU.get(), ComponentPlacerScreen::new));
        event.enqueueWork(() -> {
            Item[] managers = ModContent.UNIT_MANAGERS.values().stream()
                    .map(holder -> (Item) holder.get()).toArray(Item[]::new);
            Minecraft.getInstance().getItemColors().register((stack, tintIndex) ->
                    0xFF000000 | ((ColoredPartItem<?>) stack.getItem()).getColor()
                            .getVariantByTintIndex(tintIndex), managers);
        });
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoaderRegistry.registerLoader(new net.minecraft.util.ResourceLocation(
                Ae2bcMod.MOD_ID, "pattern_p2p_unit_port_identity"),
                PatternP2PUnitPortIdentityGeometry.INSTANCE);
    }

    @SubscribeEvent
    public static void bakeModels(ModelBakeEvent event) {
        for (java.util.Map.Entry<net.minecraft.util.ResourceLocation,
                net.minecraft.client.renderer.model.IBakedModel> entry : event.getModelRegistry().entrySet()) {
            entry.setValue(PatternP2PUnitManagerBakedModel.wrap(entry.getValue()));
        }
    }
}
