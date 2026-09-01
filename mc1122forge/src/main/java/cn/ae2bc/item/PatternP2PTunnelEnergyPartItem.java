package cn.ae2bc.item;

import cn.ae2bc.client.TooltipSupport;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;

import appeng.api.AEApi;
import appeng.api.parts.IPartItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.ITooltipFlag;
import java.util.List;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class PatternP2PTunnelEnergyPartItem extends Item implements IPartItem<PatternP2PTunnelEnergyPart> {
    public PatternP2PTunnelEnergyPartItem() { setMaxStackSize(64); }

    @Override public void addInformation(ItemStack stack, net.minecraft.world.World world,
            List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        TooltipSupport.add(tooltip, "tooltip.ae2_batchcraft.pattern_p2p.energy");
    }

    @Override public PatternP2PTunnelEnergyPart createPartFromItemStack(ItemStack stack) {
        return new PatternP2PTunnelEnergyPart(stack);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing facing, float hitX, float hitY, float hitZ) {
        return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, facing, player, hand, world);
    }
}
