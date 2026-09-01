package cn.ae2bc.pattern;

import cn.ae2bc.core.pattern.MaterialOutputFormIds;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.BlockItem;

/** How a unit port should expose one processing-pattern input. */
public enum MaterialOutputForm {
    NORMAL(MaterialOutputFormIds.NORMAL, MaterialOutputFormIds.serializedName(MaterialOutputFormIds.NORMAL)),
    DROP(MaterialOutputFormIds.DROP, MaterialOutputFormIds.serializedName(MaterialOutputFormIds.DROP)),
    PLACE(MaterialOutputFormIds.PLACE, MaterialOutputFormIds.serializedName(MaterialOutputFormIds.PLACE));

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
        return switch (MaterialOutputFormIds.normalize(id)) {
            case MaterialOutputFormIds.DROP -> DROP;
            case MaterialOutputFormIds.PLACE -> PLACE;
            default -> NORMAL;
        };
    }
}
