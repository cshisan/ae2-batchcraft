package cn.ae2bc.link;

import cn.ae2bc.core.dispatch.RoundRobinPolicy;

/** Compatibility facade for the 1.21 platform; new platform code uses {@link RoundRobinPolicy}. */
public final class RoundRobin {
    private RoundRobin() {
    }

    public static int index(int cursor, int offset, int size) {
        return RoundRobinPolicy.index(cursor, offset, size);
    }

    public static int advance(int successfulIndex, int size) {
        return RoundRobinPolicy.advance(successfulIndex, size);
    }
}
