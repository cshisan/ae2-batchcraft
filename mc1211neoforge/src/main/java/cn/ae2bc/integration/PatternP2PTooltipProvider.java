package cn.ae2bc.integration;

import appeng.api.integrations.igtooltip.ClientRegistration;
import appeng.api.integrations.igtooltip.CommonRegistration;
import appeng.api.integrations.igtooltip.PartTooltips;
import appeng.api.integrations.igtooltip.TooltipBuilder;
import appeng.api.integrations.igtooltip.TooltipContext;
import appeng.api.integrations.igtooltip.TooltipProvider;
import appeng.util.Platform;
import cn.ae2bc.logic.PatternP2PUnitIdentityColors;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Adds task and unit identity details through AE2's Jade/WTHIT/TOP abstraction. */
public final class PatternP2PTooltipProvider implements TooltipProvider {
    private static final String P2P_FREQUENCY = "ae2bcP2PFrequency";
    private static final String UNIT_FREQUENCY = "ae2bcUnitFrequency";
    private static final String TASK_ACTIVE = "ae2bcTaskActive";
    private static final String IS_INPUT = "ae2bcIsInput";
    // AE2's built-in P2P tooltip reads these fields. Include unit managers in
    // the output count so an input with only unit endpoints is shown as linked.
    private static final String AE2_P2P_STATE = "p2pState";
    private static final String AE2_P2P_OUTPUTS = "p2pOutputs";
    private static final byte AE2_P2P_INPUT_STATE = 2;

    @Override
    public void registerCommon(CommonRegistration registration) {
        PartTooltips.addServerData(PatternP2PTunnelPart.class,
                PatternP2PTooltipProvider::provideTunnelData,
                TooltipProvider.DEFAULT_PRIORITY + 1);
        PartTooltips.addServerData(PatternP2PUnitManagerPart.class,
                PatternP2PTooltipProvider::provideManagerData);
        PartTooltips.addServerData(PatternP2PUnitPortPart.class,
                PatternP2PTooltipProvider::providePortData);
    }

    @Override
    public void registerClient(ClientRegistration registration) {
        PartTooltips.addBody(PatternP2PTunnelPart.class,
                PatternP2PTooltipProvider::buildTunnelTooltip);
        PartTooltips.addBody(PatternP2PUnitManagerPart.class,
                PatternP2PTooltipProvider::buildUnitTooltip);
        PartTooltips.addBody(PatternP2PUnitPortPart.class,
                PatternP2PTooltipProvider::buildUnitTooltip);
    }

    private static void provideTunnelData(Player player, PatternP2PTunnelPart part,
                                          CompoundTag serverData) {
        serverData.putShort(P2P_FREQUENCY, part.getFrequency());
        serverData.putBoolean(IS_INPUT, !part.isOutput());
        if (!part.isOutput()) {
            includeUnitManagersInP2PState(serverData, getUnitManagerCount(part));
        }
        if (part.isOutput()) {
            serverData.putBoolean(TASK_ACTIVE, part.isTaskActive());
        }
    }

    private static void includeUnitManagersInP2PState(CompoundTag serverData, int unitManagerCount) {
        if (unitManagerCount <= 0 || !serverData.contains(AE2_P2P_STATE, Tag.TAG_BYTE)) {
            return;
        }
        // P2PStateDataProvider is registered before this provider and consumes
        // these fields when the client builds the tooltip.
        serverData.putByte(AE2_P2P_STATE, AE2_P2P_INPUT_STATE);
        serverData.putInt(AE2_P2P_OUTPUTS,
                serverData.getInt(AE2_P2P_OUTPUTS) + unitManagerCount);
    }

    private static int getUnitManagerCount(PatternP2PTunnelPart input) {
        var grid = input.getMainNode().getGrid();
        if (!input.hasConfiguredFrequency() || grid == null) {
            return 0;
        }
        short frequency = input.getFrequency();
        return (int) grid.getMachines(PatternP2PUnitManagerPart.class).stream()
                .filter(manager -> manager.getFrequency() == frequency)
                .count();
    }

    private static void provideManagerData(Player player, PatternP2PUnitManagerPart manager,
                                           CompoundTag serverData) {
        serverData.putShort(P2P_FREQUENCY, manager.getFrequency());
        serverData.putShort(UNIT_FREQUENCY,
                PatternP2PUnitIdentityColors.encode(manager.getPatternP2PUnitId()));
        serverData.putBoolean(TASK_ACTIVE, manager.isTaskActive());
    }

    private static void providePortData(Player player, PatternP2PUnitPortPart port,
                                        CompoundTag serverData) {
        serverData.putShort(P2P_FREQUENCY, port.getBoundFrequency());
        serverData.putShort(UNIT_FREQUENCY,
                PatternP2PUnitIdentityColors.encode(port.getBoundPatternP2PUnitId()));
        serverData.putBoolean(TASK_ACTIVE, port.isBoundUnitTaskActive());
    }

    private static void buildTunnelTooltip(PatternP2PTunnelPart part, TooltipContext context,
                                           TooltipBuilder tooltip) {
        CompoundTag data = context.serverData();
        // AE2's standard P2P provider shows this while powered; retain the frequency while offline too.
        if (!data.contains("p2pFrequency", Tag.TAG_SHORT)) {
            addFrequency(tooltip, "tooltip.ae2_batchcraft.p2p_frequency",
                    data.getShort(P2P_FREQUENCY));
        }
        if (!data.getBoolean(IS_INPUT)) {
            addTaskState(tooltip, data.getBoolean(TASK_ACTIVE));
        }
    }

    private static void buildUnitTooltip(Object part, TooltipContext context, TooltipBuilder tooltip) {
        CompoundTag data = context.serverData();
        addFrequency(tooltip, "tooltip.ae2_batchcraft.p2p_frequency", data.getShort(P2P_FREQUENCY));
        short unitFrequency = data.getShort(UNIT_FREQUENCY);
        if (unitFrequency == 0) {
            tooltip.addLine(Component.translatable("tooltip.ae2_batchcraft.unit_frequency",
                    Component.translatable("tooltip.ae2_batchcraft.unit.unbound")));
        } else {
            addFrequency(tooltip, "tooltip.ae2_batchcraft.unit_frequency", unitFrequency);
        }
        addTaskState(tooltip, data.getBoolean(TASK_ACTIVE));
    }

    private static void addTaskState(TooltipBuilder tooltip, boolean active) {
        Component state = Component.translatable(active
                ? "tooltip.ae2_batchcraft.task_state.working"
                : "tooltip.ae2_batchcraft.task_state.idle");
        tooltip.addLine(Component.translatable("tooltip.ae2_batchcraft.task_state", state));
    }

    private static void addFrequency(TooltipBuilder tooltip, String translationKey, short frequency) {
        tooltip.addLine(Component.translatable(translationKey,
                Platform.p2p().toColoredHexString(frequency).withStyle(ChatFormatting.BOLD)));
    }
}
