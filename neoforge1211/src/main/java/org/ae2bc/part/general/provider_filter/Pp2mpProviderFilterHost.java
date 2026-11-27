package org.ae2bc.part.general.provider_filter;

import appeng.parts.AEBasePart;
import net.minecraft.world.inventory.MenuType;
import org.ae2bc.core.screen.Route;
import org.ae2bc.part.Pp2mpKey;
import org.ae2bc.part.Pp2mpPart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPart;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/* 供应过滤菜单通过统一宿主访问本地配置、有效配置和导航信息。 */
public interface Pp2mpProviderFilterHost {
    AEBasePart getProviderFilterPart();

    Pp2mpProviderFilterData getProviderFilter();

    /* 菜单宿主固定，频率和单元 ID 表示当前编辑所处的绑定。 */
    default int getProviderFilterBinding() {
        AEBasePart part = this.getProviderFilterPart();
        short frequency = part instanceof Pp2mpPart tunnel ? tunnel.getFrequency() : 0;
        short unitId = part instanceof Pp2mpUnitPortPart port ? port.getUnitId()
                : part instanceof Pp2mpUnitManagerPart manager ? manager.getUnitId() : 0;
        return Pp2mpKey.key(frequency, unitId);
    }

    default Pp2mpProviderFilterData getEffectiveProviderFilter() {
        return this.getProviderFilter();
    }

    default boolean isProviderFilterSynced() {
        return false;
    }

    default @Nullable Consumer<Boolean> getProviderFilterSyncSetter() {
        return null;
    }

    default @Nullable MenuType<?> getProviderFilterParentMenu() {
        return null;
    }

    default @Nullable Route getProviderFilterParentRoute() {
        return Route.GENERAL;
    }
}
