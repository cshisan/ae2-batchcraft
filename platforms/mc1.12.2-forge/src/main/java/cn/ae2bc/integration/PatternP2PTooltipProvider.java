package cn.ae2bc.integration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import appeng.api.parts.IPart;
import appeng.parts.p2p.PartP2PTunnel;
import appeng.util.Platform;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.logic.PatternP2PUnitIdentityColors;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.translation.I18n;

/** Adds AE2 BatchCraft details through AE2's optional TOP and Waila adapters. */
public final class PatternP2PTooltipProvider {
    private static final String TOP_PROVIDER_INTERFACE =
            "appeng.integration.modules.theoneprobe.part.IPartProbInfoProvider";
    private static final String WAILA_PROVIDER_INTERFACE =
            "appeng.integration.modules.waila.part.IPartWailaDataProvider";
    private static final String TAG_KIND = "ae2bc_tooltip_kind";
    private static final String TAG_P2P_FREQUENCY = "ae2bc_p2p_frequency";
    private static final String TAG_UNIT_FREQUENCY = "ae2bc_unit_frequency";
    private static final String TAG_BOUND = "ae2bc_bound";
    private static final String TAG_TASK_ACTIVE = "ae2bc_task_active";
    private static final String TAG_POWERED = "ae2bc_powered";
    private static final String TAG_PULL_ENABLED = "ae2bc_pull_enabled";
    private static final String TAG_DISTRIBUTION_MODE = "ae2bc_distribution_mode";
    private static final int KIND_NONE = 0;
    private static final int KIND_TUNNEL = 1;
    private static final int KIND_ENERGY = 2;
    private static final int KIND_MANAGER = 3;
    private static final int KIND_PORT = 4;

    private PatternP2PTooltipProvider() {
    }

    public static int includeManagers(int nativeOutputs, PartP2PTunnel<?> tunnel) {
        if (!(tunnel instanceof PatternP2PTunnelPart) || tunnel.isOutput()
                || tunnel.getGridNode() == null || tunnel.getTile().getWorld() == null) {
            return nativeOutputs;
        }
        return nativeOutputs + PatternP2PTopologyGridService.countByFrequency(
                tunnel.getGridNode(), tunnel.getFrequency(),
                tunnel.getTile().getWorld().getTotalWorldTime());
    }

    public static void appendTopProvider(List<Object> providers) {
        appendProxyProvider(providers, TOP_PROVIDER_INTERFACE, new TopInvocationHandler());
    }

    public static void appendWailaProvider(List<Object> providers) {
        appendProxyProvider(providers, WAILA_PROVIDER_INTERFACE, new WailaInvocationHandler());
    }

