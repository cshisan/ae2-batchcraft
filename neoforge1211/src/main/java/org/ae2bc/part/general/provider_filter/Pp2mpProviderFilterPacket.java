package org.ae2bc.part.general.provider_filter;

import appeng.core.network.ClientboundPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.ae2bc.Ae2bcMod;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public record Pp2mpProviderFilterPacket(
        int containerId, int menuVersion, boolean synced,
        boolean whitelist, List<Long> limits
) implements ClientboundPacket {
    public static final Type<Pp2mpProviderFilterPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pp2mp_provider_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Pp2mpProviderFilterPacket> STREAM_CODEC =
            StreamCodec.ofMember(Pp2mpProviderFilterPacket::write, Pp2mpProviderFilterPacket::read);

    public Pp2mpProviderFilterPacket {
        if (limits.size() != Pp2mpProviderFilterData.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid provider filter slot count");
        }
        limits = List.copyOf(limits);
        for (long limit : limits) {
            if (limit < 0) {
                throw new IllegalArgumentException("Provider filter limits must be non-negative");
            }
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(Ae2bcMod.MOD_ID).playToClient(TYPE, STREAM_CODEC, ClientboundPacket::handleOnClient);
    }

    /* 标记资源由菜单槽位同步，此包只读取固定数量的目标值与编辑状态。 */
    private static Pp2mpProviderFilterPacket read(RegistryFriendlyByteBuf data) {
        int containerId = data.readVarInt();
        int menuVersion = data.readVarInt();
        boolean synced = data.readBoolean();
        boolean whitelist = data.readBoolean();
        List<Long> limits = new ArrayList<>(Pp2mpProviderFilterData.SLOT_COUNT);
        for (int slot = 0; slot < Pp2mpProviderFilterData.SLOT_COUNT; slot++) {
            limits.add(data.readVarLong());
        }
        return new Pp2mpProviderFilterPacket(containerId, menuVersion, synced, whitelist, limits);
    }

    private void write(RegistryFriendlyByteBuf data) {
        data.writeVarInt(this.containerId);
        data.writeVarInt(this.menuVersion);
        data.writeBoolean(this.synced);
        data.writeBoolean(this.whitelist);
        for (long limit : this.limits) {
            data.writeVarLong(limit);
        }
    }

    @Override
    public void handleOnClient(Player player) {
        if (player.containerMenu instanceof Pp2mpProviderFilterMenu menu && menu.containerId == this.containerId) {
            menu.receive(this);
        }
    }

    @Override
    public @NonNull Type<Pp2mpProviderFilterPacket> type() {
        return TYPE;
    }
}
