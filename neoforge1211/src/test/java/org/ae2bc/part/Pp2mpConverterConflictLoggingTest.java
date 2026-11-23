package org.ae2bc.part;

import appeng.api.networking.IGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.registry.BlockRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class Pp2mpConverterConflictLoggingTest {
    private static FakeCraftingConverterBlockEntity converter(BlockPos pos) {
        FakeCraftingConverterBlockEntity converter = new FakeCraftingConverterBlockEntity(
                pos, BlockRegistry.FAKE_CRAFTING_CONVERTER.get().defaultBlockState());
        Level level = Mockito.mock(Level.class);
        Mockito.when(level.dimension()).thenReturn(Level.OVERWORLD);
        converter.setLevel(level);
        converter.setFrequency((short) 0xffff);
        return converter;
    }

    /* 记录真实日志事件，验证正式级别、定位信息以及拓扑刷新不会刷屏。 */
    @Test
    void logsConflictMembershipChangesAndRecoveryWithoutRepeatingUnchangedState() {
        IGrid grid = Mockito.mock(IGrid.class);
        Pp2mpGridService service = new Pp2mpGridService(grid);
        FakeCraftingConverterBlockEntity first = Pp2mpConverterConflictLoggingTest.converter(new BlockPos(1, 64, 2));
        FakeCraftingConverterBlockEntity second = Pp2mpConverterConflictLoggingTest.converter(new BlockPos(-3, 70, 4));
        FakeCraftingConverterBlockEntity third = Pp2mpConverterConflictLoggingTest.converter(new BlockPos(5, 80, -6));
        Logger logger = Assertions.assertInstanceOf(Logger.class, LogManager.getLogger(Ae2bcMod.LOGGER.getName()));
        org.apache.logging.log4j.Level previousLevel = logger.getLevel();
        List<LogEvent> events = new ArrayList<>();
        Appender appender = Mockito.mock(Appender.class);
        Mockito.when(appender.getName()).thenReturn("ae2bc-converter-conflict-test");
        Mockito.when(appender.isStarted()).thenReturn(true);
        Mockito.doAnswer(invocation -> {
            LogEvent event = invocation.getArgument(0, LogEvent.class);
            if (event.getMessage().getFormattedMessage().startsWith("Fake crafting converter frequency conflict")) {
                events.add(event.toImmutable());
            }
            return null;
        }).when(appender).append(Mockito.any(LogEvent.class));
        logger.addAppender(appender);
        logger.setLevel(org.apache.logging.log4j.Level.INFO);
        try {
            Mockito.when(grid.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of(first));
            service.rebuildIfNeeded();
            Assertions.assertTrue(events.isEmpty());

            Mockito.when(grid.getMachines(FakeCraftingConverterBlockEntity.class))
                    .thenReturn(new LinkedHashSet<>(List.of(first, second)));
            service.topologyChanged();
            service.onServerEndTick();
            Assertions.assertEquals(1, events.size());
            Assertions.assertEquals("WARN", events.getFirst().getLevel().name());
            String conflict = events.getFirst().getMessage().getFormattedMessage();
            Assertions.assertTrue(conflict.contains("frequency=65535"));
            Assertions.assertTrue(conflict.contains("minecraft:overworld [1, 64, 2] id=" + first.getConverterId()));
            Assertions.assertTrue(conflict.contains("minecraft:overworld [-3, 70, 4] id=" + second.getConverterId()));
            Assertions.assertTrue(conflict.contains("New fake crafting task dispatch is paused"));

            Mockito.when(grid.getMachines(FakeCraftingConverterBlockEntity.class))
                    .thenReturn(new LinkedHashSet<>(List.of(second, first)));
            for (int i = 0; i < 10; i++) {
                service.topologyChanged();
                Assertions.assertNull(service.findFakeCraftingConverter((short) 0xffff));
                service.onServerEndTick();
            }
            Assertions.assertEquals(1, events.size());

            Mockito.when(grid.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of(first, third));
            service.topologyChanged();
            service.rebuildIfNeeded();
            Assertions.assertEquals(2, events.size());
            Assertions.assertEquals("WARN", events.get(1).getLevel().name());
            Assertions.assertTrue(events.get(1).getMessage().getFormattedMessage().contains(third.getConverterId().toString()));

            Mockito.when(grid.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of(first));
            service.topologyChanged();
            service.rebuildIfNeeded();
            Assertions.assertEquals(3, events.size());
            Assertions.assertEquals("INFO", events.get(2).getLevel().name());
            Assertions.assertTrue(events.get(2).getMessage().getFormattedMessage()
                    .contains("remainingConverter=minecraft:overworld [1, 64, 2] id=" + first.getConverterId()));
            service.topologyChanged();
            service.rebuildIfNeeded();
            Assertions.assertEquals(3, events.size());

            Mockito.when(grid.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of(first, second));
            service.topologyChanged();
            service.rebuildIfNeeded();
            Assertions.assertEquals(4, events.size());
            Mockito.when(grid.getMachines(FakeCraftingConverterBlockEntity.class)).thenReturn(Set.of());
            service.topologyChanged();
            service.rebuildIfNeeded();
            Assertions.assertEquals(5, events.size());
            Assertions.assertEquals("INFO", events.get(4).getLevel().name());
            Assertions.assertTrue(events.get(4).getMessage().getFormattedMessage().contains("remainingConverter=none"));
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(previousLevel);
        }
    }
}
