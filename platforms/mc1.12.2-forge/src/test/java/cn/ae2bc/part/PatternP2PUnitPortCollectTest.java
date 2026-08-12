package cn.ae2bc.part;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class PatternP2PUnitPortCollectTest {
    @Test
    public void collectPortOnlyCollectsDroppedItems() throws Exception {
        ClassNode port = readClass("/cn/ae2bc/part/PatternP2PUnitPortPart.class");
        MethodNode collect = method(port, "collect");

        assertTrue(hasCallNamed(collect, "collectDroppedItems"));
        assertFalse(hasMethod(port, "collectSourceFluid"));
        assertFalse(hasMethod(port, "returnFluidToAdjacent"));
    }

    @Test
    public void collectTooltipDescribesDroppedItemsOnly() throws Exception {
        String language = readText("/assets/ae2_batchcraft/lang/en_us.lang");
        assertTrue(language.contains("tooltip.ae2_batchcraft.pattern_p2p.unit_port.collect="
                + "Collects dropped items."));
        assertFalse(language.contains("Collects dropped items or fluids."));
    }

    private static ClassNode readClass(String resource) throws IOException {
        InputStream input = PatternP2PUnitPortCollectTest.class.getResourceAsStream(resource);
        if (input == null) throw new AssertionError("Missing class " + resource);
        try {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        } finally {
            input.close();
        }
    }

    private static String readText(String resource) throws IOException {
        InputStream input = PatternP2PUnitPortCollectTest.class.getResourceAsStream(resource);
        if (input == null) throw new AssertionError("Missing resource " + resource);
        try {
            byte[] bytes = new byte[8192];
            StringBuilder result = new StringBuilder();
            int count;
            while ((count = input.read(bytes)) >= 0) {
                result.append(new String(bytes, 0, count, StandardCharsets.UTF_8));
            }
            return result.toString();
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

    private static boolean hasMethod(ClassNode node, String name) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name)) return true;
        }
        return false;
    }

    private static boolean hasCallNamed(MethodNode method, String name) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode
                    && name.equals(((MethodInsnNode) instruction).name)) return true;
        }
        return false;
    }
}
