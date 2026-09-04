package cn.ae2bc.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class PatternP2PTunnelSettingsSyncTest {
    @Test
    public void settingsScreensDoNotWriteClientSnapshotsWhenClosed() throws Exception {
        assertFalse(hasMethod(readClass("/cn/ae2bc/client/PatternP2PTunnelScreen.class"), "onClose"));
        assertFalse(hasMethod(readClass("/cn/ae2bc/client/PatternP2PTunnelEnergyScreen.class"), "onClose"));
        assertFalse(hasMethod(readClass("/cn/ae2bc/client/PatternP2PUnitManagerScreen.class"), "onClose"));
    }

    @Test
    public void outputReturnAndExtractionUseTheTaskReturnFilter() throws Exception {
        ClassNode tunnel = readClass("/cn/ae2bc/part/PatternP2PTunnelPart.class");
        MethodNode endpointInsert = method(readClass(
                "/cn/ae2bc/part/PatternP2PTunnelPart$EndpointHandler.class"), "insertItem");
        String accessor = firstCallStartingWith(endpointInsert,
                "cn/ae2bc/part/PatternP2PTunnelPart", "access$");
        assertTrue(accessor != null && hasCallNamed(method(tunnel, accessor), "returnOutputProduct"));
        MethodNode extraction = method(tunnel, "extractFromAdjacent");
        assertTrue(hasCallNamed(extraction, "returnOutputProduct"));
        assertFalse(hasCallNamed(extraction, "returnToAdjacent"));
    }

    @Test
    public void inputSettingsNotifyThePartHostAfterTheyAreSaved() throws Exception {
        MethodNode setter = method(readClass("/cn/ae2bc/part/PatternP2PTunnelPart.class"),
                "setInputSettings");
        assertTrue(hasCall(setter, "appeng/api/parts/IPartHost", "markForUpdate"));
        assertTrue(hasCallNamed(setter, "synchronizeUnitManagers"));
        assertTrue(hasCallNamed(setter, "wakeOutputs"));
    }

    @Test
    public void inputSlotSharingUsesTheAuthoritativeFullSettingsUpdate() throws Exception {
        ClassNode screen = readClass("/cn/ae2bc/client/PatternP2PTunnelScreen.class");
        assertFalse(hasCallNamed(screen, "sendInputOutputSlotSharingMode"));
        assertTrue(hasSettingsConstructorWithOutputSlotSharingMode(
                method(screen, "sendCurrentSettings")));
        assertTrue(hasCallNamed(screen, "sendPatternSettings"));

        ClassNode packet = readClass("/cn/ae2bc/network/ModNetwork$ExtractionSettingsPacket.class");
        assertTrue(hasCallNamed(packet, "setInputSettings"));

        MethodNode setter = method(readClass("/cn/ae2bc/part/PatternP2PTunnelPart.class"),
                "setOutputSlotSharingMode");
        assertTrue(hasCall(setter, "appeng/api/parts/IPartHost", "markForSave"));
        assertTrue(hasCallNamed(setter, "synchronizeUnitManagers"));
        assertTrue(hasCallNamed(setter, "wakeOutputs"));
    }

    @Test
    public void unitPortSlotToggleWaitsForAuthoritativeContainerState() throws Exception {
        MethodNode click = methodContainingCall(readClass(
                "/cn/ae2bc/client/UnitPortOutputConfigScreen.class"),
                "getSyncedSingleSlotEditable");
        assertTrue(hasCallNamed(click, "getSyncedSingleSlotEditable"));
        assertFalse(hasFieldWrite(click, "singleSlot"));
        assertTrue(hasCallNamed(readClass("/cn/ae2bc/client/UnitPortOutputConfigScreen.class"),
                "getSyncedSingleSlot"));
    }

    @Test
    public void extractionSettingsWakeEveryRuntimeConsumer() throws Exception {
        MethodNode setter = method(readClass("/cn/ae2bc/part/PatternP2PTunnelPart.class"),
                "setExtractionSettings");
        assertTrue(hasCallNamed(setter, "synchronizeUnitManagers"));
        assertTrue(hasCallNamed(setter, "wakeOutputs"));
    }

    @Test
    public void extractionSettingsAlertEveryOutputImmediately() throws Exception {
        ClassNode tunnel = readClass("/cn/ae2bc/part/PatternP2PTunnelPart.class");
        assertTrue(hasAlertableTickingRequest(method(tunnel, "getTickingRequest")));
        MethodNode wakeOutputs = method(tunnel, "wakeOutputs");
        assertTrue(hasCallNamed(wakeOutputs, "wake"));
        assertTrue(hasCallNamed(wakeOutputs, "alertDevice"));
        assertFalse(hasCallNamed(wakeOutputs, "wakeDevice"));
    }

    @Test
    public void settingsMessagesAreBoundToTheCurrentlyOpenContainer() throws Exception {
        assertTrue(hasFieldNamed(readClass(
                "/cn/ae2bc/network/ModNetwork$ExtractionSettingsPacket.class"), "containerMenu"));
        assertTrue(hasFieldNamed(readClass(
                "/cn/ae2bc/network/ModNetwork$EnergySettingsPacket.class"), "containerMenu"));
        assertTrue(hasFieldNamed(readClass(
                "/cn/ae2bc/network/ModNetwork$UnitManagerSettingsPacket.class"), "containerMenu"));
    }

    private static ClassNode readClass(String resource) throws IOException {
        InputStream input = PatternP2PTunnelSettingsSyncTest.class.getResourceAsStream(resource);
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

    private static boolean hasMethod(ClassNode node, String name) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name)) return true;
        }
        return false;
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
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode
                    && name.equals(((MethodInsnNode) instruction).name)) return true;
        }
        return false;
    }

    private static boolean hasCallNamed(ClassNode type, String name) {
        for (MethodNode method : type.methods) {
            if (hasCallNamed(method, name)) return true;
        }
        return false;
    }

    private static boolean hasSettingsConstructorWithOutputSlotSharingMode(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if ("cn/ae2bc/core/unit/PatternP2PUnitSettings".equals(call.owner)
                    && "<init>".equals(call.name)
                    && call.desc.contains("Lcn/ae2bc/core/unit/OutputSlotSharingMode;")) return true;
        }
        return false;
    }

    private static boolean hasAlertableTickingRequest(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!"appeng/api/networking/ticking/TickingRequest".equals(call.owner)
                    || !"<init>".equals(call.name)) continue;
            AbstractInsnNode canBeAlerted = previousInstruction(instruction);
            return canBeAlerted != null && canBeAlerted.getOpcode() == Opcodes.ICONST_1;
        }
        return false;
    }

    private static AbstractInsnNode previousInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    private static String firstCallStartingWith(MethodNode method, String owner, String prefix) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) && call.name.startsWith(prefix)) return call.name;
            }
        }
        return null;
    }

    private static boolean hasFieldNamed(ClassNode type, String name) {
        for (MethodNode method : type.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof FieldInsnNode
                        && name.equals(((FieldInsnNode) instruction).name)) return true;
            }
        }
        return false;
    }

    private static boolean hasFieldWrite(MethodNode method, String name) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode
                    && name.equals(((FieldInsnNode) instruction).name)
                    && instruction.getOpcode() == Opcodes.PUTFIELD) return true;
        }
        return false;
    }

    private static MethodNode methodContainingCall(ClassNode type, String callName) {
        for (MethodNode method : type.methods) {
            if (hasCallNamed(method, callName)) return method;
        }
        throw new AssertionError("Missing method containing call " + callName);
    }
}
