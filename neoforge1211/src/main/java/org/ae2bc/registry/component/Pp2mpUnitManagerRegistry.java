package org.ae2bc.registry.component;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;

public class Pp2mpUnitManagerRegistry {
    public static final DataComponentType<Short> EXPORTED_PP2MP_UNIT_ID = DataComponentType
            .<Short>builder().persistent(Codec.SHORT)
            .networkSynchronized(ByteBufCodecs.SHORT)
            .build();

    public static void register() {
        ComponentRegistry.COMPONENTS.register("exported_pp2mp_unit_id", () -> Pp2mpUnitManagerRegistry.EXPORTED_PP2MP_UNIT_ID);
    }
}
