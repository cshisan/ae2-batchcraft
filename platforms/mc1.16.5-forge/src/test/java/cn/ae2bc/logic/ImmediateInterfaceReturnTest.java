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

public final class ImmediateInterfaceReturnTest {
    @Test
    public void realInterfaceInsertionFlushesThroughAe2sOwnTicker() throws Exception {
        ClassNode flusher = readClass("/cn/ae2bc/logic/InterfaceReturnFlusher.class");
        MethodNode insert = method(flusher, "insert");
        MethodNode flush = method(flusher, "flush");

        assertTrue(hasCallNamed(insert, "findDuality"));
        assertTrue(hasCallNamed(insert, "isEmpty"));
        assertTrue(countCallsNamed(insert, "flush") >= 2);
        assertTrue(hasCall(flush, "appeng/helpers/DualityInterface", "tickingRequest"));
    }

    @Test
    public void blockedInterfaceReturnIsProbedAndWakesOutputsAfterProgress() throws Exception {
        ClassNode tunnel = readClass("/cn/ae2bc/part/PatternP2PTunnelPart.class");
        MethodNode returnToAdjacent = method(tunnel, "returnToAdjacent");
        MethodNode probe = method(tunnel, "probeBlockedReturn");

        assertTrue(hasCall(returnToAdjacent,
                "cn/ae2bc/logic/InterfaceReturnFlusher", "insert"));
        assertTrue(hasCallNamed(returnToAdjacent, "wakeSelf"));
        assertTrue(hasCallNamed(probe, "isDue"));
        assertTrue(hasCall(probe, "net/minecraftforge/items/ItemHandlerHelper", "insertItem"));
        assertTrue(hasCallNamed(probe, "wakeOutputs"));
    }

    private static ClassNode readClass(String resource) throws IOException {
        InputStream input = ImmediateInterfaceReturnTest.class.getResourceAsStream(resource);
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

    private static boolean hasCall(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) && name.equals(call.name)) return true;
            }
        }
        return false;
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
