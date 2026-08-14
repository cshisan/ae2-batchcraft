package cn.ae2bc.logic;

import java.util.function.IntUnaryOperator;

/** Supplies receivers in fixed order and never bypasses an unsatisfied earlier receiver. */
public final class PrioritizedEnergyDistributor {
    @FunctionalInterface
    public interface Receiver {
        int receive(int receiverIndex, int maxAmount);
    }

    private PrioritizedEnergyDistributor() {
    }

    public static int distribute(int offered, int receiverCount, IntUnaryOperator demand, Receiver receiver) {
        if (offered <= 0 || receiverCount <= 0) {
            return 0;
        }
        int remaining = offered;
        for (int index = 0; index < receiverCount && remaining > 0; index++) {
            int requested = Math.min(remaining, Math.max(0, demand.applyAsInt(index)));
            if (requested <= 0) {
                continue;
            }
            int accepted = Math.max(0, Math.min(receiver.receive(index, requested), requested));
            remaining -= accepted;
            if (accepted < requested) {
                break;
            }
        }
        return offered - remaining;
    }
}
