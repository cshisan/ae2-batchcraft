package org.ae2bc.ioport;

import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.core.network.ClientboundPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.ae2bc.Ae2bcMod;
import org.jspecify.annotations.NonNull;

public record IoPortTransferPacket(
        int containerId, IoPortTransferData data,
        FullnessMode fullMode, OperationMode operationMode
) implements ClientboundPacket {
    public static final Type<IoPortTransferPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "io_port_transfer")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, IoPortTransferPacket> STREAM_CODEC =
            StreamCodec.ofMember(IoPortTransferPacket::write, IoPortTransferPacket::read);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(Ae2bcMod.MOD_ID).playToClient(TYPE, STREAM_CODEC, ClientboundPacket::handleOnClient);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        this.data.write(buffer);
        buffer.writeEnum(this.fullMode);
        buffer.writeEnum(this.operationMode);
    }

    private static IoPortTransferPacket read(RegistryFriendlyByteBuf buffer) {
        return new IoPortTransferPacket(buffer.readVarInt(), IoPortTransferData.read(buffer),
                buffer.readEnum(FullnessMode.class), buffer.readEnum(OperationMode.class));
    }

    @Override
    public void handleOnClient(Player player) {
        if (player.containerMenu.containerId == this.containerId
                && player.containerMenu instanceof Ae2bcIoPortMenu menu) {
            menu.applySnapshot(this);
        }
    }

    @Override
    public @NonNull Type<IoPortTransferPacket> type() {
        return TYPE;
    }
}
