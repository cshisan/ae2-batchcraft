package org.ae2bc.part.general.provider_filter;

import appeng.core.network.ClientboundPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.ae2bc.Ae2bcMod;
import org.jspecify.annotations.NonNull;

public record Pp2mpProviderFilterPacket(
        int containerId, int binding, boolean synced, Pp2mpProviderFilterConfig config
) implements ClientboundPacket {
    public static final Type<Pp2mpProviderFilterPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pp2mp_provider_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Pp2mpProviderFilterPacket> STREAM_CODEC =
            StreamCodec.ofMember(Pp2mpProviderFilterPacket::write, Pp2mpProviderFilterPacket::read);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(Ae2bcMod.MOD_ID).playToClient(TYPE, STREAM_CODEC, ClientboundPacket::handleOnClient);
    }

    /* 资源身份、数量和权限作为同一份状态接收。 */
    private static Pp2mpProviderFilterPacket read(RegistryFriendlyByteBuf data) {
        int containerId = data.readVarInt();
        int binding = data.readInt();
        boolean synced = data.readBoolean();
        return new Pp2mpProviderFilterPacket(containerId, binding, synced, Pp2mpProviderFilterConfig.read(data));
    }

    private void write(RegistryFriendlyByteBuf data) {
        data.writeVarInt(this.containerId);
        data.writeInt(this.binding);
        data.writeBoolean(this.synced);
        this.config.write(data);
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
