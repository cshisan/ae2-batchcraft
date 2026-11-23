package org.ae2bc.integration;

import appeng.api.integrations.igtooltip.*;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPart;
import appeng.integration.modules.igtooltip.parts.P2PStateDataProvider;
import appeng.parts.p2p.P2PTunnelPart;
import appeng.util.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlock;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakePart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPart;

@SuppressWarnings("UnstableApiUsage")
public final class Pp2mpTooltipProvider implements TooltipProvider {
    private static final ResourceLocation CONVERTER_FREQUENCY = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "fake_crafting_converter_frequency"
    );
    // Jade 按 ID 共用优先级，数据注册必须与提示分开，避免覆盖显示顺序。
    private static final ResourceLocation CONVERTER_FREQUENCY_DATA = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "fake_crafting_converter_frequency_data"
    );
    private static final String FREQUENCY = "ae2bcFrequency";
    private static final String UNIT_ID = "ae2bcUnitId";
    private static final String TASK_ACTIVE = "ae2bcTaskActive";

    /* 仅移除本模组输入端、输出端和资源接收端的原版链接提示数据。 */
    private static void provideTunnel(Player player, P2PTunnelPart<?> part, CompoundTag data) {
        data.putShort(FREQUENCY, part.getFrequency());
        data.remove(P2PStateDataProvider.TAG_P2P_STATE);
        data.remove(P2PStateDataProvider.TAG_P2P_OUTPUTS);
        if (part instanceof Pp2mpOutputPart output) {
            data.putBoolean(TASK_ACTIVE, output.getData().isTaskActive());
        }

        if (part.isOutput() && part.isPowered()) {
            IGrid grid = part.getMainNode().getGrid();
            Pp2mpInputPart input = grid == null || part.getFrequency() == 0
                    ? null : Pp2mpGridService.get(grid).findInput(part.getFrequency());
            data.remove(P2PStateDataProvider.TAG_P2P_FREQUENCY_NAME);
            Component name = input == null ? null : input.getCustomName();
            if (name != null) {
                data.putString(P2PStateDataProvider.TAG_P2P_FREQUENCY_NAME, name.getString());
            }
        }
    }

    private static void provideManager(Player player, Pp2mpUnitManagerPart part, CompoundTag data) {
        data.putShort(FREQUENCY, part.getFrequency());
        data.putShort(UNIT_ID, part.getUnitId());
        data.putBoolean(TASK_ACTIVE, part.getLogic().isTaskActive());
    }

    private static void providePort(Player player, Pp2mpUnitPortPart part, CompoundTag data) {
        data.putShort(FREQUENCY, part.getFrequency());
        data.putShort(UNIT_ID, part.getUnitId());

        boolean taskActive = false;
        IGrid grid = part.grid();
        if (grid != null) {
            Pp2mpUnitManagerPart manager = Pp2mpGridService.get(grid).findManager(
                    part.getFrequency(), part.getUnitId()
            );
            if (manager != null) {
                taskActive = part.isBoundTo(manager) && manager.getLogic().isTaskActive();
            }
        }
        data.putBoolean(TASK_ACTIVE, taskActive);
    }

    /* 补回随原版链接提示隐藏的频率，保留自定义名称和输出端任务状态。 */
    private static void buildTunnel(IPart part, TooltipContext context, TooltipBuilder tooltip) {
        CompoundTag data = context.serverData();
        Component frequency = Platform.p2p().toColoredHexString(data.getShort(FREQUENCY)).withStyle(ChatFormatting.BOLD);
        if (data.contains(P2PStateDataProvider.TAG_P2P_FREQUENCY_NAME, Tag.TAG_STRING)) {
            frequency = Component.literal(data.getString(P2PStateDataProvider.TAG_P2P_FREQUENCY_NAME))
                    .append(" (").append(frequency).append(")");
        }
        tooltip.addLine(Component.translatable("tooltip.ae2_batchcraft.frequency", frequency));
        if (data.contains(TASK_ACTIVE, Tag.TAG_BYTE)) {
            Pp2mpTooltipProvider.addTaskState(tooltip, data.getBoolean(TASK_ACTIVE));
        }
    }

    private static void buildUnit(IPart part, TooltipContext context, TooltipBuilder tooltip) {
        CompoundTag data = context.serverData();
        Pp2mpTooltipProvider.addFrequency(tooltip, "tooltip.ae2_batchcraft.frequency", data.getShort(FREQUENCY));
        short unitId = data.getShort(UNIT_ID);
        if (unitId == 0) {
            tooltip.addLine(Component.translatable("tooltip.ae2_batchcraft.unit_id",
                    Component.translatable("tooltip.ae2_batchcraft.unit.unbound")));
        } else {
            Pp2mpTooltipProvider.addFrequency(tooltip, "tooltip.ae2_batchcraft.unit_id", unitId);
        }
        Pp2mpTooltipProvider.addTaskState(tooltip, data.getBoolean(TASK_ACTIVE));
    }

    private static void addTaskState(TooltipBuilder tooltip, boolean active) {
        Component state = Component.translatable(active
                ? "tooltip.ae2_batchcraft.task_state.working" : "tooltip.ae2_batchcraft.task_state.idle");
        tooltip.addLine(Component.translatable("tooltip.ae2_batchcraft.task_state", state));
    }

    private static void addFrequency(TooltipBuilder tooltip, String key, short frequency) {
        tooltip.addLine(Component.translatable(key, Platform.p2p().toColoredHexString(frequency).withStyle(ChatFormatting.BOLD)));
    }

    /* 向提示模组提供部件和转换器的服务端状态。 */
    @Override
    public void registerCommon(CommonRegistration registration) {
        registration.addBlockEntityData(
                CONVERTER_FREQUENCY_DATA, FakeCraftingConverterBlockEntity.class,
                (player, converter, data) -> data.putShort(FREQUENCY, converter.getFrequency())
        );
        PartTooltips.addServerData(Pp2mpInputPart.class, Pp2mpTooltipProvider::provideTunnel, TooltipProvider.DEFAULT_PRIORITY + 1);
        PartTooltips.addServerData(Pp2mpOutputPart.class, Pp2mpTooltipProvider::provideTunnel, TooltipProvider.DEFAULT_PRIORITY + 1);
        PartTooltips.addServerData(Pp2mpResourceIntakePart.class, Pp2mpTooltipProvider::provideTunnel, TooltipProvider.DEFAULT_PRIORITY + 1);
        PartTooltips.addServerData(Pp2mpUnitManagerPart.class, Pp2mpTooltipProvider::provideManager);
        PartTooltips.addServerData(Pp2mpUnitPortPart.class, Pp2mpTooltipProvider::providePort);
    }

    /* 使用服务端快照统一显示部件和转换器的频率。 */
    @Override
    public void registerClient(ClientRegistration registration) {
        registration.addBlockEntityBody(
                FakeCraftingConverterBlockEntity.class, FakeCraftingConverterBlock.class, CONVERTER_FREQUENCY,
                (converter, context, tooltip) -> Pp2mpTooltipProvider.addFrequency(
                        tooltip, "tooltip.ae2_batchcraft.frequency", context.serverData().getShort(FREQUENCY)
                ), TooltipProvider.DEFAULT_PRIORITY + 1
        );
        PartTooltips.addBody(Pp2mpInputPart.class, Pp2mpTooltipProvider::buildTunnel);
        PartTooltips.addBody(Pp2mpOutputPart.class, Pp2mpTooltipProvider::buildTunnel);
        PartTooltips.addBody(Pp2mpResourceIntakePart.class, Pp2mpTooltipProvider::buildTunnel);
        PartTooltips.addBody(Pp2mpUnitManagerPart.class, Pp2mpTooltipProvider::buildUnit);
        PartTooltips.addBody(Pp2mpUnitPortPart.class, Pp2mpTooltipProvider::buildUnit);
    }
}
