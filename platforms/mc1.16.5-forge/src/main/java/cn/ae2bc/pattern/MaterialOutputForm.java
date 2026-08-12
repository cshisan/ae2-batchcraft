package cn.ae2bc.pattern;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

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

    public int getId() { return id; }
    public String getSerializedName() { return serializedName; }

    public boolean supports(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (this != PLACE || stack.getItem() instanceof BlockItem);
    }

    public static MaterialOutputForm fromId(int id) {
        switch (id) {
            case 1: return DROP;
            case 2: return PLACE;
            default: return NORMAL;
        }
    }
}
