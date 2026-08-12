package cn.ae2bc.part;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternP2PUnitPortCollectSourceTest {
    @Test
    void resetsCollectionStrategiesBeforeScanningEntities() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java"));
        int collectMethod = source.indexOf("private boolean collectDroppedItems");
        int reset = source.indexOf("strategy.reset();", collectMethod);
        int entityScan = source.indexOf("level.getEntitiesOfClass(ItemEntity.class", collectMethod);

        assertTrue(collectMethod >= 0 && reset > collectMethod && entityScan > reset,
                "collect strategies must be reset before each entity scan");
    }
}
