package org.ae2bc.part.general.provider_filter;

import appeng.parts.AEBasePart;
import net.minecraft.world.inventory.MenuType;
import org.ae2bc.core.screen.Route;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/* 供应过滤菜单通过统一宿主访问本地配置、有效配置和导航信息。 */
public interface Pp2mpProviderFilterHost {
    AEBasePart getProviderFilterPart();

    Pp2mpProviderFilterData getProviderFilter();

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
