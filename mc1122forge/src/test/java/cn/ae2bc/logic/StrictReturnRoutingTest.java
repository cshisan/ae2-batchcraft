package cn.ae2bc.logic;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class StrictReturnRoutingTest {
    @Test
    public void outputCapabilityAndExtractionUseTheActiveBatchFilter() throws Exception {
        ClassNode tunnel = readClass("/cn/ae2bc/part/PatternP2PTunnelPart.class");

        assertTrue(hasCallNamed(method(tunnel, "returnOutputProduct"), "filter"));
        assertTrue(hasCallNamed(method(tunnel, "returnOutputProduct"), "getReturnMode"));
        assertTrue(hasCallNamed(method(tunnel, "extractFromAdjacent"), "returnOutputProduct"));
        assertTrue(hasCallNamed(method(tunnel, "insertItem"), "returnOutputProduct"));
    }

    @Test
    public void everyUnitReturnPathUsesTheManagersDynamicFilter() throws Exception {
        ClassNode manager = readClass("/cn/ae2bc/part/PatternP2PUnitManagerPart.class");
        ClassNode port = readClass("/cn/ae2bc/part/PatternP2PUnitPortPart.class");
        ClassNode breakStrategy = readClass("/cn/ae2bc/platform/AnnihilationPlaneBreakStrategy.class");

        assertTrue(hasCallNamed(method(manager, "returnProduct"), "getReturnMode"));
        assertTrue(hasCallNamed(method(manager, "returnProduct"), "containsSameItem"));
        assertTrue(countCallsNamed(method(port, "extractFromAdjacent"), "returnProduct") >= 2);
        assertTrue(countCallsNamed(method(port, "collectDroppedItems"), "returnProduct") >= 2);
        assertTrue(hasCallNamed(method(breakStrategy, "canReturnDrops"), "returnProduct"));
        assertTrue(hasCallNamed(method(breakStrategy, "breakBlockAndHandleDrops"), "returnProduct"));
    }

    private static ClassNode readClass(String resource) throws IOException {
        InputStream input = StrictReturnRoutingTest.class.getResourceAsStream(resource);
        if (input == null) throw new AssertionError("Missing class " + resource);
        try {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        } finally {
            input.close();
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name)) return method;
        }
        throw new AssertionError("Missing method " + name);
    }

    private static boolean hasCallNamed(MethodNode method, String name) {
        return countCallsNamed(method, name) > 0;
    }

    private static int countCallsNamed(MethodNode method, String name) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode
                    && name.equals(((MethodInsnNode) instruction).name)) count++;
        }
        return count;
    }
}
