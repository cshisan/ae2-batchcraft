package cn.ae2bc.item;

import cn.ae2bc.client.TooltipSupport;
import cn.ae2bc.part.PatternP2PTunnelPart;

import appeng.api.AEApi;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPart;
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

/** Custom part item because AE2 rv6's ItemPart only contains its built-in PartType enum. */
public final class PatternP2PPartItem extends Item implements IPartItem<PatternP2PTunnelPart> {
    private final boolean output;

    public PatternP2PPartItem(boolean output) {
        this.output = output;
        setMaxStackSize(64);
    }

    @Override public void addInformation(ItemStack stack, World world, List<String> tooltip,
            ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        TooltipSupport.add(tooltip, output
                ? "tooltip.ae2_batchcraft.pattern_p2p.output"
                : "tooltip.ae2_batchcraft.pattern_p2p.input");
    }

    @Override
    public PatternP2PTunnelPart createPartFromItemStack(ItemStack stack) {
        return new PatternP2PTunnelPart(stack, output);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing facing, float hitX, float hitY, float hitZ) {
        return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, facing, player, hand, world);
    }
}
