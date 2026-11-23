package org.ae2bc.registry.component;

import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.pattern.MaterialOutputAttr;

public final class ComponentRegistry {
    public static final DataComponentType<MaterialOutputAttr> MATERIAL_OUTPUT_CONFIG = DataComponentType
            .<MaterialOutputAttr>builder().persistent(MaterialOutputAttr.CODEC)
            .networkSynchronized(MaterialOutputAttr.STREAM_CODEC)
            .build();
    public static final DataComponentType<Long> PATTERN_BATCH_COUNT = DataComponentType
            .<Long>builder().persistent(Codec.LONG)
            .networkSynchronized(ByteBufCodecs.VAR_LONG)
            .build();
    public static final DataComponentType<GenericStack> REAL_OUTPUT_MARKER = DataComponentType
            .<GenericStack>builder().persistent(GenericStack.CODEC)
            .networkSynchronized(GenericStack.STREAM_CODEC)
            .build();
    static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(
            Registries.DATA_COMPONENT_TYPE, Ae2bcMod.MOD_ID
    );

    private ComponentRegistry() {
    }

    /* 按统一注册器登记本模组全部数据组件。 */
    public static void register(IEventBus bus) {
        COMPONENTS.register("material_output_config", () -> MATERIAL_OUTPUT_CONFIG);
        COMPONENTS.register("pattern_batch_count", () -> PATTERN_BATCH_COUNT);
        COMPONENTS.register("real_output_marker", () -> REAL_OUTPUT_MARKER);
        Pp2mpUnitManagerRegistry.register();
        ComponentPlacerRegistry.register();
        COMPONENTS.register(bus);
    }
}
