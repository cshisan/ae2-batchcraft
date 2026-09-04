package cn.ae2bc;

import cn.ae2bc.core.ModConstants;
import cn.ae2bc.client.PatternP2PTunnelEnergyScreen;
import cn.ae2bc.client.PatternP2PUnitManagerScreen;
import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;
import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;

import appeng.api.networking.IGridNode;
import cn.ae2bc.core.dispatch.RoundRobinPolicy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import cn.ae2bc.menu.PatternP2PTunnelMenu;
import cn.ae2bc.client.PatternP2PTunnelScreen;
import cn.ae2bc.network.ModNetwork;
import appeng.api.AEApi;
import cn.ae2bc.part.PatternP2PTunnelPart;

@Mod(
        modid = Ae2bcMod.MOD_ID,
        name = "AE2 BatchCraft",
        version = "1.0.1",
        dependencies = "required-after:appliedenergistics2@[rv6-stable-7,)"
)
public final class Ae2bcMod {
    public static final String MOD_ID = ModConstants.MOD_ID;
    public static final int GUI_EXTRACTION_BASE = 4100;
    public static final int GUI_ENERGY_BASE = 4200;
    public static final int GUI_UNIT_MANAGER = 4300;
    public static final int GUI_COMPONENT_PLACER = 4400;
    public static final int GUI_COMPONENT_PLACER_CRAFT_AMOUNT = 4401;
    public static final int GUI_COMPONENT_PLACER_CRAFT_CONFIRM = 4402;
    public static final int GUI_UNIT_PORT_OUTPUT_BASE = 4500;
    public static final int GUI_UNIT_PORT_INPUT_BASE = 4600;
    @Mod.Instance(MOD_ID)
    public static Ae2bcMod INSTANCE;
    private static final Class<?> AE2_NODE_API = IGridNode.class;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        AE2_NODE_API.getName();
        RoundRobinPolicy.index(0, 0, 1);
        AEApi.instance().registries().partModels().registerModels(
                PatternP2PTunnelPart.getModels().stream()
                        .flatMap(model -> model.getModels().stream())
                        .collect(java.util.stream.Collectors.toList()));
        AEApi.instance().registries().partModels().registerModels(
                cn.ae2bc.part.PatternP2PTunnelEnergyPart.MODEL_ID);
        AEApi.instance().registries().partModels().registerModels(
                cn.ae2bc.part.PatternP2PUnitManagerPart.MODEL_ID);
        AEApi.instance().registries().partModels().registerModels(
                cn.ae2bc.part.PatternP2PUnitManagerPart.GLASS_MODEL_ID);
        AEApi.instance().registries().partModels().registerModels(
                cn.ae2bc.part.PatternP2PUnitPortPart.getModels().stream()
                        .flatMap(model -> model.getModels().stream())
                        .distinct()
                        .collect(java.util.stream.Collectors.toList()));
        ModNetwork.initialize();
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new IGuiHandler() {
            @Override public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
                if (id == GUI_COMPONENT_PLACER) {
                    return new cn.ae2bc.menu.ComponentPlacerMenu(player,
                            EnumHand.values()[Math.floorMod(y, EnumHand.values().length)]);
                }
                if (id == GUI_COMPONENT_PLACER_CRAFT_AMOUNT
                        || id == GUI_COMPONENT_PLACER_CRAFT_CONFIRM) {
                    EnumHand hand = EnumHand.values()[Math.floorMod(y, EnumHand.values().length)];
                    net.minecraft.item.ItemStack placer = player.getHeldItem(hand);
                    if (!(placer.getItem() instanceof cn.ae2bc.placer.ComponentPlacerItem)
                            || !cn.ae2bc.placer.ComponentPlacerItem.hasCraftingCard(placer)) return null;
                    cn.ae2bc.placer.ComponentPlacerNetworkAccess networkAccess =
                            new cn.ae2bc.placer.ComponentPlacerNetworkAccess(player, hand, placer);
                    if (!networkAccess.isConnected()) return null;
                    if (id == GUI_COMPONENT_PLACER_CRAFT_AMOUNT) {
                        return new cn.ae2bc.menu.ComponentPlacerCraftAmountMenu(
                                player, hand, networkAccess);
                    }
                    return new cn.ae2bc.menu.ComponentPlacerCraftConfirmMenu(
                            player, hand, networkAccess);
                }
                if (id == GUI_UNIT_MANAGER) {
                    return new cn.ae2bc.menu.PatternP2PUnitManagerMenu(
                            player, new BlockPos(x, y, z));
                }
                if (id >= GUI_UNIT_PORT_OUTPUT_BASE && id < GUI_UNIT_PORT_OUTPUT_BASE + 6) {
                    return new cn.ae2bc.menu.UnitPortOutputConfigMenu(player, new BlockPos(x, y, z),
                            EnumFacing.values()[id - GUI_UNIT_PORT_OUTPUT_BASE]);
                }
                if (id >= GUI_UNIT_PORT_INPUT_BASE && id < GUI_UNIT_PORT_INPUT_BASE + 6) {
                    return new cn.ae2bc.menu.UnitPortInputConfigMenu(player, new BlockPos(x, y, z),
                            EnumFacing.values()[id - GUI_UNIT_PORT_INPUT_BASE]);
                }
                if (id >= GUI_ENERGY_BASE && id < GUI_ENERGY_BASE + 6) {
                    return new cn.ae2bc.menu.PatternP2PTunnelEnergyMenu(player, new BlockPos(x, y, z),
                            EnumFacing.values()[id - GUI_ENERGY_BASE]);
                }
                if (id < GUI_EXTRACTION_BASE || id >= GUI_EXTRACTION_BASE + 6) return null;
                return new PatternP2PTunnelMenu(player, new BlockPos(x, y, z),
                        EnumFacing.values()[id - GUI_EXTRACTION_BASE]);
            }
            @SideOnly(Side.CLIENT)
            @Override public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
                if (id == GUI_COMPONENT_PLACER) {
                    cn.ae2bc.menu.ComponentPlacerMenu container = new cn.ae2bc.menu.ComponentPlacerMenu(
                            player, EnumHand.values()[Math.floorMod(y, EnumHand.values().length)]);
                    return new cn.ae2bc.client.ComponentPlacerScreen(container, player.inventory);
                }
                if (id == GUI_COMPONENT_PLACER_CRAFT_AMOUNT
                        || id == GUI_COMPONENT_PLACER_CRAFT_CONFIRM) {
                    EnumHand hand = EnumHand.values()[Math.floorMod(y, EnumHand.values().length)];
                    net.minecraft.item.ItemStack placer = player.getHeldItem(hand);
                    if (!(placer.getItem() instanceof cn.ae2bc.placer.ComponentPlacerItem)) return null;
                    cn.ae2bc.placer.ComponentPlacerNetworkAccess networkAccess =
                            new cn.ae2bc.placer.ComponentPlacerNetworkAccess(player, hand, placer);
                    if (id == GUI_COMPONENT_PLACER_CRAFT_AMOUNT) {
                        return new cn.ae2bc.client.ComponentPlacerCraftAmountScreen(
                                player.inventory, networkAccess.getTerminal(), placer, Math.max(1, z));
                    }
                    return new cn.ae2bc.client.ComponentPlacerCraftConfirmScreen(
                            player.inventory, networkAccess.getTerminal());
                }
                if (id == GUI_UNIT_MANAGER) {
                    cn.ae2bc.menu.PatternP2PUnitManagerMenu container =
                            new cn.ae2bc.menu.PatternP2PUnitManagerMenu(
                                    player, new BlockPos(x, y, z));
                    return new cn.ae2bc.client.PatternP2PUnitManagerScreen(container, player.inventory);
                }
                if (id >= GUI_UNIT_PORT_OUTPUT_BASE && id < GUI_UNIT_PORT_OUTPUT_BASE + 6) {
                    cn.ae2bc.menu.UnitPortOutputConfigMenu container =
                            new cn.ae2bc.menu.UnitPortOutputConfigMenu(player, new BlockPos(x, y, z),
                                    EnumFacing.values()[id - GUI_UNIT_PORT_OUTPUT_BASE]);
                    return new cn.ae2bc.client.UnitPortOutputConfigScreen(container, player.inventory);
                }
                if (id >= GUI_UNIT_PORT_INPUT_BASE && id < GUI_UNIT_PORT_INPUT_BASE + 6) {
                    cn.ae2bc.menu.UnitPortInputConfigMenu container =
                            new cn.ae2bc.menu.UnitPortInputConfigMenu(player, new BlockPos(x, y, z),
                                    EnumFacing.values()[id - GUI_UNIT_PORT_INPUT_BASE]);
                    return new cn.ae2bc.client.UnitPortInputConfigScreen(container, player.inventory);
                }
                if (id >= GUI_ENERGY_BASE && id < GUI_ENERGY_BASE + 6) {
                    cn.ae2bc.menu.PatternP2PTunnelEnergyMenu container =
                            new cn.ae2bc.menu.PatternP2PTunnelEnergyMenu(player,
                                    new BlockPos(x, y, z), EnumFacing.values()[id - GUI_ENERGY_BASE]);
                    return new cn.ae2bc.client.PatternP2PTunnelEnergyScreen(container, player.inventory);
                }
                if (id < GUI_EXTRACTION_BASE || id >= GUI_EXTRACTION_BASE + 6) return null;
                PatternP2PTunnelMenu container = new PatternP2PTunnelMenu(player,
                        new BlockPos(x, y, z), EnumFacing.values()[id - GUI_EXTRACTION_BASE]);
                return new PatternP2PTunnelScreen(container, player.inventory);
            }
        });
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        AEApi.instance().registries().wireless().registerWirelessHandler(
                cn.ae2bc.registry.ModContent.COMPONENT_PLACER);
        appeng.api.config.Upgrades.CRAFTING.registerItem(
                new net.minecraft.item.ItemStack(cn.ae2bc.registry.ModContent.COMPONENT_PLACER), 1);
    }
}
