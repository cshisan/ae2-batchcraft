package org.ae2bc.registry;

import com.mojang.datafixers.DSL;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.ioport.Ae2bcIoPortBlock;
import org.ae2bc.ioport.Ae2bcIoPortBlockEntity;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlock;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;

public final class BlockRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Ae2bcMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            Registries.BLOCK_ENTITY_TYPE, Ae2bcMod.MOD_ID
    );
    public static final DeferredBlock<Ae2bcIoPortBlock> IO_PORT = BLOCKS.register("io_port", Ae2bcIoPortBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Ae2bcIoPortBlockEntity>> IO_PORT_ENTITY =
            BLOCK_ENTITIES.register("io_port", () -> {
                BlockEntityType<Ae2bcIoPortBlockEntity> type = BlockEntityType.Builder.of(
                        Ae2bcIoPortBlockEntity::new, IO_PORT.get()).build(DSL.remainderType());
                IO_PORT.get().setBlockEntity(Ae2bcIoPortBlockEntity.class, type, null, null);
                return type;
            });
    public static final DeferredBlock<FakeCraftingConverterBlock> FAKE_CRAFTING_CONVERTER = BLOCKS.register(
            "fake_crafting_converter", FakeCraftingConverterBlock::new
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FakeCraftingConverterBlockEntity>> FAKE_CRAFTING_CONVERTER_ENTITY = BLOCK_ENTITIES.register(
            "fake_crafting_converter",
            () -> {
                BlockEntityType<FakeCraftingConverterBlockEntity> blockEntityType = BlockEntityType.Builder.of(
                        FakeCraftingConverterBlockEntity::new,
                        FAKE_CRAFTING_CONVERTER.get()
                ).build(DSL.remainderType());
                FAKE_CRAFTING_CONVERTER.get().setBlockEntity(
                        FakeCraftingConverterBlockEntity.class,
                        blockEntityType,
                        null,
                        null
                );
                return blockEntityType;
            }
    );

    private BlockRegistry() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
