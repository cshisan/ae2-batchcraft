package cn.ae2bc.platform;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

public final class AnnihilationPlaneBreakStrategyTest {
    private static final String RESOURCE = "/cn/ae2bc/platform/AnnihilationPlaneBreakStrategy.class";

    @Test
    public void followsRv6AnnihilationPlaneExecutionPath() throws Exception {
        ClassNode node = readClass();
        assertTrue(hasCall(node, "appeng/util/Platform", "getBlockDrops"));
        assertTrue(hasCall(node, "appeng/hooks/TickHandler", "addCallable"));
        assertTrue(hasCall(node, "net/minecraft/world/WorldServer", "destroyBlock"));
        assertTrue(hasMethodCall(node, "extractAEPower"));
        assertFalse(hasOwner(node, "net/minecraftforge/common/ForgeHooks"));
    }

    private static ClassNode readClass() throws IOException {
        InputStream input = AnnihilationPlaneBreakStrategyTest.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new AssertionError("Missing strategy class " + RESOURCE);
        try {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        } finally {
            input.close();
        }
    }

    private static boolean hasCall(ClassNode node, String owner, String name) {
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (owner.equals(call.owner) && name.equals(call.name)) return true;
                }
            }
        }
        return false;
    }

    private static boolean hasMethodCall(ClassNode node, String name) {
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode
                        && name.equals(((MethodInsnNode) instruction).name)) return true;
            }
        }
        return false;
    }

    private static boolean hasOwner(ClassNode node, String owner) {
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode
                        && owner.equals(((MethodInsnNode) instruction).owner)) return true;
            }
        }
        return false;
    }
}
