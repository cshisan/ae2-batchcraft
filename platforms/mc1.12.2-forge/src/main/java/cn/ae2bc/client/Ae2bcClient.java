package cn.ae2bc.client;

import cn.ae2bc.client.model.PatternP2PUnitManagerBakedModel;
import cn.ae2bc.client.model.PatternP2PUnitPortIdentityGeometry;
import cn.ae2bc.item.PatternP2PUnitPortItem;
import cn.ae2bc.registry.ModContent;

import cn.ae2bc.Ae2bcMod;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Ae2bcMod.MOD_ID, value = Side.CLIENT)
public final class Ae2bcClient {
    private Ae2bcClient() { }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoaderRegistry.registerLoader(PatternP2PUnitPortIdentityGeometry.INSTANCE);
        ModelLoader.setCustomModelResourceLocation(ModContent.INPUT, 0,
                new ModelResourceLocation(ModContent.INPUT.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModContent.OUTPUT, 0,
                new ModelResourceLocation(ModContent.OUTPUT.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModContent.ENERGY, 0,
                new ModelResourceLocation(ModContent.ENERGY.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModContent.COMPONENT_PLACER, 0,
                new ModelResourceLocation(ModContent.COMPONENT_PLACER.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModContent.UNIT_MANAGER, 0,
                managerModel("pattern_p2p_unit_manager"));
        for (appeng.api.util.AEColor color : appeng.api.util.AEColor.VALID_COLORS) {
            int metadata = appeng.api.util.AEColor.VALID_COLORS.indexOf(color) + 1;
            ModelLoader.setCustomModelResourceLocation(ModContent.UNIT_MANAGER, metadata,
                    managerModel(color.name().toLowerCase(java.util.Locale.ROOT)
                            + "_pattern_p2p_unit_manager"));
        }
        for (PatternP2PUnitPortItem port : ModContent.UNIT_PORTS.values()) {
            ModelLoader.setCustomModelResourceLocation(port, 0,
                    new ModelResourceLocation(port.getRegistryName(), "inventory"));
        }
    }

    @SubscribeEvent
    public static void registerItemColors(ColorHandlerEvent.Item event) {
        event.getItemColors().registerItemColorHandler((stack, tintIndex) ->
                        0xFF000000 | ModContent.UNIT_MANAGER.getColor(stack)
                                .getVariantByTintIndex(tintIndex),
                ModContent.UNIT_MANAGER);
    }

    @SubscribeEvent
    public static void bakeModels(ModelBakeEvent event) {
        java.util.Set<ModelResourceLocation> keys = new java.util.HashSet<ModelResourceLocation>();
        for (ModelResourceLocation key : event.getModelRegistry().getKeys()) keys.add(key);
        for (ModelResourceLocation key : keys) {
            net.minecraft.client.renderer.block.model.IBakedModel model = event.getModelRegistry().getObject(key);
            if (model != null) event.getModelRegistry().putObject(key, PatternP2PUnitManagerBakedModel.wrap(model));
        }
    }

    private static ModelResourceLocation managerModel(String id) {
        return new ModelResourceLocation(Ae2bcMod.MOD_ID + ":" + id, "inventory");
    }
}
