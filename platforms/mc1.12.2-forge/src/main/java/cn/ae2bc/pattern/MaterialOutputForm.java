package cn.ae2bc.pattern;

import cn.ae2bc.core.pattern.MaterialOutputFormIds;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

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

    public int getId() { return id; }
    public String getSerializedName() { return serializedName; }

    public boolean supports(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (this != PLACE || stack.getItem() instanceof ItemBlock);
    }

    public static MaterialOutputForm fromId(int id) {
        switch (MaterialOutputFormIds.normalize(id)) {
            case MaterialOutputFormIds.DROP: return DROP;
            case MaterialOutputFormIds.PLACE: return PLACE;
            default: return NORMAL;
        }
    }
}