    private static void appendProxyProvider(List<Object> providers, String interfaceName,
            InvocationHandler handler) {
        if (providers == null) {
            return;
        }
        try {
            ClassLoader loader = PatternP2PTooltipProvider.class.getClassLoader();
            Class<?> providerInterface = Class.forName(interfaceName, false, loader);
            providers.add(Proxy.newProxyInstance(loader, new Class<?>[] { providerInterface }, handler));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to extend AE2 tooltip provider " + interfaceName,
                    exception);
        }
    }

    private static final class TopInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Exception {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, arguments, "TOP");
            }
            if ("addProbeInfo".equals(method.getName()) && arguments != null
                    && arguments.length == 7 && arguments[0] instanceof IPart) {
                Method text = method.getParameterTypes()[2].getMethod("text", String.class);
                for (String line : buildLines((IPart) arguments[0])) {
                    text.invoke(arguments[2], line);
                }
            }
            return null;
        }
    }

    private static final class WailaInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Exception {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, arguments, "Waila");
            }
            if ("getNBTData".equals(name) && arguments != null && arguments.length == 6
                    && arguments[1] instanceof IPart && arguments[3] instanceof NBTTagCompound) {
                writeWailaData((IPart) arguments[1], (NBTTagCompound) arguments[3]);
                return arguments[3];
            }
            if ("getWailaBody".equals(name) && arguments != null && arguments.length == 4) {
                @SuppressWarnings("unchecked")
                List<String> lines = (List<String>) arguments[1];
                Method getNbtData = method.getParameterTypes()[2].getMethod("getNBTData");
                Object data = getNbtData.invoke(arguments[2]);
                if (data instanceof NBTTagCompound) {
                    lines.addAll(buildWailaLines((NBTTagCompound) data));
                }
                return lines;
            }
            if (("getWailaHead".equals(name) || "getWailaTail".equals(name))
                    && arguments != null && arguments.length == 4) {
                return arguments[1];
            }
            if ("getWailaStack".equals(name) && arguments != null && arguments.length == 3) {
                return arguments[2];
            }
            return null;
        }
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] arguments,
            String adapter) {
        if ("toString".equals(method.getName())) {
            return "AE2 BatchCraft " + adapter + " Part Provider";
        }
        if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(method.getName())) {
            return arguments != null && arguments.length == 1 && proxy == arguments[0];
        }
        return null;
    }

    private static List<String> buildLines(IPart part) {
        NBTTagCompound data = new NBTTagCompound();
        writeWailaData(part, data);
        return buildWailaLines(data);
    }

    private static void writeWailaData(IPart part, NBTTagCompound data) {
        if (part instanceof PatternP2PTunnelPart) {
            PatternP2PTunnelPart tunnel = (PatternP2PTunnelPart) part;
            data.setInteger(TAG_KIND, KIND_TUNNEL);
            data.setShort(TAG_P2P_FREQUENCY, tunnel.getFrequency());
            data.setBoolean(TAG_POWERED, tunnel.isPowered());
        } else if (part instanceof PatternP2PTunnelEnergyPart) {
            PatternP2PTunnelEnergyPart energy = (PatternP2PTunnelEnergyPart) part;
            data.setInteger(TAG_KIND, KIND_ENERGY);
            data.setBoolean(TAG_PULL_ENABLED, energy.isPullEnabled());
            data.setInteger(TAG_DISTRIBUTION_MODE, energy.getDistributionMode().getId());
        } else if (part instanceof PatternP2PUnitManagerPart) {
            PatternP2PUnitManagerPart manager = (PatternP2PUnitManagerPart) part;
            data.setInteger(TAG_KIND, KIND_MANAGER);
            data.setShort(TAG_P2P_FREQUENCY, manager.getFrequency());
            data.setShort(TAG_UNIT_FREQUENCY,
                    PatternP2PUnitIdentityColors.encode(manager.getUnitId()));
            data.setBoolean(TAG_TASK_ACTIVE, manager.isTaskActive());
        } else if (part instanceof PatternP2PUnitPortPart) {
            PatternP2PUnitPortPart port = (PatternP2PUnitPortPart) part;
            data.setInteger(TAG_KIND, KIND_PORT);
            data.setShort(TAG_P2P_FREQUENCY, port.getBoundFrequency());
            data.setBoolean(TAG_BOUND, port.getBoundManagerId() != null);
            data.setShort(TAG_UNIT_FREQUENCY,
                    PatternP2PUnitIdentityColors.encode(port.getBoundManagerId()));
            data.setBoolean(TAG_TASK_ACTIVE, port.isBoundUnitTaskActive());
        }
    }

    private static List<String> buildWailaLines(NBTTagCompound data) {
        List<String> lines = new ArrayList<String>();
        int kind = data.getInteger(TAG_KIND);
        if (kind == KIND_TUNNEL) {
            if (!data.getBoolean(TAG_POWERED)) {
                lines.add(frequencyLine("tooltip.ae2_batchcraft.p2p_frequency",
                        data.getShort(TAG_P2P_FREQUENCY)));
            }
        } else if (kind == KIND_ENERGY) {
            String inputMode = I18n.translateToLocal(data.getBoolean(TAG_PULL_ENABLED)
                    ? "gui.ae2_batchcraft.energy.mode.active"
                    : "gui.ae2_batchcraft.energy.mode.passive");
            lines.add(I18n.translateToLocalFormatted(
                    "tooltip.ae2_batchcraft.energy_input_mode", inputMode));
            String distribution = I18n.translateToLocal(
                    "gui.ae2_batchcraft.energy_distribution_mode."
                            + cn.ae2bc.logic.EnergyDistributionMode
                                    .fromId(data.getInteger(TAG_DISTRIBUTION_MODE))
                                    .getSerializedName());
            lines.add(I18n.translateToLocalFormatted(
                    "tooltip.ae2_batchcraft.energy_distribution_mode", distribution));
        } else if (kind == KIND_MANAGER) {
            lines.add(frequencyLine("tooltip.ae2_batchcraft.p2p_frequency",
                    data.getShort(TAG_P2P_FREQUENCY)));
            lines.add(frequencyLine("tooltip.ae2_batchcraft.unit_frequency",
                    data.getShort(TAG_UNIT_FREQUENCY)));
            lines.add(taskStateLine(data.getBoolean(TAG_TASK_ACTIVE)));
        } else if (kind == KIND_PORT) {
            lines.add(frequencyLine("tooltip.ae2_batchcraft.p2p_frequency",
                    data.getShort(TAG_P2P_FREQUENCY)));
            if (data.getBoolean(TAG_BOUND)) {
                lines.add(frequencyLine("tooltip.ae2_batchcraft.unit_frequency",
                        data.getShort(TAG_UNIT_FREQUENCY)));
            } else {
                lines.add(I18n.translateToLocalFormatted("tooltip.ae2_batchcraft.unit_frequency",
                        I18n.translateToLocal("tooltip.ae2_batchcraft.unit.unbound")));
            }
            lines.add(taskStateLine(data.getBoolean(TAG_TASK_ACTIVE)));
        }
        return lines;
    }

    private static String frequencyLine(String key, short frequency) {
        return I18n.translateToLocalFormatted(key, Platform.p2p().toHexString(frequency));
    }

    private static String taskStateLine(boolean active) {
        return I18n.translateToLocalFormatted("tooltip.ae2_batchcraft.task_state",
                I18n.translateToLocal(active
                        ? "tooltip.ae2_batchcraft.task_state.working"
                        : "tooltip.ae2_batchcraft.task_state.idle"));
    }

}
