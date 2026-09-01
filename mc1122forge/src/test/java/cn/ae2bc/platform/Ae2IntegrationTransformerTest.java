package cn.ae2bc.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class Ae2IntegrationTransformerTest {
    private static final String CLASS_NAME = "appeng.parts.CableBusContainer";
    private static final String CLASS_RESOURCE = "/appeng/parts/CableBusContainer.class";
    private static final String PART_CABLE_CLASS = "appeng.parts.networking.PartCable";
    private static final String PART_CABLE_RESOURCE = "/appeng/parts/networking/PartCable.class";

    @Test
    public void managerPlacementGuardHasAValidBranchFrame() throws Exception {
        byte[] original = readClassBytes(CLASS_RESOURCE);
        byte[] transformed = new Ae2IntegrationTransformer().transform(
                CLASS_NAME, CLASS_NAME, original);

        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        MethodNode canAddPart = findMethod(node, "canAddPart",
                "(Lnet/minecraft/item/ItemStack;Lappeng/api/util/AEPartLocation;)Z");

        JumpInsnNode guardJump = null;
        for (AbstractInsnNode instruction = canAddPart.instructions.getFirst();
                instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof JumpInsnNode && instruction.getOpcode() == Opcodes.IFEQ) {
                guardJump = (JumpInsnNode) instruction;
                break;
            }
        }
        assertNotNull("Missing manager placement guard", guardJump);

        AbstractInsnNode frame = guardJump.label.getNext();
        while (frame != null && !(frame instanceof FrameNode)) frame = frame.getNext();
        assertNotNull("Missing frame at manager placement guard branch target", frame);
        assertEquals(FrameNode.class, frame.getClass());
        assertEquals(Opcodes.F_SAME, ((FrameNode) frame).type);

        Class<?> verified = new VerificationClassLoader(getClass().getClassLoader())
                .define(CLASS_NAME, transformed);
        assertEquals(CLASS_NAME, verified.getName());
    }

    @Test
    public void partCableConstructorUsesCustomColorResolver() throws Exception {
        byte[] original = readClassBytes(PART_CABLE_RESOURCE);
        byte[] transformed = new Ae2IntegrationTransformer().transform(
                PART_CABLE_CLASS, PART_CABLE_CLASS, original);

        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        MethodNode constructor = findMethod(node, "<init>",
                "(Lnet/minecraft/item/ItemStack;)V");
        int resolverCalls = 0;
        for (AbstractInsnNode instruction = constructor.instructions.getFirst();
                instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() == Opcodes.INVOKESTATIC
                    && "cn/ae2bc/integration/CablePartColorBridge".equals(call.owner)
                    && "resolve".equals(call.name)) {
                resolverCalls++;
            }
        }
        assertEquals(1, resolverCalls);

        Class<?> verified = new VerificationClassLoader(getClass().getClassLoader())
                .define(PART_CABLE_CLASS, transformed);
        assertEquals(PART_CABLE_CLASS, verified.getName());
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        }
        throw new AssertionError("Missing method " + name + descriptor);
    }

    private static byte[] readClassBytes(String resource) throws IOException {
        InputStream input = Ae2IntegrationTransformerTest.class.getResourceAsStream(resource);
        assertNotNull("Missing AE2 test class " + resource, input);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static final class VerificationClassLoader extends ClassLoader {
        private VerificationClassLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> define(String name, byte[] bytecode) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }
}
