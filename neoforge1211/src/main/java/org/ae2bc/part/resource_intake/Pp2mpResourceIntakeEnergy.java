package org.ae2bc.part.resource_intake;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 将 Applied Flux 的 FE 资源转换为所在网络的运行能源，不持有资源库存。
 */
public final class Pp2mpResourceIntakeEnergy {
    private static final ResourceLocation FLUX_TYPE = ResourceLocation.fromNamespaceAndPath("appflux", "flux");
    private static final ResourceLocation FE_KEY = ResourceLocation.fromNamespaceAndPath("appflux", "fe");
    private static final long MAX_EXACT_AMOUNT = 1L << 53;

    private final Supplier<@Nullable IGrid> gridSupplier;

    public Pp2mpResourceIntakeEnergy(Supplier<@Nullable IGrid> gridSupplier) {
        this.gridSupplier = gridSupplier;
    }

    /* 仅识别具有已知 FE 换算语义的资源类型，不依赖附属模组的类加载。 */
    public boolean supports(AEKeyType type) {
        return Pp2mpResourceIntakeEnergy.FLUX_TYPE.equals(type.getId()) && this.gridSupplier.get() != null;
    }

    /* 供电不检查通电状态或频率；先模拟整份 FE，再按实际接收量结算。 */
    public long insert(AEKey key, long amount, Actionable mode) {
        if (amount <= 0 || !Pp2mpResourceIntakeEnergy.FLUX_TYPE.equals(key.getType().getId())
                || !Pp2mpResourceIntakeEnergy.FE_KEY.equals(key.getId())) {
            return 0;
        }
        IGrid grid = this.gridSupplier.get();
        if (grid == null) {
            return 0;
        }
        IEnergyService energy = grid.getEnergyService();
        long offered = Math.min(amount, Pp2mpResourceIntakeEnergy.MAX_EXACT_AMOUNT);
        double offeredPower = PowerUnit.FE.convertTo(PowerUnit.AE, offered);
        double overflow = energy.injectPower(offeredPower, Actionable.SIMULATE);
        long planned = (long) Math.clamp(Math.floor(
                PowerUnit.AE.convertTo(PowerUnit.FE, offeredPower - overflow)), 0, offered);
        if (mode == Actionable.SIMULATE || planned == 0) {
            return planned;
        }

        double power = PowerUnit.FE.convertTo(PowerUnit.AE, planned);
        double remaining = energy.injectPower(power, Actionable.MODULATE);
        double accepted = PowerUnit.AE.convertTo(PowerUnit.FE, power - remaining);
        // 若能源存储在模拟后改变容量，已消耗的不足 1 FE 也必须计费，避免重复供电。
        return (long) Math.clamp(Math.ceil(accepted), 0, planned);
    }
}
