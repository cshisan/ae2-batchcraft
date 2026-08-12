package cn.ae2bc.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class PatternP2PTunnelSettingsSyncTest {
    @Test
    public void toggleLabelUsesTheNonShadowFontRendererPath() throws Exception {
        MethodNode drawButton = method(readClass("/cn/ae2bc/client/ToggleSwitch.class"), "drawButton");
        assertTrue(hasCall(drawButton, "net/minecraft/client/gui/FontRenderer", "drawString"));
        assertFalse(hasCall(drawButton, "net/minecraft/client/gui/FontRenderer", "drawStringWithShadow"));
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
    public void outputExtractionAlwaysResolvesTheCurrentInputSettings() throws Exception {
        ClassNode tunnel = readClass("/cn/ae2bc/part/PatternP2PTunnelPart.class");
        assertTrue(hasCallNamed(method(tunnel, "tickingRequest"),
                "getProductExtractionSettingsFromInput"));
        assertTrue(hasCallNamed(method(tunnel, "onTunnelNetworkChange"),
                "refreshExtractionEndpoints"));
    }

    @Test
    public void managerRefreshesItsMainConfigurationBeforeAcceptingATask() throws Exception {
        ClassNode manager = readClass("/cn/ae2bc/part/PatternP2PUnitManagerPart.class");
        assertTrue(hasCallNamed(method(manager, "canAcceptTask"), "synchronizeFromInput"));
        assertTrue(hasCallNamed(method(manager, "acceptInputs"), "synchronizeFromInput"));
        assertTrue(hasCallNamed(method(manager, "onPowerStatusChanged"), "synchronizeFromInput"));
        assertTrue(hasCallNamed(method(manager, "onChannelsChanged"), "synchronizeFromInput"));
        assertTrue(hasCallNamed(method(manager, "writeToNBT"), "writeSettings"));
    }

    @Test
    public void closingTheScreenDoesNotWriteItsClientSnapshotBack() throws Exception {
        MethodNode onClose = method(readClass("/cn/ae2bc/client/PatternP2PTunnelScreen.class"),
                "onGuiClosed");
        assertFalse(hasCallNamed(onClose, "sendCurrentSettings"));
    }

    @Test
    public void closingOtherSettingsScreensDoesNotWriteClientSnapshotsBack() throws Exception {
        MethodNode energyClose = method(readClass("/cn/ae2bc/client/PatternP2PTunnelEnergyScreen.class"),
                "onGuiClosed");
        MethodNode managerClose = method(readClass("/cn/ae2bc/client/PatternP2PUnitManagerScreen.class"),
                "onGuiClosed");
        assertFalse(hasCallNamed(energyClose, "sendCurrentSettings"));
        assertFalse(hasCallNamed(managerClose, "sendCurrentSettings"));
    }

    @Test
    public void outputReturnAndExtractionUseTheTaskReturnFilter() throws Exception {
        ClassNode tunnel = readClass("/cn/ae2bc/part/PatternP2PTunnelPart.class");
        assertTrue(hasCallNamed(method(tunnel, "insertItem"), "returnOutputProduct"));
        MethodNode extraction = method(tunnel, "extractFromAdjacent");
        assertTrue(hasCallNamed(extraction, "returnOutputProduct"));
        assertFalse(hasCallNamed(extraction, "returnToAdjacent"));
    }

    @Test
    public void settingsMessagesAreBoundToTheCurrentlyOpenContainer() throws Exception {
        assertTrue(hasFieldNamed(readClass(
                "/cn/ae2bc/network/ModNetwork$SettingsHandler.class"), "openContainer"));
        assertTrue(hasFieldNamed(readClass(
                "/cn/ae2bc/network/ModNetwork$EnergyHandler.class"), "openContainer"));
        assertTrue(hasFieldNamed(readClass(
                "/cn/ae2bc/network/ModNetwork$UnitManagerHandler.class"), "openContainer"));
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
}
