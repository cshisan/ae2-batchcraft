package org.ae2bc.common;

public interface Version {
    int version();

    static int nextInput(int version) {
        int current = version > 0 && version < Integer.MAX_VALUE ? version : 0;
        return current + 1;
    }

    static int nextLocal(int version) {
        return version < 0 && version > Integer.MIN_VALUE ? version - 1 : -1;
    }

    static int restoreInput(int version) {
        return version > 0 ? version : 1;
    }

    static int restoreAny(int version) {
        return version == 0 ? 1 : version;
    }

    static boolean equals(Version v1, Version v2) {
        if (v1 == null || v2 == null) {
            return false;
        }
        return v1.version() == v2.version();
    }
}
