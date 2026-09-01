package cn.ae2bc.integration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import appeng.api.parts.IPart;
import appeng.parts.p2p.P2PTunnelPart;
import appeng.util.Platform;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.logic.PatternP2PUnitIdentityColors;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** Adds AE2 BatchCraft details through AE2's optional TOP part adapter. */
public final class PatternP2PTooltipProvider {
    private static final String TOP_PROVIDER_INTERFACE =
            "appeng.integration.modules.theoneprobe.part.IPartProbInfoProvider";

    private PatternP2PTooltipProvider() {
    }

    public static int includeManagers(int nativeOutputs, P2PTunnelPart<?> tunnel) {
        if (!(tunnel instanceof PatternP2PTunnelPart) || tunnel.isOutput()
                || tunnel.getGridNode() == null || tunnel.getTile().getLevel() == null) {
            return nativeOutputs;
        }
        return nativeOutputs + PatternP2PTopologyGridService.countByFrequency(
                tunnel.getGridNode(), tunnel.getFrequency(),
                tunnel.getTile().getLevel().getGameTime());
    }

    public static void appendTopProvider(List<Object> providers) {
        if (providers == null) {
            return;
        }
        try {
            ClassLoader loader = PatternP2PTooltipProvider.class.getClassLoader();
            Class<?> providerInterface = Class.forName(TOP_PROVIDER_INTERFACE, false, loader);
            Object provider = Proxy.newProxyInstance(loader, new Class<?>[] { providerInterface },
                    new TopInvocationHandler());
            providers.add(provider);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to extend AE2's TOP part provider", exception);
        }
    }

    private static final class TopInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Exception {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, arguments);
            }
            if ("addProbeInfo".equals(method.getName()) && arguments != null
                    && arguments.length == 7 && arguments[0] instanceof IPart) {
                List<ITextComponent> lines = buildLines((IPart) arguments[0]);
                Method text = method.getParameterTypes()[2].getMethod("text", ITextComponent.class);
                for (ITextComponent line : lines) {
                    text.invoke(arguments[2], line);
                }
            }
            return null;
        }
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] arguments) {
        if ("toString".equals(method.getName())) {
            return "AE2 BatchCraft TOP Part Provider";
        }
        if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(method.getName())) {
            return arguments != null && arguments.length == 1 && proxy == arguments[0];
        }
        return null;
    }

    private static List<ITextComponent> buildLines(IPart part) {
        List<ITextComponent> lines = new ArrayList<ITextComponent>();
        if (part instanceof PatternP2PTunnelPart) {
            PatternP2PTunnelPart tunnel = (PatternP2PTunnelPart) part;
            if (!tunnel.isPowered()) {
                lines.add(frequencyLine("tooltip.ae2_batchcraft.p2p_frequency",
                        tunnel.getFrequency()));
            }
        } else if (part instanceof PatternP2PTunnelEnergyPart) {
            PatternP2PTunnelEnergyPart energy = (PatternP2PTunnelEnergyPart) part;
            lines.add(new TranslationTextComponent("tooltip.ae2_batchcraft.energy_input_mode",
                    new TranslationTextComponent(energy.isPullEnabled()
                            ? "gui.ae2_batchcraft.energy.mode.active"
                            : "gui.ae2_batchcraft.energy.mode.passive")));
            lines.add(new TranslationTextComponent("tooltip.ae2_batchcraft.energy_distribution_mode",
                    new TranslationTextComponent("gui.ae2_batchcraft.energy_distribution_mode."
                            + energy.getDistributionMode().getSerializedName())));
        } else if (part instanceof PatternP2PUnitManagerPart) {
            PatternP2PUnitManagerPart manager = (PatternP2PUnitManagerPart) part;
            lines.add(frequencyLine("tooltip.ae2_batchcraft.p2p_frequency", manager.getFrequency()));
            lines.add(frequencyLine("tooltip.ae2_batchcraft.unit_frequency",
                    PatternP2PUnitIdentityColors.encode(manager.getUnitId())));
            lines.add(taskStateLine(manager.isTaskActive()));
        } else if (part instanceof PatternP2PUnitPortPart) {
            PatternP2PUnitPortPart port = (PatternP2PUnitPortPart) part;
            lines.add(frequencyLine("tooltip.ae2_batchcraft.p2p_frequency",
                    port.getBoundFrequency()));
            if (port.getBoundManagerId() == null) {
                lines.add(new TranslationTextComponent("tooltip.ae2_batchcraft.unit_frequency",
                        new TranslationTextComponent("tooltip.ae2_batchcraft.unit.unbound")));
            } else {
                lines.add(frequencyLine("tooltip.ae2_batchcraft.unit_frequency",
                        PatternP2PUnitIdentityColors.encode(port.getBoundManagerId())));
            }
            lines.add(taskStateLine(port.isBoundUnitTaskActive()));
        }
        return lines;
    }

    private static ITextComponent frequencyLine(String key, short frequency) {
        return new TranslationTextComponent(key, Platform.p2p().toHexString(frequency));
    }

    private static ITextComponent taskStateLine(boolean active) {
        return new TranslationTextComponent("tooltip.ae2_batchcraft.task_state",
                new TranslationTextComponent(active
                        ? "tooltip.ae2_batchcraft.task_state.working"
                        : "tooltip.ae2_batchcraft.task_state.idle"));
    }

}
