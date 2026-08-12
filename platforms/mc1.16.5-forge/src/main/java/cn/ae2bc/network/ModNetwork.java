package cn.ae2bc.network;

import cn.ae2bc.extension.PatternEncodingTermMenuExtension;
import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;
import cn.ae2bc.menu.PatternP2PTunnelMenu;
import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.pattern.MaterialOutputConfigData;

import java.util.function.Supplier;

import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.logic.EnergyDistributionMode;
import cn.ae2bc.Ae2bcMod;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraft.network.PacketBuffer;

public final class ModNetwork {
    public static final int PLACER_SET_DIRECTION = 0;
    public static final int PLACER_ADJUST_X = 1;
    public static final int PLACER_ADJUST_Y = 2;
    public static final int PLACER_ADJUST_Z = 3;
    public static final int PLACER_RESET_OFFSETS = 4;
    public static final int PLACER_CLEAR_SELECTION = 5;
    public static final int PLACER_EXECUTE = 6;
    public static final int PLACER_LOAD_FREQUENCY = 7;
    public static final int PLACER_RESET_FREQUENCY = 8;
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ae2bcMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void initialize() {
        CHANNEL.registerMessage(0, ExtractionSettingsPacket.class,
                ExtractionSettingsPacket::encode, ExtractionSettingsPacket::decode,
                ExtractionSettingsPacket::handle);
        CHANNEL.registerMessage(1, MaterialOutputConfigPacket.class,
                MaterialOutputConfigPacket::encode, MaterialOutputConfigPacket::decode,
                MaterialOutputConfigPacket::handle);
        CHANNEL.registerMessage(2, EnergySettingsPacket.class,
                EnergySettingsPacket::encode, EnergySettingsPacket::decode,
                EnergySettingsPacket::handle);
        CHANNEL.registerMessage(3, UnitManagerFrequencyPacket.class,
                UnitManagerFrequencyPacket::encode, UnitManagerFrequencyPacket::decode,
                UnitManagerFrequencyPacket::handle);
        CHANNEL.registerMessage(4, UnitManagerSettingsPacket.class,
                UnitManagerSettingsPacket::encode, UnitManagerSettingsPacket::decode,
                UnitManagerSettingsPacket::handle);
        CHANNEL.registerMessage(5, ComponentPlacerActionPacket.class,
                ComponentPlacerActionPacket::encode, ComponentPlacerActionPacket::decode,
                ComponentPlacerActionPacket::handle);
    }

    public static void sendComponentPlacerAction(ComponentPlacerMenu menu, int action, int value) {
        CHANNEL.sendToServer(new ComponentPlacerActionPacket(menu.containerId, action, value));
    }

    private static final class ComponentPlacerActionPacket {
        private final int windowId;
        private final int action;
        private final int value;

        private ComponentPlacerActionPacket(int windowId, int action, int value) {
            this.windowId = windowId;
            this.action = action;
            this.value = value;
        }

        private static void encode(ComponentPlacerActionPacket packet, PacketBuffer buffer) {
            buffer.writeVarInt(packet.windowId);
            buffer.writeByte(packet.action);
            buffer.writeInt(packet.value);
        }

        private static ComponentPlacerActionPacket decode(PacketBuffer buffer) {
            return new ComponentPlacerActionPacket(buffer.readVarInt(), buffer.readUnsignedByte(), buffer.readInt());
        }

