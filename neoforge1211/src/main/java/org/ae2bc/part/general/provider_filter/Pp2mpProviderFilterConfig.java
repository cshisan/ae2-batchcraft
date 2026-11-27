package org.ae2bc.part.general.provider_filter;

import appeng.api.stacks.AEKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/* 规则快照只保存业务值，不包含实物升级卡、回调或同步来源。 */
public record Pp2mpProviderFilterConfig(boolean whitelist, List<Entry> entries) {
    public Pp2mpProviderFilterConfig {
        if (entries.size() != Pp2mpProviderFilterData.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid provider filter slot count");
        }
        entries = List.copyOf(entries);
    }

    public record Entry(@Nullable AEKey key, long amount) {
        public Entry {
            if (amount < 0) {
                throw new IllegalArgumentException("Provider filter limits must be non-negative");
            }
        }
    }

    /* 整组传输资源和数量，避免标记槽与目标数量显示错配。 */
    public void write(RegistryFriendlyByteBuf data) {
        data.writeBoolean(this.whitelist);
        for (Entry entry : this.entries) {
            AEKey.writeOptionalKey(data, entry.key());
            data.writeVarLong(entry.amount());
        }
    }

    public static Pp2mpProviderFilterConfig read(RegistryFriendlyByteBuf data) {
        boolean whitelist = data.readBoolean();
        List<Entry> entries = new ArrayList<>(Pp2mpProviderFilterData.SLOT_COUNT);
        for (int slot = 0; slot < Pp2mpProviderFilterData.SLOT_COUNT; slot++) {
            entries.add(new Entry(AEKey.readOptionalKey(data), data.readVarLong()));
        }
        return new Pp2mpProviderFilterConfig(whitelist, entries);
    }
}
