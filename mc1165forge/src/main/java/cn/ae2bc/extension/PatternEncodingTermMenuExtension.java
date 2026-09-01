package cn.ae2bc.extension;

import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputForm;
import net.minecraft.util.Direction;

public interface PatternEncodingTermMenuExtension {
    MaterialOutputConfigData ae2bc$getMaterialOutputConfig();
    void ae2bc$setInputDirection(int slot, Direction direction);
    void ae2bc$setMaterialOutputForm(int slot, MaterialOutputForm form);
    void ae2bc$setMaterialOutputConfig(MaterialOutputConfigData config);
}
