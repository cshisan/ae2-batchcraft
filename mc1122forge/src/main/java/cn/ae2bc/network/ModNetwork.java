package cn.ae2bc.network;

import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;
import cn.ae2bc.menu.PatternP2PTunnelMenu;
import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.menu.ComponentPlacerCraftAmountMenu;
import cn.ae2bc.menu.ComponentPlacerCraftConfirmMenu;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.pattern.PatternEncodingTermMenuState;

import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.core.dispatch.TaskAllocationMode;
import cn.ae2bc.logic.EnergyDistributionMode;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Server-validated settings transport for the 1.12.2 client screen. */
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
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(
            "ae2_batchcraft");

    private ModNetwork() { }

    public static void initialize() {
        CHANNEL.registerMessage(SettingsHandler.class, SettingsMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(MaterialOutputConfigHandler.class, MaterialOutputConfigMessage.class, 1, Side.SERVER);
        CHANNEL.registerMessage(EnergyHandler.class, EnergyMessage.class, 2, Side.SERVER);
        CHANNEL.registerMessage(UnitManagerHandler.class, UnitManagerMessage.class, 3, Side.SERVER);
        CHANNEL.registerMessage(ComponentPlacerActionHandler.class, ComponentPlacerActionMessage.class, 4, Side.SERVER);
        CHANNEL.registerMessage(ComponentPlacerCraftRequestHandler.class,
                ComponentPlacerCraftRequestMessage.class, 5, Side.SERVER);
        CHANNEL.registerMessage(ComponentPlacerCraftReturnHandler.class,
                ComponentPlacerCraftReturnMessage.class, 6, Side.SERVER);
        CHANNEL.registerMessage(UnitPortPriorityHandler.class,
                UnitPortPriorityMessage.class, 7, Side.SERVER);
        CHANNEL.registerMessage(UnitPortStateHandler.class,
                UnitPortStateMessage.class, 8, Side.CLIENT);
        CHANNEL.registerMessage(TaskAllocationModeHandler.class,
                TaskAllocationModeMessage.class, 9, Side.SERVER);
    }

    public static void sendComponentPlacerAction(ComponentPlacerMenu menu, int action, int value) {
        CHANNEL.sendToServer(new ComponentPlacerActionMessage(menu.windowId, action, value));
    }

    public static void sendComponentPlacerCraftRequest(int windowId, int amount, boolean autoStart) {
        CHANNEL.sendToServer(new ComponentPlacerCraftRequestMessage(windowId, amount, autoStart));
    }

    public static void sendComponentPlacerCraftReturn(int windowId) {
        CHANNEL.sendToServer(new ComponentPlacerCraftReturnMessage(windowId));
    }

    public static final class ComponentPlacerCraftReturnMessage implements IMessage {
        private int windowId;

        public ComponentPlacerCraftReturnMessage() {
        }

        private ComponentPlacerCraftReturnMessage(int windowId) {
            this.windowId = windowId;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
        }
    }

    public static final class ComponentPlacerCraftReturnHandler
            implements IMessageHandler<ComponentPlacerCraftReturnMessage, IMessage> {
        @Override
        public IMessage onMessage(ComponentPlacerCraftReturnMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer == null || player.openContainer.windowId != message.windowId) return;
                net.minecraft.util.EnumHand hand;
                if (player.openContainer instanceof ComponentPlacerCraftAmountMenu) {
                    hand = ((ComponentPlacerCraftAmountMenu) player.openContainer).getHand();
                } else if (player.openContainer instanceof ComponentPlacerCraftConfirmMenu) {
                    hand = ((ComponentPlacerCraftConfirmMenu) player.openContainer).getHand();
                } else {
                    return;
                }
                net.minecraft.item.ItemStack placer = player.getHeldItem(hand);
                if (!(placer.getItem() instanceof cn.ae2bc.placer.ComponentPlacerItem)) return;
                int slot = hand == net.minecraft.util.EnumHand.MAIN_HAND
                        ? player.inventory.currentItem : 40;
                player.openGui(cn.ae2bc.Ae2bcMod.INSTANCE,
                        cn.ae2bc.Ae2bcMod.GUI_COMPONENT_PLACER,
                        player.world, slot, hand.ordinal(), 0);
            });
            return null;
        }
    }

    public static final class ComponentPlacerCraftRequestMessage implements IMessage {
        private int windowId;
        private int amount;
        private boolean autoStart;

        public ComponentPlacerCraftRequestMessage() {
        }

        private ComponentPlacerCraftRequestMessage(int windowId, int amount, boolean autoStart) {
            this.windowId = windowId;
            this.amount = amount;
            this.autoStart = autoStart;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
            amount = buffer.readInt();
            autoStart = buffer.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeInt(amount);
            buffer.writeBoolean(autoStart);
        }
    }

    public static final class ComponentPlacerCraftRequestHandler
            implements IMessageHandler<ComponentPlacerCraftRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(ComponentPlacerCraftRequestMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.amount <= 0
                        || !(player.openContainer instanceof ComponentPlacerCraftAmountMenu)
                        || player.openContainer.windowId != message.windowId) return;
                cn.ae2bc.placer.ComponentPlacerCraftingService.request(player,
                        (ComponentPlacerCraftAmountMenu) player.openContainer,
                        message.amount, message.autoStart);
            });
            return null;
        }
    }

    public static final class ComponentPlacerActionMessage implements IMessage {
        private int windowId;
        private int action;
        private int value;
        public ComponentPlacerActionMessage() { }
        private ComponentPlacerActionMessage(int windowId, int action, int value) {
            this.windowId = windowId; this.action = action; this.value = value;
        }
        @Override public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt(); action = buffer.readUnsignedByte(); value = buffer.readInt();
        }
        @Override public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId); buffer.writeByte(action); buffer.writeInt(value);
        }
    }

    public static final class ComponentPlacerActionHandler
            implements IMessageHandler<ComponentPlacerActionMessage, IMessage> {
        @Override public IMessage onMessage(ComponentPlacerActionMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer instanceof ComponentPlacerMenu
                        && player.openContainer.windowId == message.windowId) {
                    ((ComponentPlacerMenu) player.openContainer).handleAction(message.action, message.value);
                }
            });
            return null;
        }
    }

    public static void send(PatternP2PTunnelMenu menu, boolean enabled, int interval, int amount) {
        CHANNEL.sendToServer(new SettingsMessage(menu.windowId, menu.getPos(), menu.getSide(), enabled,
                new PatternP2PUnitSettings(cn.ae2bc.logic.ReturnMode.UNBLOCKED, true,
                        interval, amount, cn.ae2bc.logic.RedstoneOutputMode.SINGLE_TRIGGER,
                        15, 2, 20), true, false));
    }

    public static void sendPattern(PatternP2PTunnelMenu menu, boolean enabled,
            PatternP2PUnitSettings settings, boolean syncInputSettings, boolean resetTask) {
        CHANNEL.sendToServer(new SettingsMessage(menu.windowId, menu.getPos(), menu.getSide(), enabled, settings,
                syncInputSettings, resetTask));
    }

    public static void sendTaskAllocationMode(PatternP2PTunnelMenu menu, TaskAllocationMode mode) {
        CHANNEL.sendToServer(new TaskAllocationModeMessage(menu.windowId, menu.getPos(),
                menu.getSide(), mode));
    }

    public static final class TaskAllocationModeMessage implements IMessage {
        private int windowId;
        private BlockPos pos;
        private EnumFacing side;
        private TaskAllocationMode mode;

        public TaskAllocationModeMessage() { }

        private TaskAllocationModeMessage(int windowId, BlockPos pos, EnumFacing side,
                TaskAllocationMode mode) {
            this.windowId = windowId;
            this.pos = pos;
            this.side = side;
            this.mode = mode == null ? TaskAllocationMode.ROUND_ROBIN : mode;
        }

        @Override public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
            pos = BlockPos.fromLong(buffer.readLong());
            side = EnumFacing.values()[buffer.readUnsignedByte() % EnumFacing.values().length];
            mode = TaskAllocationMode.fromId(buffer.readUnsignedByte());
        }

        @Override public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeLong(pos.toLong());
            buffer.writeByte(side.ordinal());
            buffer.writeByte(mode.getId());
        }
    }

    public static final class TaskAllocationModeHandler
            implements IMessageHandler<TaskAllocationModeMessage, IMessage> {
        @Override public IMessage onMessage(TaskAllocationModeMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof PatternP2PTunnelMenu)
                        || player.openContainer.windowId != message.windowId
                        || !((PatternP2PTunnelMenu) player.openContainer).getPos().equals(message.pos)
                        || ((PatternP2PTunnelMenu) player.openContainer).getSide() != message.side
                        || player.getDistanceSq(message.pos.getX() + 0.5, message.pos.getY() + 0.5,
                        message.pos.getZ() + 0.5) > 64.0) return;
                PatternP2PTunnelPart part = PatternP2PTunnelMenu.findPart(player, message.pos, message.side);
                if (part != null && !part.isOutput()) part.setTaskAllocationMode(message.mode);
            });
            return null;
        }
    }

    public static void sendMaterialOutputConfig(int windowId, long[] packed) {
        CHANNEL.sendToServer(new MaterialOutputConfigMessage(windowId, packed));
    }

    public static void sendUnitPortPriority(cn.ae2bc.menu.UnitPortOutputConfigMenu menu, int priority) {
        CHANNEL.sendToServer(new UnitPortPriorityMessage(menu.getPos(), menu.getSide(), priority,
                menu.singleSlot));
    }

    public static void sendUnitPortState(EntityPlayerMP player,
            cn.ae2bc.menu.UnitPortOutputConfigMenu menu) {
        CHANNEL.sendTo(new UnitPortStateMessage(menu.windowId, menu.getPos(), menu.getSide(),
                menu.priority, menu.singleSlot, menu.singleSlotEditable), player);
    }

    /** Pushes effective single-slot state to every player viewing a bound output port. */
    public static void refreshUnitPortStates(PatternP2PUnitManagerPart manager) {
        if (manager == null || manager.getTile() == null || manager.getTile().getWorld() == null
                || manager.getTile().getWorld().isRemote) return;
        for (net.minecraft.entity.player.EntityPlayer online
                : manager.getTile().getWorld().playerEntities) {
            if (!(online instanceof EntityPlayerMP)
                    || !(online.openContainer
                    instanceof cn.ae2bc.menu.UnitPortOutputConfigMenu)) continue;
            cn.ae2bc.menu.UnitPortOutputConfigMenu menu =
                    (cn.ae2bc.menu.UnitPortOutputConfigMenu) online.openContainer;
            cn.ae2bc.part.PatternP2PUnitPortPart port = menu.getPart();
            if (port != null && port.findManager() == manager) menu.refreshStateAndSync();
        }
    }

    public static final class UnitPortStateMessage implements IMessage {
        private int windowId;
        private BlockPos pos;
        private EnumFacing side;
        private int priority;
        private boolean singleSlot;
        private boolean singleSlotEditable;

        public UnitPortStateMessage() { }

        private UnitPortStateMessage(int windowId, BlockPos pos, EnumFacing side, int priority,
                boolean singleSlot, boolean singleSlotEditable) {
            this.windowId = windowId;
            this.pos = pos;
            this.side = side;
            this.priority = priority;
            this.singleSlot = singleSlot;
            this.singleSlotEditable = singleSlotEditable;
        }

        @Override public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
            pos = BlockPos.fromLong(buffer.readLong());
            side = EnumFacing.values()[buffer.readUnsignedByte() % EnumFacing.values().length];
            priority = buffer.readInt();
            singleSlot = buffer.readBoolean();
            singleSlotEditable = buffer.readBoolean();
        }

        @Override public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeLong(pos.toLong());
            buffer.writeByte(side.ordinal());
            buffer.writeInt(priority);
            buffer.writeBoolean(singleSlot);
            buffer.writeBoolean(singleSlotEditable);
        }
    }

    public static final class UnitPortStateHandler
            implements IMessageHandler<UnitPortStateMessage, IMessage> {
        @Override public IMessage onMessage(UnitPortStateMessage message, MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().player == null
                        || !(Minecraft.getMinecraft().player.openContainer
                        instanceof cn.ae2bc.menu.UnitPortOutputConfigMenu)) return;
                cn.ae2bc.menu.UnitPortOutputConfigMenu menu =
                        (cn.ae2bc.menu.UnitPortOutputConfigMenu)
                                Minecraft.getMinecraft().player.openContainer;
                if (menu.windowId != message.windowId || !menu.getPos().equals(message.pos)
                        || menu.getSide() != message.side) return;
                menu.priority = message.priority;
                menu.singleSlot = message.singleSlot;
                menu.singleSlotEditable = message.singleSlotEditable;
            });
            return null;
        }
    }

    public static final class UnitPortPriorityMessage implements IMessage {
        private BlockPos pos;
        private EnumFacing side;
        private int priority;
        private boolean singleSlot;

        public UnitPortPriorityMessage() { }

        private UnitPortPriorityMessage(BlockPos pos, EnumFacing side, int priority,
                boolean singleSlot) {
            this.pos = pos;
            this.side = side;
            this.priority = Math.max(-9999, Math.min(9999, priority));
            this.singleSlot = singleSlot;
        }

        @Override public void fromBytes(ByteBuf buffer) {
            pos = BlockPos.fromLong(buffer.readLong());
            side = EnumFacing.values()[buffer.readUnsignedByte() % EnumFacing.values().length];
            priority = buffer.readInt();
            singleSlot = buffer.readBoolean();
        }

        @Override public void toBytes(ByteBuf buffer) {
            buffer.writeLong(pos.toLong());
            buffer.writeByte(side.ordinal());
            buffer.writeInt(priority);
            buffer.writeBoolean(singleSlot);
        }
    }

    public static final class UnitPortPriorityHandler
            implements IMessageHandler<UnitPortPriorityMessage, IMessage> {
        @Override public IMessage onMessage(UnitPortPriorityMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof cn.ae2bc.menu.UnitPortOutputConfigMenu)) return;
                cn.ae2bc.menu.UnitPortOutputConfigMenu menu =
                        (cn.ae2bc.menu.UnitPortOutputConfigMenu) player.openContainer;
                if (!menu.getPos().equals(message.pos) || menu.getSide() != message.side
                        || player.getDistanceSq(message.pos.getX() + .5, message.pos.getY() + .5,
                        message.pos.getZ() + .5) > 64.0) return;
                cn.ae2bc.part.PatternP2PUnitPortPart part =
                        cn.ae2bc.menu.UnitPortOutputConfigMenu.findPart(player, message.pos, message.side);
                if (part != null && part.getPortType().acceptsTaskInput()) {
                    PatternP2PUnitManagerPart manager = part.findManager();
                    if (manager != null && manager.isSyncMainConfiguration()) {
                        manager.synchronizeFromInput();
                    }
                    part.setPriority(Math.max(-9999, Math.min(9999, message.priority)));
                    if (part.isSingleSlotEditable()) part.setSingleSlot(message.singleSlot);
                    menu.refreshStateAndSync(true);
                }
            });
            return null;
        }
    }

    public static void sendEnergy(PatternP2PTunnelEnergyMenu menu, boolean pullEnabled,
            EnergyDistributionMode mode) {
        CHANNEL.sendToServer(new EnergyMessage(menu.windowId, menu.getPos(), menu.getSide(), pullEnabled, mode));
    }

    public static void sendUnitManagerSettings(PatternP2PUnitManagerMenu menu, int frequency,
            PatternP2PUnitSettings settings) {
        CHANNEL.sendToServer(new UnitManagerMessage(menu.windowId, menu.getPos(), frequency, settings, true,
                EnergyDistributionMode.EVEN, false));
    }

    public static void sendUnitManagerSettings(PatternP2PUnitManagerMenu menu, int frequency,
            PatternP2PUnitSettings settings, boolean syncMainConfiguration,
            EnergyDistributionMode energyMode, boolean resetTask) {
        CHANNEL.sendToServer(new UnitManagerMessage(menu.windowId, menu.getPos(), frequency, settings,
                syncMainConfiguration, energyMode, resetTask));
    }

    public static final class UnitManagerMessage implements IMessage {
        private int windowId;
        private BlockPos pos;
        private int frequency;
        private PatternP2PUnitSettings settings;
        private boolean syncMainConfiguration;
        private EnergyDistributionMode energyMode;
        private boolean resetTask;
        public UnitManagerMessage() { }
        private UnitManagerMessage(int windowId, BlockPos pos, int frequency, PatternP2PUnitSettings settings,
                boolean syncMainConfiguration, EnergyDistributionMode energyMode, boolean resetTask) {
            this.windowId = windowId;
            this.pos = pos;
            this.frequency = cn.ae2bc.core.frequency.FrequencyLimits.clamp(frequency);
            this.settings = settings == null ? PatternP2PUnitSettings.DEFAULT : settings;
            this.syncMainConfiguration = syncMainConfiguration;
            this.energyMode = energyMode == null ? EnergyDistributionMode.EVEN : energyMode;
            this.resetTask = resetTask;
        }
        @Override public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
            pos = BlockPos.fromLong(buffer.readLong());
            frequency = cn.ae2bc.core.frequency.FrequencyLimits.clamp(buffer.readInt());
            settings = readSettings(buffer);
            syncMainConfiguration = buffer.readBoolean();
            energyMode = EnergyDistributionMode.fromId(buffer.readUnsignedByte());
            resetTask = buffer.readBoolean();
        }
        @Override public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeLong(pos.toLong());
            buffer.writeInt(frequency);
            writeSettings(buffer, settings);
            buffer.writeBoolean(syncMainConfiguration);
            buffer.writeByte(energyMode.getId());
            buffer.writeBoolean(resetTask);
        }
    }

    public static final class UnitManagerHandler implements IMessageHandler<UnitManagerMessage, IMessage> {
        @Override public IMessage onMessage(UnitManagerMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof PatternP2PUnitManagerMenu)
                        || player.openContainer.windowId != message.windowId
                        || !((PatternP2PUnitManagerMenu) player.openContainer).getPos().equals(message.pos)
                        || player.getDistanceSq(message.pos.getX() + 0.5, message.pos.getY() + 0.5,
                        message.pos.getZ() + 0.5) > 64.0) return;
                PatternP2PUnitManagerPart part = PatternP2PUnitManagerMenu.findPart(player, message.pos);
                if (part != null) {
                    part.setFrequency(message.frequency);
                    part.setSyncMainConfiguration(message.syncMainConfiguration);
                    part.setSettings(message.settings);
                    part.setEnergyDistributionMode(message.energyMode);
                    if (message.resetTask) part.resetTaskState();
                }
            });
            return null;
        }
    }

    public static final class EnergyMessage implements IMessage {
        private int windowId;
        private BlockPos pos;
        private EnumFacing side;
        private boolean pullEnabled;
        private EnergyDistributionMode mode;

        public EnergyMessage() { }
        private EnergyMessage(int windowId, BlockPos pos, EnumFacing side, boolean pullEnabled,
                EnergyDistributionMode mode) {
            this.windowId = windowId;
            this.pos = pos;
            this.side = side;
            this.pullEnabled = pullEnabled;
            this.mode = mode == null ? EnergyDistributionMode.EVEN : mode;
        }
        @Override public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
            pos = BlockPos.fromLong(buffer.readLong());
            side = EnumFacing.values()[buffer.readUnsignedByte() % EnumFacing.values().length];
            pullEnabled = buffer.readBoolean();
            mode = EnergyDistributionMode.fromId(buffer.readUnsignedByte());
        }
        @Override public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeLong(pos.toLong());
            buffer.writeByte(side.ordinal());
            buffer.writeBoolean(pullEnabled);
            buffer.writeByte(mode.getId());
        }
    }

    public static final class EnergyHandler implements IMessageHandler<EnergyMessage, IMessage> {
        @Override public IMessage onMessage(EnergyMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof PatternP2PTunnelEnergyMenu)
                        || player.openContainer.windowId != message.windowId
                        || !((PatternP2PTunnelEnergyMenu) player.openContainer).getPos().equals(message.pos)
                        || ((PatternP2PTunnelEnergyMenu) player.openContainer).getSide() != message.side
                        || player.getDistanceSq(message.pos.getX() + 0.5, message.pos.getY() + 0.5,
                        message.pos.getZ() + 0.5) > 64.0) return;
                PatternP2PTunnelEnergyPart part = PatternP2PTunnelEnergyMenu.findPart(
                        player, message.pos, message.side);
                if (part != null) part.setSettings(message.pullEnabled, message.mode);
            });
            return null;
        }
    }

    public static final class SettingsMessage implements IMessage {
        private int windowId;
        private BlockPos pos;
        private EnumFacing side;
        private boolean enabled;
        private PatternP2PUnitSettings settings;
        private boolean syncInputSettings;
        private boolean resetTask;

        public SettingsMessage() { }
        private SettingsMessage(int windowId, BlockPos pos, EnumFacing side, boolean enabled,
                PatternP2PUnitSettings settings, boolean syncInputSettings, boolean resetTask) {
            this.windowId = windowId;
            this.pos = pos; this.side = side; this.enabled = enabled;
            this.settings = settings == null ? PatternP2PUnitSettings.DEFAULT : settings;
            this.syncInputSettings = syncInputSettings;
            this.resetTask = resetTask;
        }
        @Override public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
            pos = BlockPos.fromLong(buffer.readLong());
            side = EnumFacing.values()[buffer.readUnsignedByte() % EnumFacing.values().length];
            enabled = buffer.readBoolean();
            settings = readSettings(buffer);
            syncInputSettings = buffer.readBoolean();
            resetTask = buffer.readBoolean();
        }
        @Override public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeLong(pos.toLong()); buffer.writeByte(side.ordinal()); buffer.writeBoolean(enabled);
            writeSettings(buffer, settings);
            buffer.writeBoolean(syncInputSettings);
            buffer.writeBoolean(resetTask);
        }
    }

    public static final class SettingsHandler implements IMessageHandler<SettingsMessage, IMessage> {
        @Override public IMessage onMessage(SettingsMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof PatternP2PTunnelMenu)
                        || player.openContainer.windowId != message.windowId
                        || !((PatternP2PTunnelMenu) player.openContainer).getPos().equals(message.pos)
                        || ((PatternP2PTunnelMenu) player.openContainer).getSide() != message.side
                        || player.getDistanceSq(message.pos.getX() + 0.5, message.pos.getY() + 0.5,
                        message.pos.getZ() + 0.5) > 64.0) return;
                PatternP2PTunnelPart part = PatternP2PTunnelMenu.findPart(player, message.pos, message.side);
                if (part != null) {
                    if (part.isOutput()) {
                        part.setOutputSettings(message.settings.getReturnMode(), message.syncInputSettings);
                        if (!message.syncInputSettings) {
                            part.setExtractionSettings(message.enabled,
                                    message.settings.getExtractionInterval(),
                                    message.settings.getExtractionAmount());
                        }
                    } else part.setInputSettings(message.enabled, message.settings);
                    if (message.resetTask) part.resetTaskState();
                }
            });
            return null;
        }
    }

    private static void writeSettings(ByteBuf buffer, PatternP2PUnitSettings settings) {
        buffer.writeByte(settings.getReturnMode().getId());
        buffer.writeBoolean(settings.isBreakRecovery());
        buffer.writeInt(settings.getExtractionInterval());
        buffer.writeInt(settings.getExtractionAmount());
        buffer.writeByte(settings.getRedstoneMode().getId());
        buffer.writeByte(settings.getRedstoneStrength());
        buffer.writeInt(settings.getPulseWidthTicks());
        buffer.writeInt(settings.getPulsePeriodTicks());
        buffer.writeByte(settings.getTransferPortOutputMode().getId());
        buffer.writeByte(settings.getOutputSlotSharingMode().getId());
        buffer.writeByte(settings.getEnergyDistributionMode().getId());
    }

    private static PatternP2PUnitSettings readSettings(ByteBuf buffer) {
        return new PatternP2PUnitSettings(
                cn.ae2bc.logic.ReturnMode.fromId(buffer.readUnsignedByte()), buffer.readBoolean(),
                buffer.readInt(), buffer.readInt(),
                cn.ae2bc.logic.RedstoneOutputMode.fromId(buffer.readUnsignedByte()),
                buffer.readUnsignedByte(), buffer.readInt(), buffer.readInt(),
                cn.ae2bc.core.unit.TransferPortOutputMode.fromId(buffer.readUnsignedByte()),
                cn.ae2bc.core.unit.OutputSlotSharingMode.fromId(buffer.readUnsignedByte()),
                cn.ae2bc.logic.EnergyDistributionMode.fromId(buffer.readUnsignedByte()));
    }

    public static final class MaterialOutputConfigMessage implements IMessage {
        private int windowId;
        private long[] packed;
        public MaterialOutputConfigMessage() { }
        private MaterialOutputConfigMessage(int windowId, long[] packed) {
            this.windowId = windowId;
            this.packed = cn.ae2bc.pattern.MaterialOutputConfigCodec.normalize(packed);
        }
        @Override public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
            int length = buffer.readUnsignedByte();
            if (length > cn.ae2bc.pattern.MaterialOutputConfigCodec.WORD_COUNT) {
                throw new IllegalArgumentException("Invalid material output config length: " + length);
            }
            packed = new long[length];
            for (int i = 0; i < length; i++) packed[i] = buffer.readLong();
        }
        @Override public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeByte(packed.length);
            for (long value : packed) buffer.writeLong(value);
        }
    }

    public static final class MaterialOutputConfigHandler
            implements IMessageHandler<MaterialOutputConfigMessage, IMessage> {
        @Override public IMessage onMessage(MaterialOutputConfigMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer == null || player.openContainer.windowId != message.windowId
                        || !(player.openContainer instanceof appeng.container.implementations.ContainerPatternTerm)) return;
                PatternEncodingTermMenuState.set(player.openContainer, message.packed);
            });
            return null;
        }
    }
}
