package org.ae2bc.part.output;

import appeng.api.parts.PartModels;
import net.minecraft.resources.ResourceLocation;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.part.Pp2mpPartModels;

import java.util.List;

public interface Pp2mpOutputModel {
    Pp2mpPartModels MODELS = new Pp2mpPartModels(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "part/pp2mp_output"));

    static void register() {
        List<ResourceLocation> locations = MODELS.getModels().stream().flatMap(
                model -> model.getModels().stream()
        ).toList();
        PartModels.registerModels(locations);
    }
}
