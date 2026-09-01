package cn.ae2bc.client.model;

import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Additional render data shared by unit managers and their bound ports. */
public final class PatternP2PUnitModelData {
    public static final ModelProperty<Long> PATTERN_P2P_UNIT_ID = new ModelProperty<>();

    private PatternP2PUnitModelData() {
    }
}
