package org.ae2bc.placer.packet;

import appeng.core.network.ServerboundPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.placer.ComponentPlacerData;
import org.ae2bc.placer.ComponentPlacerItem;
import org.jspecify.annotations.NonNull;

public record ComponentPlacerServerPacket(boolean frequencyMode) implements ServerboundPacket {
    public static final Type<ComponentPlacerServerPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "component_placer_server_packet")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentPlacerServerPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeBoolean(packet.frequencyMode()),
            buffer -> new ComponentPlacerServerPacket(buffer.readBoolean())
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Ae2bcMod.MOD_ID);
        registrar.playToServer(TYPE, STREAM_CODEC, ServerboundPacket::handleOnServer);
    }

    @Override
    public void handleOnServer(ServerPlayer player) {
        ItemStack stack = ComponentPlacerItem.getHeldPlacer(player);
        if (!stack.isEmpty()) {
            ComponentPlacerData.frequencyMode(stack, this.frequencyMode);
            player.displayClientMessage(Component.translatable(this.frequencyMode
                    ? "message.ae2_batchcraft.component_placer.mode.frequency"
                    : "message.ae2_batchcraft.component_placer.mode.placement"
            ), true);
        }
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
