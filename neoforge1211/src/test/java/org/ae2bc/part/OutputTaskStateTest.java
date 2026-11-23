package org.ae2bc.part;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.part.output.Pp2mpOutputData;
import org.ae2bc.part.output.Pp2mpOutputLogic;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class OutputTaskStateTest {
    @Test
    void receivingAllProductsDoesNotFinishWhileInputsRemainUnsent() {
        Pp2mpOutputData data = new Pp2mpOutputData(() -> {
        }, () -> {
        }, () -> {
        });
        Pp2mpOutputLogic logic = new Pp2mpOutputLogic(new TestPart(), data);
        AEKey key = AEItemKey.of(Items.STONE);
        UUID taskId = UUID.randomUUID();
        data.begin(Map.of(key, 6L), null, taskId, null);
        data.getPendingInputs().put(null, new LinkedHashMap<>(Map.of(key, 3L)));
        logic.onReturnedInserted(key, 64);
        Assertions.assertTrue(data.isTaskActive());
        Assertions.assertTrue(TaskTestSupport.remaining(data).isEmpty());
        Assertions.assertEquals(taskId, data.getDeliveryId());
        data.getPendingInputs().clear();
        logic.retryTaskCompletion();
        Assertions.assertFalse(data.isTaskActive());
        Assertions.assertEquals(taskId, data.getLastTaskId());
        Assertions.assertNull(data.getDeliveryId());
    }

    @Test
    void completedProductsWaitForGridAndFinishAfterReconnection() {
        Pp2mpOutputData data = new Pp2mpOutputData(() -> {
        }, () -> {
        }, () -> {
        });
        TestPart part = new TestPart();
        part.setConnected(false);
        Pp2mpOutputLogic logic = new Pp2mpOutputLogic(part, data);
        AEKey key = AEItemKey.of(Items.STONE);
        UUID taskId = UUID.randomUUID();
        data.begin(Map.of(key, 6L), null, taskId, null);

        logic.onReturnedInserted(key, 6);
        logic.retryTaskCompletion();
        Assertions.assertTrue(TaskTestSupport.remaining(data).isEmpty());
        Assertions.assertTrue(data.isTaskActive());
        Assertions.assertEquals(taskId, data.getDeliveryId());
        Assertions.assertNull(data.getLastTaskId());

        part.setConnected(true);
        logic.retryTaskCompletion();
        Assertions.assertFalse(data.isTaskActive());
        Assertions.assertNull(data.getDeliveryId());
        Assertions.assertEquals(taskId, data.getLastTaskId());
    }

    @Test
    void resetMovesOnlyUnsentInputsIntoRecoveryAndBlocksReadmission() {
        Pp2mpOutputData data = new Pp2mpOutputData(() -> {
        }, () -> {
        }, () -> {
        });
        Pp2mpOutputLogic logic = new Pp2mpOutputLogic(new TestPart(), data);
        AEKey key = AEItemKey.of(Items.STONE);
        data.begin(Map.of(key, 6L), null, UUID.randomUUID(), null);
        data.getPendingInputs().put(null, new LinkedHashMap<>(Map.of(key, 3L)));
        data.getPendingInputs().put(Direction.NORTH, new LinkedHashMap<>(Map.of(key, 4L)));
        logic.resetTaskState();
        Assertions.assertEquals(Map.of(key, 7L), data.getInputRecovery());
        Assertions.assertTrue(data.getPendingInputs().isEmpty());
        Assertions.assertTrue(TaskTestSupport.remaining(data).isEmpty());
        Assertions.assertTrue(data.isTaskActive());
        Assertions.assertTrue(data.isCancelRequested());
        Assertions.assertFalse(logic.canAcceptTask());
    }

    @Test
    void resetKeepsDeliveryReceiptUntilSourceAcknowledgesIt() {
        Pp2mpOutputData data = new Pp2mpOutputData(() -> {
        }, () -> {
        }, () -> {
        });
        Pp2mpOutputLogic logic = new Pp2mpOutputLogic(new TestPart(), data);
        UUID taskId = UUID.randomUUID();
        data.begin(Map.of(), null, taskId, null);
        logic.holdBatchReceipt(taskId);
        logic.resetTaskState();
        Assertions.assertTrue(logic.hasAcceptedTask(taskId));
        Assertions.assertTrue(data.getBatchReceipt().isPending());
        Assertions.assertFalse(logic.canAcceptTask());
        logic.acknowledgeBatch(taskId);
        Assertions.assertFalse(data.getBatchReceipt().isPending());
        Assertions.assertTrue(logic.hasAcceptedTask(taskId));
    }

    private static final class TestPart extends Pp2mpOutputPart {
        private static final IGrid EMPTY_GRID = (IGrid) Proxy.newProxyInstance(
                IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getMachines")) {
                        Assertions.assertEquals(FakeCraftingConverterBlockEntity.class, arguments[0]);
                        return Set.of();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        private static final IPartHost HOST = (IPartHost) Proxy.newProxyInstance(
                IPartHost.class.getClassLoader(), new Class<?>[]{IPartHost.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("markForSave") || method.getName().equals("markForUpdate")) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        private IManagedGridNode testNode;
        private boolean connected = true;

        private TestPart() {
            super(ItemRegistry.PP2MP_OUTPUT.get());
            IManagedGridNode originalNode = super.getMainNode();
            this.testNode = (IManagedGridNode) Proxy.newProxyInstance(
                    IManagedGridNode.class.getClassLoader(), new Class<?>[]{IManagedGridNode.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("getGrid")) {
                            return this.connected ? TestPart.EMPTY_GRID : null;
                        }
                        return method.invoke(originalNode, arguments);
                    });
        }

        private void setConnected(boolean connected) {
            this.connected = connected;
        }

        @Override
        public IManagedGridNode getMainNode() {
            // 父类构造期间继续使用真实节点，构造完成后仅替换网络查询。
            return this.testNode == null ? super.getMainNode() : this.testNode;
        }

        @Override
        public IPartHost getHost() {
            return TestPart.HOST;
        }

        @Override
        public void wakeProductExtractor() {
        }
    }
}
