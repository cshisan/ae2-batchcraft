package cn.ae2bc.platform;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adds the old AE2 crafting-machine interface at the cable-bus boundary. */
public final class Ae2IntegrationTransformer implements IClassTransformer {
    private static final String TARGET = "appeng.tile.networking.TileCableBus";
    private static final String ENCODED_PATTERN = "appeng.items.misc.ItemEncodedPattern";
    private static final String PATTERN_TERM_CONTAINER = "appeng.container.implementations.ContainerPatternTerm";
    private static final String PATTERN_TERM_GUI = "appeng.client.gui.implementations.GuiPatternTerm";
    private static final String WAILA_P2P =
            "appeng.integration.modules.waila.part.P2PStateWailaDataProvider";
    private static final String TOP_P2P =
            "appeng.integration.modules.theoneprobe.part.P2PStateInfoProvider";
    private static final String WAILA_PART_PROVIDER =
            "appeng.integration.modules.waila.PartWailaDataProvider";
    private static final String TOP_PART_PROVIDER =
            "appeng.integration.modules.theoneprobe.PartInfoProvider";
    private static final String CABLE_BUS_CONTAINER = "appeng.parts.CableBusContainer";
    private static final String MACHINE = "appeng/api/implementations/tiles/ICraftingMachine";
    private static final String BRIDGE = "cn/ae2bc/integration/CableBusCraftingBridge";
    private static final String PART_CABLE_CLASS = "appeng.parts.networking.PartCable";
    private static final String CABLE_RENDER_BRIDGE =
            "cn/ae2bc/integration/CableRenderStateBridge";
    private static final String CABLE_COLOR_BRIDGE =
            "cn/ae2bc/integration/CablePartColorBridge";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        if (ENCODED_PATTERN.equals(transformedName)) {
            return transformEncodedPattern(basicClass);
        }
        if (PATTERN_TERM_CONTAINER.equals(transformedName)) {
            return transformPatternTermContainer(basicClass);
        }
        if (PATTERN_TERM_GUI.equals(transformedName)) {
            return transformPatternTermGui(basicClass);
        }
        if (TOP_PART_PROVIDER.equals(transformedName)) {
            return transformTooltipProviderConstructor(basicClass, "appendTopProvider");
        }
        if (WAILA_PART_PROVIDER.equals(transformedName)) {
            return transformTooltipProviderConstructor(basicClass, "appendWailaProvider");
        }
        if (WAILA_P2P.equals(transformedName) || TOP_P2P.equals(transformedName)) {
            return transformP2PTooltip(basicClass);
        }
        if (CABLE_BUS_CONTAINER.equals(transformedName)) {
            return transformCableBusContainer(basicClass);
        }
        if (PART_CABLE_CLASS.equals(transformedName)) {
            return transformPartCable(basicClass);
        }
        if (!TARGET.equals(transformedName)) return basicClass;
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        if (!node.interfaces.contains(MACHINE)) {
            node.interfaces.add(MACHINE);
        }
        if (!hasMethod(node, "acceptsPlans", "()Z")) {
            node.methods.add(acceptsPlansMethod());
        }
        String descriptor = "(Lappeng/api/networking/crafting/ICraftingPatternDetails;"
                + "Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/util/EnumFacing;)Z";
        if (!hasMethod(node, "pushPattern", descriptor)) {
            node.methods.add(pushPatternMethod(descriptor));
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] transformCableBusContainer(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        String descriptor = "()Lappeng/client/render/cablebus/CableBusRenderState;";
        int managerPlacementGuards = addManagerPlacementGuard(node);
        int managerModelHooks = 0;
        for (MethodNode method : node.methods) {
            if (!"getRenderState".equals(method.name) || !descriptor.equals(method.desc)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction.getOpcode() == Opcodes.ARETURN) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CABLE_RENDER_BRIDGE,
                            "addManagerModel", "(Lappeng/client/render/cablebus/CableBusRenderState;"
                                    + "Lappeng/parts/CableBusContainer;)"
                                    + "Lappeng/client/render/cablebus/CableBusRenderState;", false));
                    method.instructions.insertBefore(instruction, hook);
                    managerModelHooks++;
                }
            }
        }
        if (managerModelHooks != 1 || managerPlacementGuards != 1) {
            throw new IllegalStateException("Unexpected AE2 CableBusContainer shape: managerModelHooks="
                    + managerModelHooks
                    + ", managerPlacementGuards=" + managerPlacementGuards);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] transformPartCable(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        int replacements = 0;
        for (MethodNode method : node.methods) {
            if (!"<init>".equals(method.name)
                    || !"(Lnet/minecraft/item/ItemStack;)V".equals(method.desc)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() != Opcodes.INVOKESTATIC
                        || !"appeng/api/util/AEColor".equals(call.owner)
                        || !"values".equals(call.name)
                        || !"()[Lappeng/api/util/AEColor;".equals(call.desc)) continue;

                AbstractInsnNode end = instruction;
                while (end != null && end.getOpcode() != Opcodes.AALOAD) end = end.getNext();
                if (end == null) {
                    throw new IllegalStateException("Unable to find PartCable color lookup end");
                }
                AbstractInsnNode after = end.getNext();
                AbstractInsnNode current = instruction;
                while (current != after) {
                    AbstractInsnNode next = current.getNext();
                    method.instructions.remove(current);
                    current = next;
                }
                InsnList replacement = new InsnList();
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
                replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CABLE_COLOR_BRIDGE,
                        "resolve", "(Lnet/minecraft/item/ItemStack;)"
                                + "Lappeng/api/util/AEColor;", false));
                method.instructions.insertBefore(after, replacement);
                replacements++;
                break;
            }
        }
        if (replacements != 1) {
            throw new IllegalStateException("Unexpected AE2 PartCable constructor shape: replacements="
                    + replacements);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static int addManagerPlacementGuard(ClassNode node) {
        String descriptor = "(Lnet/minecraft/item/ItemStack;Lappeng/api/util/AEPartLocation;)Z";
        for (MethodNode method : node.methods) {
            if (!"canAddPart".equals(method.name) || !descriptor.equals(method.desc)) continue;
            LabelNode allowed = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CABLE_RENDER_BRIDGE,
                    "shouldRejectPart", "(Lappeng/parts/CableBusContainer;"
                            + "Lnet/minecraft/item/ItemStack;)Z", false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ, allowed));
            guard.add(new InsnNode(Opcodes.ICONST_0));
            guard.add(new InsnNode(Opcodes.IRETURN));
            guard.add(allowed);
            guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(guard);
            return 1;
        }
        return 0;
    }

    private static byte[] transformP2PTooltip(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        String descriptor = "(Lappeng/parts/p2p/PartP2PTunnel;)I";
        for (MethodNode method : node.methods) {
            if (!"getOutputCount".equals(method.name) || !descriptor.equals(method.desc)) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction.getOpcode() != Opcodes.IRETURN) continue;
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "cn/ae2bc/integration/PatternP2PTooltipProvider", "includeManagers",
                        "(ILappeng/parts/p2p/PartP2PTunnel;)I", false));
                method.instructions.insertBefore(instruction, hook);
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] transformTooltipProviderConstructor(byte[] basicClass,
            String appendMethod) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        int hooks = 0;
        for (MethodNode method : node.methods) {
            if (!"<init>".equals(method.name) || !"()V".equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction.getOpcode() != Opcodes.RETURN) {
                    continue;
                }
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "providers",
                        "Ljava/util/List;"));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "cn/ae2bc/integration/PatternP2PTooltipProvider", appendMethod,
                        "(Ljava/util/List;)V", false));
                method.instructions.insertBefore(instruction, hook);
                hooks++;
            }
        }
        if (hooks != 1) {
            throw new IllegalStateException("Unexpected AE2 tooltip provider constructor shape for "
                    + node.name + ": hooks=" + hooks);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] transformPatternTermContainer(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        int prepareHooks = 0;
        int applyHooks = 0;
        int cleanupHooks = 0;
        for (MethodNode method : node.methods) {
            if (!"encode".equals(method.name) || !"()V".equals(method.desc)) continue;
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "cn/ae2bc/pattern/PatternEncodingState", "prepare",
                    "(Ljava/lang/Object;)V", false));
            method.instructions.insert(hook);
            prepareHooks++;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction.getOpcode() == Opcodes.RETURN) {
                    method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "cn/ae2bc/pattern/PatternEncodingState", "clear", "()V", false));
                    cleanupHooks++;
                    continue;
                }
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!"net/minecraft/item/ItemStack".equals(call.owner)
                        || !("setTagCompound".equals(call.name) || "func_77982_d".equals(call.name))
                        || !"(Lnet/minecraft/nbt/NBTTagCompound;)V".equals(call.desc)) continue;
                InsnList apply = new InsnList();
                apply.add(new VarInsnNode(Opcodes.ALOAD, 1));
                apply.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "cn/ae2bc/pattern/PatternEncodingState", "apply",
                        "(Lnet/minecraft/item/ItemStack;)V", false));
                method.instructions.insert(instruction, apply);
                applyHooks++;
            }
        }
        if (prepareHooks != 1 || applyHooks != 1 || cleanupHooks == 0) {
            throw new IllegalStateException("Unexpected AE2 ContainerPatternTerm#encode shape: prepare="
                    + prepareHooks + ", apply=" + applyHooks + ", cleanup=" + cleanupHooks);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] transformPatternTermGui(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        if (hasMethod(node, "mouseClicked", "(III)V")
                || hasMethod(node, "func_73864_a", "(III)V")
                || hasMethod(node, "drawSlot", "(Lnet/minecraft/inventory/Slot;)V")
                || hasMethod(node, "func_146977_a", "(Lnet/minecraft/inventory/Slot;)V")
                || hasMethod(node, "drawScreen", "(IIF)V")
                || hasMethod(node, "func_73863_a", "(IIF)V")) {
            throw new IllegalStateException("Unexpected AE2 GuiPatternTerm override shape");
        }
        node.methods.add(patternTermMouseClickedMethod(node, "mouseClicked"));
        node.methods.add(patternTermMouseClickedMethod(node, "func_73864_a"));
        node.methods.add(patternTermDrawSlotMethod(node, "drawSlot"));
        node.methods.add(patternTermDrawSlotMethod(node, "func_146977_a"));
        node.methods.add(patternTermDrawScreenMethod(node, "drawScreen"));
        node.methods.add(patternTermDrawScreenMethod(node, "func_73863_a"));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode patternTermMouseClickedMethod(ClassNode node, String methodName) {
        MethodNode method = new MethodNode(Opcodes.ACC_PROTECTED, methodName, "(III)V", null,
                new String[] { "java/io/IOException" });
        LabelNode vanilla = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, "getSlot",
                "(II)Lnet/minecraft/inventory/Slot;", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "cn/ae2bc/client/PatternEncodingTermScreenSupport", "handleMouseClicked",
                "(Lappeng/client/gui/implementations/GuiPatternTerm;Lnet/minecraft/inventory/Slot;I)Z", false));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(vanilla);
        method.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName,
                methodName, "(III)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private static MethodNode patternTermDrawSlotMethod(ClassNode node, String methodName) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, methodName,
                "(Lnet/minecraft/inventory/Slot;)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, methodName,
                "(Lnet/minecraft/inventory/Slot;)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "cn/ae2bc/client/PatternEncodingTermScreenSupport", "renderConfigMarker",
                "(Lappeng/client/gui/implementations/GuiPatternTerm;Lnet/minecraft/inventory/Slot;)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private static MethodNode patternTermDrawScreenMethod(ClassNode node, String methodName) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, methodName, "(IIF)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.FLOAD, 3));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName,
                methodName, "(IIF)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, "getSlot",
                "(II)Lnet/minecraft/inventory/Slot;", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "cn/ae2bc/client/PatternEncodingTermScreenSupport", "renderInputTooltip",
                "(Lappeng/client/gui/implementations/GuiPatternTerm;Lnet/minecraft/inventory/Slot;II)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private static byte[] transformEncodedPattern(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        String descriptor = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
                + "Ljava/util/List;Lnet/minecraft/client/util/ITooltipFlag;)V";
        int hooks = 0;
        for (MethodNode method : node.methods) {
            if (!"addCheckedInformation".equals(method.name) || !descriptor.equals(method.desc)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction.getOpcode() == Opcodes.RETURN) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 4));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "cn/ae2bc/client/EncodedPatternTooltipSupport", "append",
                            descriptor, false));
                    method.instructions.insertBefore(instruction, hook);
                    hooks++;
                }
            }
        }
        if (hooks == 0) {
            throw new IllegalStateException("Missing AE2 ItemEncodedPattern#addCheckedInformation hook");
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean hasMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) return true;
        }
        return false;
    }

    private static MethodNode acceptsPlansMethod() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "acceptsPlans", "()Z", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "acceptsPlans",
                "(Lappeng/tile/networking/TileCableBus;)Z", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    private static MethodNode pushPatternMethod(String descriptor) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "pushPattern", descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "pushPattern",
                "(Lappeng/tile/networking/TileCableBus;Lappeng/api/networking/crafting/ICraftingPatternDetails;"
                        + "Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/util/EnumFacing;)Z", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }
}
