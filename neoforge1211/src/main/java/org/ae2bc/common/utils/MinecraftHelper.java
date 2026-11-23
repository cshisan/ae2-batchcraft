package org.ae2bc.common.utils;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.ae2bc.common.Utils;

import java.util.Map;
import java.util.function.Supplier;

public class MinecraftHelper {
    public static ListTag keyCountMap2listTag(Map<AEKey, Long> map, HolderLookup.Provider registries) {
        ListTag listtag = new ListTag();
        for (Map.Entry<AEKey, Long> entry : map.entrySet()) {
            GenericStack stack = new GenericStack(entry.getKey(), entry.getValue());
            listtag.add(GenericStack.writeTag(registries, stack));
        }
        return listtag;
    }

    public static void listTag2keyCountMap(CompoundTag data, HolderLookup.Provider registries, String datName, Map<AEKey, Long> map) {
        map.clear();
        ListTag listTag = data.getList(datName, Tag.TAG_COMPOUND);
        for (int index = 0; index < listTag.size(); index++) {
            CompoundTag tag = listTag.getCompound(index);
            GenericStack stack = GenericStack.readTag(registries, tag);
            if (stack != null && stack.amount() > 0) {
                map.merge(stack.what(), stack.amount(), Utils::saturatingAdd);
            }
        }
    }

    public static Supplier<Level> level(Player player) {
        return player::level;
    }
}
