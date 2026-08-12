package cn.ae2bc.pattern;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.BlockItem;

/** How a unit port should expose one processing-pattern input. */
public enum MaterialOutputForm {
    NORMAL(0, "normal"),
    DROP(1, "drop"),
    PLACE(2, "place");

    private final int id;
    private final String serializedName;

    MaterialOutputForm(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int getId() {
        return id;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public boolean supports(AEKey what) {
        return switch (this) {
            case NORMAL -> true;
            case DROP -> what instanceof AEItemKey;
            case PLACE -> what instanceof AEFluidKey
                    || what instanceof AEItemKey itemKey && itemKey.getItem() instanceof BlockItem;
        };
    }

    public static MaterialOutputForm fromId(int id) {
        return switch (id) {
            case 1 -> DROP;
            case 2 -> PLACE;
            default -> NORMAL;
        };
    }
}
