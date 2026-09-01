package cn.ae2bc.core.pattern;

/** Stable IDs and names shared by platform-specific material-output form enums. */
public final class MaterialOutputFormIds {
    public static final int NORMAL = 0;
    public static final int DROP = 1;
    public static final int PLACE = 2;

    private MaterialOutputFormIds() {
    }

    public static int normalize(int id) {
        return id == DROP || id == PLACE ? id : NORMAL;
    }

    public static String serializedName(int id) {
        switch (normalize(id)) {
            case DROP:
                return "drop";
            case PLACE:
                return "place";
            default:
                return "normal";
        }
    }
}
