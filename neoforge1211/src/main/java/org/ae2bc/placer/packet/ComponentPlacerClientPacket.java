package org.ae2bc.placer.packet;

import appeng.core.network.ClientboundPacket;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.placer.DataModel;
import org.ae2bc.core.placer.SelectionStatus;
import org.ae2bc.placer.ComponentPlacerMenu;
import org.jspecify.annotations.NonNull;

public record ComponentPlacerClientPacket(int containerId, Direction direction,
                                          DataModel model) implements ClientboundPacket {
    public static final Type<ComponentPlacerClientPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "component_placer_client_packet")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentPlacerClientPacket> STREAM_CODEC = StreamCodec.ofMember(
            ComponentPlacerClientPacket::write, ComponentPlacerClientPacket::read
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Ae2bcMod.MOD_ID);
        registrar.playToClient(TYPE, STREAM_CODEC, ClientboundPacket::handleOnClient);
    }

    private static ComponentPlacerClientPacket read(RegistryFriendlyByteBuf data) {
        return new ComponentPlacerClientPacket(data.readInt(), data.readEnum(Direction.class), new DataModel(
                data.readBoolean(), data.readShort(), data.readShort(),
                RangeAttrPacket.read(data), RangeAttrPacket.read(data),
                data.readEnum(SelectionStatus.class),
                data.readBoolean(), data.readBoolean(),
                data.readBoolean(), data.readBoolean()
        ));
    }

    private void write(RegistryFriendlyByteBuf data) {
        data.writeInt(this.containerId);
        data.writeEnum(this.direction);
        data.writeBoolean(this.model.frequencyMode());
        data.writeShort(this.model.frequency());
        data.writeShort(this.model.unitId());
        RangeAttrPacket.write(data, this.model.pos1());
        RangeAttrPacket.write(data, this.model.pos2());
        data.writeEnum(this.model.selection());
        data.writeBoolean(this.model.hasCable());
        data.writeBoolean(this.model.hasPart());
        data.writeBoolean(this.model.hasSelection());
        data.writeBoolean(this.model.aeConnected());
    }

    @Override
    public void handleOnClient(Player player) {
        AbstractContainerMenu currentMenu = player.containerMenu;
        if (currentMenu instanceof ComponentPlacerMenu menu && currentMenu.containerId == this.containerId) {
            menu.change(this.direction, this.model());
        }
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
