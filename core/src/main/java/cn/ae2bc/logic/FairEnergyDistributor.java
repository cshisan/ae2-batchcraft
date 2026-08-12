package cn.ae2bc.logic;

/**
 * Splits one energy offer fairly and carries rejected shares to later receivers.
 */
public final class FairEnergyDistributor {
    @FunctionalInterface
    public interface Receiver {
        int receive(int receiverIndex, int maxAmount);
    }

    private FairEnergyDistributor() {
    }

    public static int distribute(int offered, int receiverCount, int startIndex, Receiver receiver) {
        if (offered <= 0 || receiverCount <= 0) {
            return 0;
        }

        int accepted = distributePass(offered, receiverCount, startIndex, receiver);
        int remaining = offered - accepted;
        if (remaining > 0) {
            accepted += distributePass(remaining, receiverCount, startIndex + 1, receiver);
        }
        return accepted;
    }

    private static int distributePass(int offered, int receiverCount, int startIndex, Receiver receiver) {
        int share = offered / receiverCount;
        int extraShares = offered % receiverCount;
        int carry = 0;
        int acceptedTotal = 0;

        for (int step = 0; step < receiverCount; step++) {
            int allocation = share + (step < extraShares ? 1 : 0);
            int toOffer = allocation + carry;
            if (toOffer <= 0) {
                continue;
            }

            int index = Math.floorMod(startIndex + step, receiverCount);
            int accepted = receiver.receive(index, toOffer);
            accepted = Math.max(0, Math.min(accepted, toOffer));
            acceptedTotal += accepted;
            carry = toOffer - accepted;
        }

        return acceptedTotal;
    }
}
