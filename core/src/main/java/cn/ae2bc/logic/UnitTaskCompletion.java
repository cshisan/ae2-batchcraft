package cn.ae2bc.logic;

final class UnitTaskCompletion {
    private UnitTaskCompletion() {
    }

    static boolean isComplete(boolean taskActive, long remainingPrimary, boolean hasPendingInputs) {
        return taskActive && remainingPrimary <= 0 && !hasPendingInputs;
    }
}
