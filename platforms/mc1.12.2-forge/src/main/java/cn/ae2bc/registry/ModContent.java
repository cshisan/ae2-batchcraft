package cn.ae2bc.registry;

import cn.ae2bc.item.PatternP2PPartItem;
import cn.ae2bc.item.PatternP2PTunnelEnergyPartItem;
import cn.ae2bc.item.PatternP2PUnitManagerItem;
import cn.ae2bc.item.PatternP2PUnitPortItem;
import cn.ae2bc.placer.ComponentPlacerItem;

import net.minecraft.item.Item;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.core.unit.UnitPortType;

@Mod.EventBusSubscriber(modid = Ae2bcMod.MOD_ID)
public final class ModContent {
    public static PatternP2PPartItem INPUT;
    public static PatternP2PPartItem OUTPUT;
    public static PatternP2PTunnelEnergyPartItem ENERGY;
    public static PatternP2PUnitManagerItem UNIT_MANAGER;
    public static ComponentPlacerItem COMPONENT_PLACER;
    public static final java.util.Map<UnitPortType, PatternP2PUnitPortItem> UNIT_PORTS =
            new java.util.EnumMap<UnitPortType, PatternP2PUnitPortItem>(UnitPortType.class);

    private ModContent() { }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        INPUT = new PatternP2PPartItem(false);
        INPUT.setRegistryName(Ae2bcMod.MOD_ID, "pattern_p2p_tunnel_input");
        INPUT.setTranslationKey("ae2_batchcraft.pattern_p2p_tunnel_input");
        OUTPUT = new PatternP2PPartItem(true);
        OUTPUT.setRegistryName(Ae2bcMod.MOD_ID, "pattern_p2p_tunnel_output");
        OUTPUT.setTranslationKey("ae2_batchcraft.pattern_p2p_tunnel_output");
        INPUT.setCreativeTab(CreativeTabs.MISC);
        OUTPUT.setCreativeTab(CreativeTabs.MISC);
        ENERGY = new PatternP2PTunnelEnergyPartItem();
        ENERGY.setRegistryName(Ae2bcMod.MOD_ID, "pattern_p2p_tunnel_energy");
        ENERGY.setTranslationKey("ae2_batchcraft.pattern_p2p_tunnel_energy");
        ENERGY.setCreativeTab(CreativeTabs.MISC);
        UNIT_MANAGER = new PatternP2PUnitManagerItem();
        UNIT_MANAGER.setRegistryName(Ae2bcMod.MOD_ID, "pattern_p2p_unit_manager");
        UNIT_MANAGER.setTranslationKey("ae2_batchcraft.pattern_p2p_unit_manager");
        UNIT_MANAGER.setCreativeTab(CreativeTabs.MISC);
        COMPONENT_PLACER = new ComponentPlacerItem();
        COMPONENT_PLACER.setRegistryName(Ae2bcMod.MOD_ID, "component_placer");
        COMPONENT_PLACER.setTranslationKey("ae2_batchcraft.component_placer");
        COMPONENT_PLACER.setCreativeTab(CreativeTabs.MISC);
        for (UnitPortType type : UnitPortType.values()) {
            PatternP2PUnitPortItem port = new PatternP2PUnitPortItem(type);
            port.setRegistryName(Ae2bcMod.MOD_ID, "pattern_p2p_unit_port_" + type.getId());
            port.setTranslationKey("ae2_batchcraft.pattern_p2p_unit_port_" + type.getId());
            port.setCreativeTab(CreativeTabs.MISC);
            UNIT_PORTS.put(type, port);
        }
        event.getRegistry().register(INPUT);
        event.getRegistry().register(OUTPUT);
        event.getRegistry().register(ENERGY);
        event.getRegistry().register(UNIT_MANAGER);
        event.getRegistry().register(COMPONENT_PLACER);
        event.getRegistry().registerAll(UNIT_PORTS.values().toArray(new Item[UNIT_PORTS.size()]));
    }
}
