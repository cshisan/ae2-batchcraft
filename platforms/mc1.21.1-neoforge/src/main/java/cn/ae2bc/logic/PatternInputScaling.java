package cn.ae2bc.logic;

import appeng.api.stacks.KeyCounter;

import java.math.BigInteger;

final class PatternInputScaling {
    private PatternInputScaling() {
    }

    static KeyCounter[] atomicInputs(KeyCounter[] inputs, long totalUnits) {
        return scale(inputs, 1, totalUnits);
    }

    static KeyCounter[] multiply(KeyCounter[] atomicInputs, long units) {
        if (units <= 0) {
            return null;
        }
        KeyCounter[] result = new KeyCounter[atomicInputs.length];
        try {
            for (int i = 0; i < atomicInputs.length; i++) {
                KeyCounter counter = new KeyCounter();
                for (var entry : atomicInputs[i]) {
                    counter.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), units));
                }
                result[i] = counter;
            }
            return result;
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private static KeyCounter[] scale(KeyCounter[] inputs, long numerator, long denominator) {
        if (inputs == null || denominator <= 0 || numerator <= 0 || numerator > denominator) {
            return null;
        }
        BigInteger divisor = BigInteger.valueOf(denominator);
        BigInteger multiplier = BigInteger.valueOf(numerator);
        KeyCounter[] result = new KeyCounter[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            KeyCounter counter = new KeyCounter();
            for (var entry : inputs[i]) {
                BigInteger[] divided = BigInteger.valueOf(entry.getLongValue())
                        .multiply(multiplier).divideAndRemainder(divisor);
                if (divided[1].signum() != 0 || divided[0].signum() <= 0
                        || divided[0].bitLength() > 63) {
                    return null;
                }
                counter.add(entry.getKey(), divided[0].longValueExact());
            }
            result[i] = counter;
        }
        return result;
    }
}