        private static void handle(ComponentPlacerActionPacket packet,
                Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayerEntity sender = context.getSender();
            context.enqueueWork(() -> {
                if (sender != null && sender.containerMenu instanceof ComponentPlacerMenu
                        && sender.containerMenu.containerId == packet.windowId) {
                    ((ComponentPlacerMenu) sender.containerMenu).handleAction(packet.action, packet.value);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public static void sendExtractionSettings(PatternP2PTunnelMenu menu, boolean enabled, int interval, int amount) {
        PatternP2PUnitSettings current = menu.getSettings();
        CHANNEL.sendToServer(new ExtractionSettingsPacket(menu.containerId, menu.getPos(), menu.getSide(), enabled,
                new PatternP2PUnitSettings(current.getReturnMode(), current.isBreakRecovery(),
                        interval, amount, current.getRedstoneMode(), current.getRedstoneStrength(),
                        current.getPulseWidthTicks(), current.getPulsePeriodTicks()),
                menu.isSyncInputSettings(), false));
    }

    public static void sendPatternSettings(PatternP2PTunnelMenu menu, boolean enabled,
            PatternP2PUnitSettings settings, boolean syncInputSettings, boolean resetTask) {
        CHANNEL.sendToServer(new ExtractionSettingsPacket(menu.containerId, menu.getPos(), menu.getSide(), enabled,
                settings, syncInputSettings, resetTask));
    }

    public static void sendMaterialOutputConfig(int windowId, long[] packed) {
        CHANNEL.sendToServer(new MaterialOutputConfigPacket(windowId, packed));
    }

    public static void sendEnergySettings(PatternP2PTunnelEnergyMenu menu, boolean pullEnabled,
            EnergyDistributionMode mode) {
        CHANNEL.sendToServer(new EnergySettingsPacket(
                menu.containerId, menu.getPos(), menu.getSide(), pullEnabled, mode));
    }

    public static void sendUnitManagerFrequency(PatternP2PUnitManagerMenu menu, int frequency) {
        CHANNEL.sendToServer(new UnitManagerFrequencyPacket(menu.containerId, menu.getPos(), frequency));
    }

    public static void sendUnitManagerSettings(PatternP2PUnitManagerMenu menu, int frequency,
            PatternP2PUnitSettings settings) {
        sendUnitManagerSettings(menu, frequency, settings, menu.isSyncMainConfiguration(),
                menu.getEnergyDistributionMode(), false);
    }

    public static void sendUnitManagerSettings(PatternP2PUnitManagerMenu menu, int frequency,
            PatternP2PUnitSettings settings, boolean syncMainConfiguration,
            EnergyDistributionMode energyDistributionMode, boolean resetTask) {
        CHANNEL.sendToServer(new UnitManagerSettingsPacket(menu.containerId, menu.getPos(), frequency, settings,
                syncMainConfiguration, energyDistributionMode, resetTask));
    }

    private static final class UnitManagerSettingsPacket {
        private final int windowId;
        private final BlockPos pos;
        private final int frequency;
        private final PatternP2PUnitSettings settings;
        private final boolean syncMainConfiguration;
        private final boolean resetTask;
        private final EnergyDistributionMode energyDistributionMode;

        private UnitManagerSettingsPacket(int windowId, BlockPos pos, int frequency, PatternP2PUnitSettings settings) {
            this(windowId, pos, frequency, settings, true, EnergyDistributionMode.EVEN, false);
        }

        private UnitManagerSettingsPacket(int windowId, BlockPos pos, int frequency, PatternP2PUnitSettings settings,
                boolean syncMainConfiguration, EnergyDistributionMode energyDistributionMode,
                boolean resetTask) {
            this.windowId = windowId;
            this.pos = pos;
            this.frequency = cn.ae2bc.core.frequency.FrequencyLimits.clamp(frequency);
            this.settings = settings == null ? PatternP2PUnitSettings.DEFAULT : settings;
            this.syncMainConfiguration = syncMainConfiguration;
            this.resetTask = resetTask;
            this.energyDistributionMode = energyDistributionMode == null
                    ? EnergyDistributionMode.EVEN : energyDistributionMode;
        }

        private static void encode(UnitManagerSettingsPacket packet, PacketBuffer buffer) {
            buffer.writeInt(packet.windowId);
            buffer.writeBlockPos(packet.pos);
            buffer.writeInt(packet.frequency);
            buffer.writeByte(packet.settings.getReturnMode().getId());
            buffer.writeBoolean(packet.settings.isBreakRecovery());
            buffer.writeInt(packet.settings.getExtractionInterval());
            buffer.writeInt(packet.settings.getExtractionAmount());
            buffer.writeByte(packet.settings.getRedstoneMode().getId());
            buffer.writeByte(packet.settings.getRedstoneStrength());
            buffer.writeInt(packet.settings.getPulseWidthTicks());
            buffer.writeInt(packet.settings.getPulsePeriodTicks());
            buffer.writeBoolean(packet.syncMainConfiguration);
            buffer.writeByte(packet.energyDistributionMode.getId());
            buffer.writeBoolean(packet.resetTask);
        }

        private static UnitManagerSettingsPacket decode(PacketBuffer buffer) {
            int windowId = buffer.readInt();
            BlockPos pos = buffer.readBlockPos();
            int frequency = buffer.readInt();
            PatternP2PUnitSettings settings = new PatternP2PUnitSettings(
                    cn.ae2bc.logic.ReturnMode.fromId(buffer.readUnsignedByte()), buffer.readBoolean(),
                    buffer.readInt(), buffer.readInt(),
                    cn.ae2bc.logic.RedstoneOutputMode.fromId(buffer.readUnsignedByte()),
                    buffer.readUnsignedByte(), buffer.readInt(), buffer.readInt());
            return new UnitManagerSettingsPacket(windowId, pos, frequency, settings,
                    buffer.readBoolean(), EnergyDistributionMode.fromId(buffer.readUnsignedByte()),
                    buffer.readBoolean());
        }

        private static void handle(UnitManagerSettingsPacket packet,
                Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayerEntity sender = context.getSender();
            context.enqueueWork(() -> {
                if (sender == null || !(sender.containerMenu instanceof PatternP2PUnitManagerMenu)
                        || sender.containerMenu.containerId != packet.windowId
                        || !((PatternP2PUnitManagerMenu) sender.containerMenu).getPos().equals(packet.pos)
                        || sender.distanceToSqr(packet.pos.getX() + 0.5,
                        packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) return;
                PatternP2PUnitManagerPart part = PatternP2PUnitManagerMenu.findPart(sender, packet.pos);
                if (part != null) {
                    part.setFrequency(packet.frequency);
                    part.setSettings(packet.settings);
                    part.setSyncMainConfiguration(packet.syncMainConfiguration);
                    if (packet.resetTask) part.resetTaskState();
                }
            });
            context.setPacketHandled(true);
        }
    }

    private static final class UnitManagerFrequencyPacket {
        private final int windowId;
        private final BlockPos pos;
        private final int frequency;

        private UnitManagerFrequencyPacket(int windowId, BlockPos pos, int frequency) {
            this.windowId = windowId;
            this.pos = pos;
            this.frequency = cn.ae2bc.core.frequency.FrequencyLimits.clamp(frequency);
        }
        private static void encode(UnitManagerFrequencyPacket packet, PacketBuffer buffer) {
            buffer.writeInt(packet.windowId);
            buffer.writeBlockPos(packet.pos);
            buffer.writeInt(packet.frequency);
        }
        private static UnitManagerFrequencyPacket decode(PacketBuffer buffer) {
            return new UnitManagerFrequencyPacket(buffer.readInt(), buffer.readBlockPos(), buffer.readInt());
        }
        private static void handle(UnitManagerFrequencyPacket packet,
                Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayerEntity sender = context.getSender();
            context.enqueueWork(() -> {
                if (sender == null || !(sender.containerMenu instanceof PatternP2PUnitManagerMenu)
                        || sender.containerMenu.containerId != packet.windowId
                        || !((PatternP2PUnitManagerMenu) sender.containerMenu).getPos().equals(packet.pos)
                        || sender.distanceToSqr(packet.pos.getX() + 0.5,
                        packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) return;
                PatternP2PUnitManagerPart part = PatternP2PUnitManagerMenu.findPart(sender, packet.pos);
                if (part != null) part.setFrequency(packet.frequency);
            });
            context.setPacketHandled(true);
        }
    }

    private static final class EnergySettingsPacket {
        private final int windowId;
        private final BlockPos pos;
        private final Direction side;
        private final boolean pullEnabled;
        private final EnergyDistributionMode mode;

        private EnergySettingsPacket(int windowId, BlockPos pos, Direction side, boolean pullEnabled,
                EnergyDistributionMode mode) {
            this.windowId = windowId;
            this.pos = pos;
            this.side = side;
            this.pullEnabled = pullEnabled;
            this.mode = mode == null ? EnergyDistributionMode.EVEN : mode;
        }

        private static void encode(EnergySettingsPacket packet, PacketBuffer buffer) {
            buffer.writeInt(packet.windowId);
            buffer.writeBlockPos(packet.pos);
            buffer.writeByte(packet.side.ordinal());
            buffer.writeBoolean(packet.pullEnabled);
            buffer.writeByte(packet.mode.getId());
        }

        private static EnergySettingsPacket decode(PacketBuffer buffer) {
            int windowId = buffer.readInt();
            BlockPos pos = buffer.readBlockPos();
            Direction side = Direction.values()[buffer.readUnsignedByte() % Direction.values().length];
            return new EnergySettingsPacket(windowId, pos, side, buffer.readBoolean(),
                    EnergyDistributionMode.fromId(buffer.readUnsignedByte()));
        }

        private static void handle(EnergySettingsPacket packet,
                Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayerEntity sender = context.getSender();
            context.enqueueWork(() -> {
                if (sender == null || !(sender.containerMenu instanceof PatternP2PTunnelEnergyMenu)
                        || sender.containerMenu.containerId != packet.windowId
                        || !((PatternP2PTunnelEnergyMenu) sender.containerMenu).getPos().equals(packet.pos)
                        || ((PatternP2PTunnelEnergyMenu) sender.containerMenu).getSide() != packet.side
                        || sender.distanceToSqr(packet.pos.getX() + 0.5,
                        packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) return;
                PatternP2PTunnelEnergyPart part = PatternP2PTunnelEnergyMenu.findPart(sender, packet.pos, packet.side);
                if (part != null) part.setSettings(packet.pullEnabled, packet.mode);
            });
            context.setPacketHandled(true);
        }
    }

    private static final class ExtractionSettingsPacket {
        private final int windowId;
        private final BlockPos pos;
        private final Direction side;
        private final boolean enabled;
        private final PatternP2PUnitSettings settings;
        private final boolean syncInputSettings;
        private final boolean resetTask;

        private ExtractionSettingsPacket(int windowId, BlockPos pos, Direction side, boolean enabled,
                PatternP2PUnitSettings settings, boolean syncInputSettings, boolean resetTask) {
            this.windowId = windowId;
            this.pos = pos;
            this.side = side;
            this.enabled = enabled;
            this.settings = settings == null ? PatternP2PUnitSettings.DEFAULT : settings;
            this.syncInputSettings = syncInputSettings;
            this.resetTask = resetTask;
        }

        private static void encode(ExtractionSettingsPacket packet, PacketBuffer buffer) {
            buffer.writeInt(packet.windowId);
            buffer.writeBlockPos(packet.pos);
            buffer.writeByte(packet.side.ordinal());
            buffer.writeBoolean(packet.enabled);
            writeSettings(buffer, packet.settings);
            buffer.writeBoolean(packet.syncInputSettings);
            buffer.writeBoolean(packet.resetTask);
        }

        private static ExtractionSettingsPacket decode(PacketBuffer buffer) {
            int windowId = buffer.readInt();
            BlockPos pos = buffer.readBlockPos();
            Direction side = Direction.values()[buffer.readUnsignedByte() % Direction.values().length];
            boolean enabled = buffer.readBoolean();
            PatternP2PUnitSettings settings = readSettings(buffer);
            return new ExtractionSettingsPacket(windowId, pos, side, enabled, settings,
                    buffer.readBoolean(), buffer.readBoolean());
        }

        private static void handle(ExtractionSettingsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayerEntity sender = context.getSender();
            context.enqueueWork(() -> {
                if (sender == null || !(sender.containerMenu instanceof PatternP2PTunnelMenu)
                        || sender.containerMenu.containerId != packet.windowId
                        || !((PatternP2PTunnelMenu) sender.containerMenu).getPos().equals(packet.pos)
                        || ((PatternP2PTunnelMenu) sender.containerMenu).getSide() != packet.side
                        || sender.distanceToSqr(packet.pos.getX() + 0.5,
                        packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                    return;
                }
                PatternP2PTunnelPart part = PatternP2PTunnelMenu.findPart(sender, packet.pos, packet.side);
                if (part != null) {
                    if (part.isOutput()) {
                        part.setOutputSettings(packet.settings.getReturnMode(), packet.syncInputSettings);
                    } else {
                        part.setInputSettings(packet.enabled, packet.settings);
                    }
                    if (packet.resetTask) part.resetTaskState();
                }
            });
            context.setPacketHandled(true);
        }
    }

    private static void writeSettings(PacketBuffer buffer, PatternP2PUnitSettings settings) {
        buffer.writeByte(settings.getReturnMode().getId());
        buffer.writeBoolean(settings.isBreakRecovery());
        buffer.writeInt(settings.getExtractionInterval());
        buffer.writeInt(settings.getExtractionAmount());
        buffer.writeByte(settings.getRedstoneMode().getId());
        buffer.writeByte(settings.getRedstoneStrength());
        buffer.writeInt(settings.getPulseWidthTicks());
        buffer.writeInt(settings.getPulsePeriodTicks());
    }

    private static PatternP2PUnitSettings readSettings(PacketBuffer buffer) {
        return new PatternP2PUnitSettings(
                cn.ae2bc.logic.ReturnMode.fromId(buffer.readUnsignedByte()), buffer.readBoolean(),
                buffer.readInt(), buffer.readInt(),
                cn.ae2bc.logic.RedstoneOutputMode.fromId(buffer.readUnsignedByte()),
                buffer.readUnsignedByte(), buffer.readInt(), buffer.readInt());
    }

    private static final class MaterialOutputConfigPacket {
        private final int windowId;
        private final long[] packed;

        private MaterialOutputConfigPacket(int windowId, long[] packed) {
            this.windowId = windowId;
            this.packed = cn.ae2bc.pattern.MaterialOutputConfigCodec.normalize(packed);
        }

        private static void encode(MaterialOutputConfigPacket packet, PacketBuffer buffer) {
            buffer.writeInt(packet.windowId);
            buffer.writeInt(packet.packed.length);
            for (long value : packet.packed) buffer.writeLong(value);
        }

        private static MaterialOutputConfigPacket decode(PacketBuffer buffer) {
            int windowId = buffer.readInt();
            int length = buffer.readInt();
            if (length < 0 || length > cn.ae2bc.pattern.MaterialOutputConfigCodec.WORD_COUNT) {
                throw new IllegalArgumentException("Invalid material output config length: " + length);
            }
            long[] packed = new long[length];
            for (int i = 0; i < length; i++) packed[i] = buffer.readLong();
            return new MaterialOutputConfigPacket(windowId, packed);
        }

        private static void handle(MaterialOutputConfigPacket packet,
                Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayerEntity sender = context.getSender();
            context.enqueueWork(() -> {
                if (sender == null || sender.containerMenu == null
                        || sender.containerMenu.containerId != packet.windowId
                        || !(sender.containerMenu instanceof PatternEncodingTermMenuExtension)) {
                    return;
                }
                ((PatternEncodingTermMenuExtension) sender.containerMenu)
                        .ae2bc$setMaterialOutputConfig(
                                MaterialOutputConfigData.fromPacked(packet.packed));
            });
            context.setPacketHandled(true);
        }
    }
}
