package cn.ae2bc.item;

import cn.ae2bc.client.TooltipSupport;
import cn.ae2bc.part.PatternP2PUnitManagerPart;

import appeng.api.AEApi;
import appeng.api.parts.IPartItem;
import appeng.api.util.AEColor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.NonNullList;
import java.util.List;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class PatternP2PUnitManagerItem extends Item implements IPartItem<PatternP2PUnitManagerPart> {
    public PatternP2PUnitManagerItem() {
        setMaxStackSize(64);
        setHasSubtypes(true);
    }

    public AEColor getColor(ItemStack stack) {
        int metadata = stack == null ? 0 : stack.getMetadata();
        if (metadata <= 0 || metadata > AEColor.VALID_COLORS.size()) return AEColor.TRANSPARENT;
        return AEColor.VALID_COLORS.get(metadata - 1);
    }

    public ItemStack stack(AEColor color) {
        int metadata = color == null || color == AEColor.TRANSPARENT
                ? 0 : AEColor.VALID_COLORS.indexOf(color) + 1;
        return new ItemStack(this, 1, Math.max(0, metadata));
    }

    @Override public int getMetadata(int damage) { return damage; }

    @Override
    public String getTranslationKey(ItemStack stack) {
        AEColor color = getColor(stack);
        return color == AEColor.TRANSPARENT ? super.getTranslationKey(stack)
                : "item.ae2_batchcraft." + color.name().toLowerCase(java.util.Locale.ROOT)
                        + "_pattern_p2p_unit_manager";
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        items.add(stack(AEColor.TRANSPARENT));
        for (AEColor color : AEColor.VALID_COLORS) items.add(stack(color));
    }

    @Override public void addInformation(ItemStack stack, World world, List<String> tooltip,
            ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        TooltipSupport.add(tooltip, "tooltip.ae2_batchcraft.pattern_p2p.unit_manager");
    }
    @Override public PatternP2PUnitManagerPart createPartFromItemStack(ItemStack stack) {
        return new PatternP2PUnitManagerPart(stack);
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, facing, player, hand, world);
    }
}
