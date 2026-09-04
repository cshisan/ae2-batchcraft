package cn.ae2bc.part;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public final class UnitPortRuntimeStateSourceTest {
    @Test
    public void taskLifecycleInvalidatesPortsWithoutResettingOnConfigurationChanges() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");

        String apply = section(manager, "public void applyMainConfiguration", "public void resetTaskState");
        assertTrue(apply.contains("wakeBoundPorts();"));
        assertFalse(apply.contains("invalidateBoundPortRuntimeState();"));

        String reset = section(manager, "public void resetTaskState", "public EnergyDistributionMode");
        assertTrue(reset.contains("invalidateBoundPortRuntimeState();"));

        String finish = section(manager, "private boolean finishTaskIfComplete", "private static boolean sameItem");
        assertTrue(finish.contains("invalidateBoundPortRuntimeState();"));
    }

    @Test
    public void reloadForcesRedstoneNeighborNotificationAndMigratesLegacyMainConfiguration() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String port = read("PatternP2PUnitPortPart.java");
        String tunnel = read("PatternP2PTunnelPart.java");

        assertTrue(manager.contains("PatternP2PUnitMainConfiguration"));
        assertTrue(manager.contains("PatternP2PUnitMainConfigurationRevision"));
        assertTrue(manager.contains("data.contains(MAIN_CONFIGURATION, 10)"));
        assertTrue(manager.contains("applyLocalSettings(readSettings(data.getCompound(MAIN_CONFIGURATION)"));
        assertTrue(manager.contains("data.remove(MAIN_CONFIGURATION);"));
        assertTrue(manager.contains("data.putLong(MAIN_CONFIGURATION_REVISION, lastAppliedMainConfigurationRevision)"));
        assertFalse(manager.contains("PatternP2PUnitSettings mainConfiguration"));
        assertTrue(manager.contains("data.contains(\"PatternP2PUnitOutputSlotSharingMode\")"));
        assertTrue(manager.contains("data.putInt(\"PatternP2PUnitOutputSlotSharingMode\""));
        assertTrue(tunnel.contains("Ae2bcUnitConfigurationRevision"));
        assertTrue(tunnel.contains("refreshConfigurationConsumers();"));
        assertTrue(port.contains("private boolean redstoneWorldStateDirty = true;"));
        assertTrue(port.contains("redstonePower == next && !redstoneWorldStateDirty"));
        assertTrue(port.contains("public void invalidateTaskRuntimeState()"));
        assertTrue(port.contains("setRedstonePower(0);"));
    }

    @Test
    public void networkRecoveryWakesPortsEvenWhenConfigurationIsUnchanged() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String port = read("PatternP2PUnitPortPart.java");

        String managerAdd = section(manager, "@Override public void addToWorld()",
                "@Override public void removeFromWorld()");
        assertTrue(managerAdd.indexOf("synchronizeFromInput();")
                < managerAdd.indexOf("wakeBoundPorts();"));

        String managerGrid = section(manager, "@Override public void gridChanged()",
                "@MENetworkEventSubscribe");
        assertTrue(managerGrid.contains("wakeBoundPorts();"));

        String managerPower = section(manager, "public void onPowerStatusChanged",
                "@MENetworkEventSubscribe");
        assertTrue(managerPower.contains("wakeBoundPorts();"));

        String managerChannels = section(manager, "public void onChannelsChanged",
                "private void refreshModelState");
        assertTrue(managerChannels.contains("wakeBoundPorts();"));

        String portGrid = section(port, "public void gridChanged()", "@MENetworkEventSubscribe");
        assertTrue(portGrid.contains("cachedManager = null;"));
        assertTrue(portGrid.contains("redstoneWorldStateDirty = true;"));
        assertTrue(portGrid.contains("alertTicking();"));

        String portPower = section(port, "public void onPowerStatusChanged",
                "private void refreshModelState");
        assertTrue(portPower.contains("cachedManager = null;"));
        assertTrue(portPower.contains("redstoneWorldStateDirty = true;"));
        assertTrue(portPower.contains("alertTicking();"));
    }

    @Test
    public void taskAdmissionRefreshesReturnConfigurationBeforeCommittingInputs() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");

        String admission = section(manager, "public boolean canAcceptTask()", "public boolean isTaskActive()");
        assertTrue(admission.contains("synchronizeFromInput();"));

        String accept = section(manager, "public boolean acceptInputs", "private PatternP2PUnitPortPart findInputPort");
        int finalRefresh = accept.lastIndexOf("if (!canAcceptTask()) return false;");
        int forcedRefresh = accept.indexOf("synchronizeFromInput(true);", finalRefresh);
        int commit = accept.indexOf("pendingInputs.clear();");
        assertTrue("Configuration must be refreshed immediately before task state is committed",
                finalRefresh >= 0 && finalRefresh < commit);
        assertTrue("The final refresh must bypass a same-tick empty input cache",
                forcedRefresh > finalRefresh && forcedRefresh < commit);

        String apply = section(manager, "public void applyMainConfiguration", "public void resetTaskState");
        assertTrue(apply.contains("settings == null || !syncMainConfiguration"));
        assertTrue(apply.contains("sameSettings(getLocalSettings(), settings)"));
        assertTrue(apply.contains("lastAppliedMainConfigurationRevision == revision"));
        assertTrue(apply.contains("applyLocalSettings(settings);"));
    }

    @Test
    public void networkLifecycleInvalidatesInputCacheAndResynchronizesConfiguration() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String tunnel = read("PatternP2PTunnelPart.java");

        String tunnelAdd = section(tunnel, "public void addToWorld()", "public void removeFromWorld()");
        assertTrue(tunnelAdd.indexOf("super.addToWorld();")
                < tunnelAdd.indexOf("refreshConfigurationConsumers();"));
        assertTrue(tunnelAdd.contains("PatternP2PTopologyGridService.invalidate(getGridNode());"));

        String managerAdd = section(manager, "@Override public void addToWorld()",
                "@Override public void removeFromWorld()");
        assertTrue(managerAdd.indexOf("super.addToWorld();")
                < managerAdd.indexOf("synchronizeFromInput();"));
        assertTrue(managerAdd.contains("invalidateInputCache();"));

        String grid = section(manager, "@Override public void gridChanged()", "@MENetworkEventSubscribe");
        assertTrue(grid.contains("invalidateInputCache();"));
        assertTrue(grid.contains("synchronizeFromInput();"));

        String power = section(manager, "public void onPowerStatusChanged", "@MENetworkEventSubscribe");
        assertTrue(power.contains("invalidateInputCache();"));
        assertTrue(power.contains("synchronizeFromInput();"));

        String channels = section(manager, "public void onChannelsChanged", "private void refreshModelState");
        assertTrue(channels.contains("invalidateInputCache();"));
        assertTrue(channels.contains("synchronizeFromInput();"));
    }

    @Test
    public void configurationLookupIsStructuralButProductReturnRequiresAnActiveInput() throws Exception {
        String topology = readLogic("PatternP2PTopologyGridService.java");
        String manager = read("PatternP2PUnitManagerPart.java");

        String lookup = section(topology, "public static PatternP2PTunnelPart findInput",
                "public static int countByFrequency");
        assertTrue(lookup.contains("snapshot(ownNode.getGrid()).inputs.get(frequency)"));
        assertFalse("Configuration lookup must match 1.21.1 and not require a channel",
                lookup.contains("isActive()"));

        String returning = section(manager, "public ItemStack returnProduct", "private PatternP2PTunnelPart findInput");
        assertTrue(returning.contains("findOperationalInput();"));

        String operational = section(manager, "private PatternP2PTunnelPart findOperationalInput",
                "private void invalidateInputCache");
        assertTrue("Product transfer must still reject an offline input",
                operational.contains("input.getGridNode().isActive()"));
    }

    @Test
    public void singleSlotDispatchKeepsEncodedSlotIdentityAcrossQueueMutationAndReload() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String tunnel = read("PatternP2PTunnelPart.java");

        String plan = section(tunnel, "private ManagerDispatch createManagerDispatch",
                "private boolean tryAcceptManager");
        assertTrue(plan.contains("List<Integer> inputSlots"));
        assertTrue(plan.contains("inputSlots.add(slot);"));
        assertTrue(plan.contains("new ManagerDispatch(inputs, targetTypes, inputSlots"));
        assertTrue(tunnel.contains("dispatch.targetTypes, dispatch.inputSlots"));

        assertTrue(manager.contains("private final List<Integer> pendingInputSlots"));
        String allocation = section(manager, "private boolean canAcceptInputsAggregate",
                "private boolean dispatchPending");
        assertTrue(allocation.contains("int slot = inputSlots.get(index);"));
        assertTrue(allocation.contains("canUsePortForSlot(slot"));
        assertTrue(allocation.contains("assignedSlotPorts.putIfAbsent(slot, port);"));
        assertTrue("A claimed single-slot material must not be split over another port",
                allocation.contains("Once claimed") && allocation.contains("break;"));

        String dispatch = section(manager, "private boolean dispatchPending",
                "private boolean canUsePortForSlot");
        assertTrue(dispatch.contains("int slot = pendingInputSlots.get(index);"));
        assertTrue(dispatch.contains("restorePersistedSlotPort(slot, candidates);"));
        assertTrue(dispatch.contains("canUsePortForSlot("));
        assertTrue(dispatch.contains("pendingInputSlots.remove(index);"));
        assertFalse(dispatch.contains("pendingInputSlots.get(iterator.nextIndex())"));

        String load = section(manager, "@Override public void readFromNBT",
                "@Override public void writeToNBT");
        assertTrue(load.contains("entry.contains(\"PatternSlot\")"));
        assertTrue(load.contains(": i);"));
        String save = section(manager, "@Override public void writeToNBT",
                "private static PatternP2PUnitSettings readSettings");
        assertTrue(save.contains("entry.putInt(\"PatternSlot\", pendingInputSlots.get(i));"));
    }

    @Test
    public void admissionUsesAggregateCapacityAndKeepsDropPortsReusable() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String port = read("PatternP2PUnitPortPart.java");
        String allocation = section(manager, "private boolean canAcceptInputsAggregate",
                "private boolean dispatchPending");

        assertTrue(port.contains("public int estimateTransferCapacity(ItemStack stack, int fallback)"));
        assertTrue(port.contains("handler.insertItem(slot, probe, true)"));
        assertTrue(port.contains("if (type == UnitPortType.DROP) return Integer.MAX_VALUE;"));
        assertTrue(allocation.contains("type == cn.ae2bc.core.unit.UnitPortType.TRANSFER"));
        assertTrue(allocation.contains("port.estimateTransferCapacity(stack, simulated)"));
        assertTrue("DROP must retain its unlimited capacity across multiple pattern inputs",
                !allocation.contains("else if (capacity == Integer.MAX_VALUE)"));
        assertTrue(allocation.contains("if (simulated <= 0) continue;"));
        assertTrue(allocation.contains("if (accepted <= 0) continue;"));
    }

    @Test
    public void admissionRetainsPriorityFormAndWhitelistBlacklistFiltering() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String port = read("PatternP2PUnitPortPart.java");
        String candidates = section(manager, "private List<PatternP2PUnitPortPart> candidatePorts",
                "private boolean canAcceptInputsAggregate");
        String matching = section(port, "public boolean matchesInput",
                "/** Forwards Forge Energy");
        String filter = section(port, "private boolean allowsOutputFilter",
                "@Override public int getPriority()");

        assertTrue(candidates.contains("port.matchesInput(this, stack, form)"));
        assertTrue(candidates.contains("right.getTransferPriority(), left.getTransferPriority()"));
        assertTrue(matching.contains("UnitPortType.forOutputFormId(form.getId()) == type"));
        assertTrue(matching.contains("form.supports(stack)"));
        assertTrue(matching.contains("allowsOutputFilter(stack)"));
        assertTrue(filter.contains("ItemStack.isSame(marker, stack)"));
        assertTrue(filter.contains("ItemStack.tagMatches(marker, stack)"));
        assertTrue(filter.contains("return !hasMarkers || (inverted ? !marked : marked);"));
    }

    @Test
    public void sameTypeModeUsesItemAndNbtDuringAdmissionAndDispatch() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String allocation = section(manager, "private boolean canAcceptInputsAggregate",
                "private boolean dispatchPending");
        String findPort = section(manager, "private PatternP2PUnitPortPart findInputPort",
                "private static cn.ae2bc.pattern.MaterialOutputForm formForPortType");
        String dispatch = section(manager, "private boolean dispatchPending",
                "private boolean canUsePortForSlot");

        assertTrue(manager.contains("Map<PatternP2PUnitPortPart, ItemStack> dispatchPortTypes"));
        assertTrue(allocation.contains("Map<PatternP2PUnitPortPart, ItemStack> assignedTypes"));
        assertTrue(allocation.contains("!sameItem(assignedTypes.get(port), stack)"));
        assertTrue("A rejected simulation must not claim the SAME_TYPE identity",
                allocation.indexOf("if (simulated <= 0) continue;")
                        < allocation.indexOf("assignedTypes.put(port, identity);"));
        assertTrue(findPort.contains("!sameItem(assignedType, stack)"));
        assertTrue(dispatch.contains("rememberDispatchedType(port, stack);"));
        assertTrue(manager.contains("ItemStack.isSame(left, right) && ItemStack.tagMatches(left, right)"));
    }

    @Test
    public void placementDispatchFallsBackAndRetriesOnRelevantChanges() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String port = read("PatternP2PUnitPortPart.java");

        String dispatch = section(manager, "private boolean dispatchPending",
                "private void restorePersistedSlotPort");
        assertTrue(dispatch.contains("for (PatternP2PUnitPortPart port : candidates)"));
        assertTrue(dispatch.contains("port.insertTaskInput(this, attempt, false)"));
        assertTrue("A real placement rejection must try the next candidate",
                dispatch.contains("if (moved <= 0)") && dispatch.contains("continue;"));

        assertTrue(manager.contains("private int pendingRetryFailures;"));
        assertTrue(manager.contains("private long pendingNextRetryTick;"));
        assertTrue(manager.contains("DispatchBackoffPolicy.pendingDelay(pendingRetryFailures)"));
        assertTrue(manager.contains("public void alertPendingRetry()"));
        assertTrue(manager.contains("new TickingRequest(1, DispatchBackoffPolicy.MAX_PENDING_DELAY, false, true)"));
        String retryAlert = section(manager, "public void alertPendingRetry", "private void resetPendingRetryBackoff");
        assertTrue(retryAlert.contains("alertDevice(getGridNode())"));
        assertFalse(retryAlert.contains("wakeDevice(getGridNode())"));

        String singleSlot = section(port, "public void setSingleSlot", "public ItemStackHandler getOutputFilterMarkers");
        String priority = section(port, "public void setTransferPriority", "public UUID getBoundManagerId");
        String neighbor = section(port, "public void onNeighborChanged", "public void gridChanged");
        assertTrue(singleSlot.contains("alertManagerPendingRetry();"));
        assertTrue(priority.contains("alertManagerPendingRetry();"));
        assertTrue(neighbor.contains("alertManagerPendingRetry();"));
    }

    @Test
    public void placementCandidatesRequireMatchingUnitAndFrequency() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String port = read("PatternP2PUnitPortPart.java");

        String binding = section(port, "public boolean isBoundTo", "private FakePlayer placementPlayer");
        assertTrue(binding.contains("boundManagerId.equals(manager.getUnitId())"));
        assertTrue(binding.contains("boundFrequency == 0 || boundFrequency == manager.getFrequency()"));

        String matching = section(port, "public boolean matchesInput", "/** Forwards Forge Energy");
        assertTrue(matching.contains("isBoundTo(manager)"));
        assertTrue(manager.contains("port.matchesInput(this, stack, form)"));

        String placement = section(port, "private FakePlayer placementPlayer", "private IItemHandler adjacentItemHandler");
        assertTrue(placement.contains("getGridNode().getPlayerID()"));
        assertTrue(placement.contains("FakePlayerFactory.get(level, owner.getGameProfile())"));
        assertTrue(placement.contains("FakePlayerFactory.getMinecraft(level)"));
    }

    @Test
    public void sharedPlacementPortsRetainCommittedRemaindersAcrossPatternSlots() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String allocation = section(manager, "private boolean canAcceptInputsAggregate",
                "private boolean dispatchPending");

        assertTrue(allocation.contains("type == cn.ae2bc.core.unit.UnitPortType.PLACE"));
        assertTrue(allocation.contains("!port.getEffectiveSingleSlot()"));
        assertTrue("A shared placement port must not treat one simulated placement as total task capacity",
                allocation.contains("capacity = Integer.MAX_VALUE;"));
        assertTrue("Single-slot placement ports must retain their one-slot ownership path",
                allocation.contains("assignedSlotPorts.putIfAbsent(slot, port);"));
    }

    @Test
    public void inputSingleSlotControlRequiresAnActualPatternInputPart() throws Exception {
        String menu = readMenu("PatternP2PTunnelMenu.java");
        String screen = readClient("PatternP2PTunnelScreen.java");

        String inputConfiguration = section(menu, "public boolean isInputConfiguration",
                "public PatternP2PUnitSettings getSettings");
        assertTrue(inputConfiguration.contains("!output"));
        assertTrue(inputConfiguration.contains("part != null"));
        assertTrue(inputConfiguration.contains("!part.isOutput()"));
        assertTrue(screen.contains("showSingleSlotControl = menu.isInputConfiguration();"));
        assertTrue(screen.contains("page == Page.UNIT_COMMON && showSingleSlotControl"));
        assertTrue("Button, background and title must all use the verified endpoint guard",
                occurrences(screen, "Page.UNIT_COMMON && showSingleSlotControl") == 3);
    }

    @Test
    public void chineseSingleSlotTooltipUsesChinesePunctuation() throws Exception {
        String chinese = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/assets/ae2_batchcraft/lang/zh_cn.json")), StandardCharsets.UTF_8);
        assertTrue(chinese.contains("当前：%s\\n开启后，"));
    }

    private static String read(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/part", name));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String readLogic(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/logic", name));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String readMenu(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/menu", name));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String readClient(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/client", name));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int occurrences(String source, String text) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(text, from)) >= 0) {
            count++;
            from += text.length();
        }
        return count;
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue("Missing section start: " + start, from >= 0);
        assertTrue("Missing section end: " + end, to > from);
        return source.substring(from, to);
    }
}
