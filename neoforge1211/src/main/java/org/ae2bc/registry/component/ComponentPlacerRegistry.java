package org.ae2bc.registry.component;

import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.ItemContainerContents;
import org.ae2bc.placer.ComponentPlacerPose;
import org.ae2bc.placer.ComponentPlacerSelection;

public class ComponentPlacerRegistry {
    public static final DataComponentType<ComponentPlacerSelection> SELECTION = DataComponentType
            .<ComponentPlacerSelection>builder().persistent(ComponentPlacerSelection.CODEC)
            .networkSynchronized(ComponentPlacerSelection.STREAM_CODEC)
            .build();
    public static final DataComponentType<ComponentPlacerPose> POSE = DataComponentType
            .<ComponentPlacerPose>builder().persistent(ComponentPlacerPose.CODEC)
            .networkSynchronized(ComponentPlacerPose.STREAM_CODEC)
            .build();
    /* 保留旧组件注册以读取工具本体；旧频率不再使用，重新保存时统一清除。 */
    public static final DataComponentType<Short> LEGACY_FREQUENCY = DataComponentType
            .<Short>builder().persistent(Codec.SHORT)
            .networkSynchronized(ByteBufCodecs.SHORT)
            .build();
    public static final DataComponentType<Short> LEGACY_UNIT_ID = DataComponentType
            .<Short>builder().persistent(Codec.SHORT)
            .networkSynchronized(ByteBufCodecs.SHORT)
            .build();
    public static final DataComponentType<Boolean> FREQUENCY_MODE = DataComponentType
            .<Boolean>builder().persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL)
            .build();
    public static final DataComponentType<GenericStack> CABLE = DataComponentType
            .<GenericStack>builder().persistent(GenericStack.CODEC)
            .networkSynchronized(GenericStack.STREAM_CODEC)
            .build();
    public static final DataComponentType<GenericStack> PART = DataComponentType
            .<GenericStack>builder().persistent(GenericStack.CODEC)
            .networkSynchronized(GenericStack.STREAM_CODEC)
            .build();
    public static final DataComponentType<ItemContainerContents> LEGACY_CABLE = DataComponentType
            .<ItemContainerContents>builder().persistent(ItemContainerContents.CODEC)
            .networkSynchronized(ItemContainerContents.STREAM_CODEC)
            .build();
    public static final DataComponentType<ItemContainerContents> LEGACY_PART = DataComponentType
            .<ItemContainerContents>builder().persistent(ItemContainerContents.CODEC)
            .networkSynchronized(ItemContainerContents.STREAM_CODEC)
            .build();

    public static void register() {
        ComponentRegistry.COMPONENTS.register("component_placer_selection", () -> ComponentPlacerRegistry.SELECTION);
        ComponentRegistry.COMPONENTS.register("component_placer_pose", () -> ComponentPlacerRegistry.POSE);
        ComponentRegistry.COMPONENTS.register("component_placer_frequency", () -> ComponentPlacerRegistry.LEGACY_FREQUENCY);
        ComponentRegistry.COMPONENTS.register("component_placer_unit_id", () -> ComponentPlacerRegistry.LEGACY_UNIT_ID);
        ComponentRegistry.COMPONENTS.register("component_placer_frequency_mode", () -> ComponentPlacerRegistry.FREQUENCY_MODE);
        ComponentRegistry.COMPONENTS.register("component_placer_cable_key", () -> ComponentPlacerRegistry.CABLE);
        ComponentRegistry.COMPONENTS.register("component_placer_part_key", () -> ComponentPlacerRegistry.PART);
        ComponentRegistry.COMPONENTS.register("component_placer_cable", () -> ComponentPlacerRegistry.LEGACY_CABLE);
        ComponentRegistry.COMPONENTS.register("component_placer_part", () -> ComponentPlacerRegistry.LEGACY_PART);
    }
}
