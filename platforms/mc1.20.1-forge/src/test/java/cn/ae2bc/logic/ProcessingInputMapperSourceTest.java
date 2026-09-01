package cn.ae2bc.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Guards the 1.20.1 migration points that keep unit-port admission slot-aware. */
class ProcessingInputMapperSourceTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/cn/ae2bc/logic", file));
    }

    @Test
    void managerUsesStrictSlotMapping() throws Exception {
        String manager = read("PatternP2PUnitManagerLogic.java");
        assertTrue(manager.contains("ProcessingInputMapper.map"));
        assertTrue(manager.contains("new PendingMaterial(input.stack(), form, input.slot())"));
    }

    @Test
    void mapperUsesDenseInputValidation() throws Exception {
        String mapper = read("ProcessingInputMapper.java");
        assertTrue(mapper.contains("matchers[patternSlot].isValid"));
        assertTrue(mapper.contains("PatternInputSlotAllocator.allocate"));
    }
}
